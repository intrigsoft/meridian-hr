/**
 * The Meridian front-door MCP server, transport-agnostic. Both entrypoints use it:
 *   - server.ts       → stdio  (local / `npm run mcp`)
 *   - http-server.ts  → Streamable HTTP (what DioscHub connects to)
 *
 * Every tool call is STATELESS and per-identity: it resolves the caller's BYOA artifact
 * from this call's `Authorization` header, opens a fresh session as that identity, runs the
 * one tool, and returns. No shared session, no persona singleton — so two users hitting the
 * same server never cross wires, and the app's own RBAC gates every call (RBAC-for-free).
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { FrontDoor, ToolError } from "./engine.js";
import { resolveArtifact, type ToolExtra } from "./auth.js";
import { config } from "./config.js";

type ArgSpec = Record<string, { required: boolean; description: string }>;

function inputSchema(args?: ArgSpec) {
  const properties: Record<string, { type: string; description: string }> = {};
  const required: string[] = [];
  for (const [k, spec] of Object.entries(args ?? {})) {
    properties[k] = { type: "string", description: spec.description };
    if (spec.required) required.push(k);
  }
  return { type: "object" as const, properties, required };
}

function requireArgs(name: string, args: ArgSpec | undefined, provided: Record<string, unknown>): void {
  for (const [k, spec] of Object.entries(args ?? {})) {
    if (spec.required && (provided[k] == null || String(provided[k]).trim() === "")) {
      throw new ToolError(`${name}: missing required argument '${k}'`);
    }
  }
}

const text = (obj: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(obj, null, 2) }] });

/** Build a configured (but not yet connected) MCP server. Caller attaches a transport. */
export function createMeridianMcpServer(): Server {
  const server = new Server({ name: "meridian-hr", version: "0.2.0" }, { capabilities: { tools: {} } });

  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: [
      ...config.readTools.map((t) => ({ name: t.name, description: t.description, inputSchema: inputSchema(t.args) })),
      ...config.writeTools.map((t) => ({ name: t.name, description: t.description, inputSchema: inputSchema(t.args) })),
    ],
  }));

  server.setRequestHandler(CallToolRequestSchema, async (req, extra) => {
    const name = req.params.name;
    const args = (req.params.arguments ?? {}) as Record<string, string>;
    try {
      // Per-call identity: the hub injects the visitor's BYOA auth as this call's
      // Authorization header (stdio falls back to the MERIDIAN_* env).
      const door = new FrontDoor(config);
      await door.authenticate(resolveArtifact(extra as ToolExtra));

      const rt = config.readTools.find((t) => t.name === name);
      if (rt) {
        requireArgs(rt.name, rt.args, args);
        return text(await door.read(rt, args));
      }
      const wt = config.writeTools.find((t) => t.name === name);
      if (wt) {
        requireArgs(wt.name, wt.args, args);
        return text(await door.write(wt, args));
      }
      throw new ToolError(`unknown tool: ${name}`);
    } catch (e) {
      // The message carries the 401/403 marker (AuthError/ForbiddenError) the hub classifies on.
      return { content: [{ type: "text" as const, text: `ERROR: ${(e as Error).message}` }], isError: true };
    }
  });

  return server;
}
