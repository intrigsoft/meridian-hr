/**
 * BYOA artifact resolution — the credential-blind seam between DioscHub and Meridian.
 *
 * DioscHub is a conduit: the visitor's auth is bound to their session and injected on
 * EVERY tool call as the HTTP `Authorization: Bearer <artifact>` header (MCP spec
 * 2025-11-25 — see the hub's connect-per-call transport). This adapter reads that header
 * and turns the artifact into a Meridian session. Meridian has no machine API, so the
 * artifact isn't a bearer token an API validates — it names *which session to act as*.
 *
 * Two artifact shapes, one per real deployment mode:
 *   - `session:<meridian_device>` — PASS-THROUGH. The forwarded value is the visitor's
 *     own signed-in device cookie; the adapter acts as them with zero credentials of its
 *     own. This is the production path (RBAC + identity ride with the cookie).
 *   - `persona:<userId>`         — DEV EXCHANGE. Meridian's login is passwordless, so the
 *     adapter mints a fresh signed-in session via POST /login. Demo/local convenience;
 *     mirrors Cadence's dev-artifact fallback.
 *
 * The artifact is a credential. It is never logged, never returned in a tool result.
 */

export type Artifact =
  | { mode: "session"; value: string }
  | { mode: "persona"; value: string };

/** The per-call request context the MCP SDK attaches for the Streamable HTTP transport.
 *  `requestInfo.headers` is where the hub's per-call BYOA auth lands. */
export interface ToolExtra {
  requestInfo?: { headers?: Record<string, string | string[] | undefined> };
}

const BEARER = /^Bearer\s+/i;

/** Pull the raw artifact string for this call: the per-call `Authorization` header if the
 *  transport supplied one (HTTP), else the env fallback (stdio/local). Never logs it. */
export function rawArtifact(extra?: ToolExtra): string {
  const headers = extra?.requestInfo?.headers ?? {};
  const raw = headers.authorization ?? headers.Authorization; // web Headers lowercases
  const auth = Array.isArray(raw) ? raw[0] : raw;
  if (typeof auth === "string" && BEARER.test(auth)) return auth.replace(BEARER, "").trim();

  // Local fallbacks (no per-call header, e.g. stdio): an explicit artifact, or a persona.
  const env = process.env.MERIDIAN_ARTIFACT?.trim();
  if (env) return env;
  const persona = process.env.MERIDIAN_PERSONA?.trim() || "priya.nair";
  return `persona:${persona}`;
}

/** Parse the artifact grammar. A bare value (no `scheme:` prefix) is treated as a persona,
 *  so `MERIDIAN_PERSONA=sarah.chen` and `Bearer sarah.chen` both still work. */
export function parseArtifact(raw: string): Artifact {
  const i = raw.indexOf(":");
  const scheme = i > 0 ? raw.slice(0, i) : "";
  const value = i > 0 ? raw.slice(i + 1) : raw;
  if (scheme === "session") return { mode: "session", value: value.trim() };
  if (scheme === "persona") return { mode: "persona", value: value.trim() };
  return { mode: "persona", value: raw.trim() };
}

/** Resolve the artifact for this call in one step. */
export function resolveArtifact(extra?: ToolExtra): Artifact {
  return parseArtifact(rawArtifact(extra));
}
