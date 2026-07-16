/**
 * Streamable HTTP transport for the Meridian front-door MCP server — the transport
 * DioscHub speaks to (MCP spec 2025-11-25). Stateless, mirroring the hub's own
 * connect-per-call client: a fresh Server + transport per request, identity resolved
 * from that request's `Authorization` header. No sessions, nothing to evict.
 *
 * Register in the hub as an MCP instance with serverUrl `http://<host>:<port>/mcp`,
 * transport `http`. The hub injects the visitor's bound BYOA auth on every call.
 */
import { createServer, type IncomingMessage, type Server as HttpServer } from "node:http";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createMeridianMcpServer } from "./mcp-server.js";

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

/** Build the node:http server exposing POST /mcp. Not listening yet — caller binds a port. */
export function createMeridianHttpServer(): HttpServer {
  return createServer(async (req, res) => {
    const path = (req.url ?? "").replace(/\?.*$/, "");
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
