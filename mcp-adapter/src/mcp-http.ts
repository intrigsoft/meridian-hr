/**
 * Streamable HTTP transport for the Meridian front-door MCP server — the transport
 * DioscHub speaks to (MCP spec 2025-11-25). Stateless, mirroring the hub's own
 * connect-per-call client: a fresh Server + transport per request.
 *
 * Auth: this adapter is an OAuth-style resource server AND the issuer of its own
 * access tokens (see auth-broker.ts). The Meridian app registers sessions via
 * POST /auth/bind; tool calls must carry the issued `Authorization: Bearer <jwt>`.
 * On a valid token the HTTP layer swaps the JWT for the cached Meridian artifact
 * BEFORE the transport parses the request — that swap IS the token exchange, so
 * the adapter internals (auth.ts) only ever see the Meridian artifact, and the
 * JWT is never forwarded upstream. Invalid/expired → 401 + WWW-Authenticate,
 * which the hub surfaces as a re-auth interrupt so the app can re-bind.
 *
 * Register in the hub as an MCP instance with serverUrl `http://<host>:<port>/mcp`,
 * transport `http`.
 */
import { createServer, type IncomingMessage, type ServerResponse, type Server as HttpServer } from "node:http";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createMeridianMcpServer } from "./mcp-server.js";
import { mintSessionToken, resolveSessionArtifact, sessionCount } from "./auth-broker.js";

const HUB_URL = (process.env.DIOSC_HUB_URL ?? "http://localhost:3333").replace(/\/$/, "");
const HUB_API_KEY = process.env.DIOSC_HUB_API_KEY ?? "";
// Shared secret the Meridian app must present to /auth/bind. Without it, anyone
// who can reach this adapter could mint bindings for arbitrary identities.
const BIND_SECRET = process.env.MCP_BIND_SECRET ?? "";
if (!BIND_SECRET) {
  console.error("[auth] MCP_BIND_SECRET not set — /auth/bind is unauthenticated (dev only)");
}

async function readBody(req: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const c of req) chunks.push(c as Buffer);
  if (chunks.length === 0) return undefined;
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    return undefined;
  }
}

function json(res: ServerResponse, status: number, body: unknown, headers: Record<string, string> = {}): void {
  res.writeHead(status, { "content-type": "application/json", ...headers }).end(JSON.stringify(body));
}

/** Bind: cache the app's artifact, mint the JWT, register the session with the hub. */
async function handleBind(req: IncomingMessage, res: ServerResponse): Promise<void> {
  if (BIND_SECRET && req.headers["x-bind-secret"] !== BIND_SECRET) {
    return json(res, 401, { error: "Invalid bind secret" });
  }
  const body = (await readBody(req)) as
    | {
        wsId?: string;
        identity?: { userId: string; username: string; role?: { id: string; name: string } } | null;
        artifact?: string;
      }
    | undefined;
  if (!body?.wsId) return json(res, 400, { error: "wsId is required" });
  if (!body.artifact) return json(res, 400, { error: "artifact is required" });

  const jwt = await mintSessionToken(body.identity?.userId ?? "anonymous", body.artifact);

  try {
    const hubRes = await fetch(`${HUB_URL}/api/auth/bind`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-api-key": HUB_API_KEY },
      body: JSON.stringify({
        wsId: body.wsId,
        identity: body.identity ?? null,
        authArtifacts: { headers: { Authorization: `Bearer ${jwt}` } },
      }),
    });
    if (!hubRes.ok) {
      const detail = await hubRes.text().catch(() => "");
      console.error(`[auth] hub bind failed (${hubRes.status}): ${detail.slice(0, 200)}`);
      return json(res, 502, { error: `hub bind failed: ${hubRes.status}` });
    }
  } catch (err) {
    console.error("[auth] could not reach the hub:", err);
    return json(res, 502, { error: "could not reach the hub" });
  }
  json(res, 200, { ok: true });
}

/** Build the node:http server exposing POST /mcp + POST /auth/bind. Not listening yet. */
export function createMeridianHttpServer(): HttpServer {
  return createServer(async (req, res) => {
    const path = (req.url ?? "").replace(/\?.*$/, "");

    if (path === "/auth/bind" && req.method === "POST") {
      await handleBind(req, res);
      return;
    }

    if (path === "/health") {
      return json(res, 200, { status: "healthy", service: "meridian-mcp", boundSessions: sessionCount() });
    }

    if (path !== "/mcp") {
      res.writeHead(404).end();
      return;
    }
    if (req.method !== "POST") {
      res
        .writeHead(405, { "content-type": "application/json", allow: "POST" })
        .end(JSON.stringify({ jsonrpc: "2.0", error: { code: -32000, message: "Method not allowed" }, id: null }));
      return;
    }

    const body = await readBody(req);
    const rpc = body as { method?: string; params?: { name?: string } } | undefined;
    if (rpc?.method) console.error(`[mcp] ${rpc.method}${rpc.params?.name ? ` ${rpc.params.name}` : ""}`);

    // Token exchange at the transport boundary: tool calls must present the JWT
    // this adapter issued; it is swapped for the cached Meridian artifact so the
    // adapter internals never see (or forward) the JWT.
    if (rpc?.method === "tools/call") {
      const artifact = await resolveSessionArtifact(
        Array.isArray(req.headers.authorization) ? req.headers.authorization[0] : req.headers.authorization,
      );
      if (!artifact) {
        return json(
          res,
          401,
          { jsonrpc: "2.0", error: { code: -32001, message: "Missing, invalid, or expired access token" }, id: null },
          { "WWW-Authenticate": 'Bearer resource="meridian-mcp", error="invalid_token"' },
        );
      }
      req.headers.authorization = `Bearer ${artifact}`;
    }

    // Stateless: throwaway server+transport for this one request.
    const server = createMeridianMcpServer();
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
    res.on("close", () => {
      void transport.close();
      void server.close();
    });
    await server.connect(transport);
    await transport.handleRequest(req, res, body);
  });
}
