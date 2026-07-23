/**
 * Auth broker — the MCP adapter as OAuth-style token issuer (MCP spec 2025-11-25).
 *
 * The spec requires a resource server to accept only tokens issued for itself
 * (audience-bound) and forbids passing a client-presented token through to an
 * upstream API. This module is how the Meridian front-door adapter satisfies both:
 *
 *   1. At bind time the Meridian app POSTs the visitor's identity + its BYOA
 *      artifact (`session:<meridian_device>` — the visitor's own cookie handle)
 *      to `/auth/bind` here. The artifact is CACHED and never leaves this process.
 *   2. We mint a short-lived HS256 JWT — issuer AND audience are this adapter,
 *      `jti` keys the cache entry — and register it with the hub as the session's
 *      auth artifact. The hub stays credential-blind: it holds a token that
 *      references the credential, never the credential.
 *   3. On every tool call the hub presents `Authorization: Bearer <jwt>`. The
 *      HTTP layer validates it and swaps in the cached Meridian artifact BEFORE
 *      the transport parses the request — that swap IS the token exchange, so the
 *      adapter internals (auth.ts) only ever see the Meridian artifact, and the
 *      JWT is never forwarded upstream.
 *
 * The cache is in-memory (single-replica sample). Production variants: a shared
 * store (Redis) keyed by `jti`, or encrypting the artifact into the token.
 */
import crypto from "node:crypto";
import { SignJWT, jwtVerify } from "jose";

const ISSUER = "meridian-mcp";
const AUDIENCE = "meridian-mcp";
const TTL_MS = 8 * 60 * 60 * 1000; // 8h — a working session

const RAW_SECRET = process.env.MCP_JWT_SECRET ?? "";
if (!RAW_SECRET) {
  console.error("[auth-broker] MCP_JWT_SECRET not set — using an insecure dev fallback");
}
const SECRET = new TextEncoder().encode(RAW_SECRET || "dev-insecure-meridian-mcp-jwt-secret");

interface CacheEntry {
  artifact: string;
  expiresAt: number;
}

const sessions = new Map<string, CacheEntry>();

function sweep(): void {
  const now = Date.now();
  for (const [jti, entry] of sessions) {
    if (entry.expiresAt <= now) sessions.delete(jti);
  }
}
setInterval(sweep, 60_000).unref();

/** Cache the app's BYOA artifact and mint the JWT that references it. */
export async function mintSessionToken(subject: string, artifact: string): Promise<string> {
  const jti = crypto.randomUUID();
  const expiresAt = Date.now() + TTL_MS;
  sessions.set(jti, { artifact, expiresAt });

  return new SignJWT({})
    .setProtectedHeader({ alg: "HS256" })
    .setIssuer(ISSUER)
    .setAudience(AUDIENCE)
    .setSubject(subject)
    .setJti(jti)
    .setIssuedAt()
    .setExpirationTime(Math.floor(expiresAt / 1000))
    .sign(SECRET);
}

/**
 * Validate a bearer JWT and resolve the cached Meridian artifact it references.
 * Returns null for anything invalid: bad signature, wrong audience, expired, or
 * an unknown/evicted session (e.g. after an adapter restart).
 */
export async function resolveSessionArtifact(authorizationHeader: string | undefined): Promise<string | null> {
  if (!authorizationHeader?.startsWith("Bearer ")) return null;
  const token = authorizationHeader.slice(7).trim();
  try {
    const { payload } = await jwtVerify(token, SECRET, { issuer: ISSUER, audience: AUDIENCE });
    const entry = payload.jti ? sessions.get(payload.jti) : undefined;
    if (!entry || entry.expiresAt <= Date.now()) return null;
    return entry.artifact;
  } catch {
    return null;
  }
}

/** Introspection for /health — never exposes artifact values. */
export function sessionCount(): number {
  sweep();
  return sessions.size;
}
