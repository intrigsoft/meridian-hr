#!/usr/bin/env node
/**
 * Meridian HR front-door MCP server (stdio).
 *
 * Exposes the config catalog as MCP tools. Each call lazily establishes the session
 * (persona from MERIDIAN_PERSONA — the BYOA identity this instance acts as), then runs
 * the generic engine. The HTML/session mechanics stay hidden behind clean typed tools.
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { FrontDoor, ToolError } from "./engine.js";
import { config } from "./config.js";

type ArgSpec = Record<string, { required: boolean; description: string }>;

const persona = process.env.MERIDIAN_PERSONA ?? "priya.nair";
const door = new FrontDoor(config);

let booted = false;
async function ensureSession(): Promise<void> {
  if (!booted) {
    await door.bootstrap(persona);
    booted = true;
  }
}

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

const server = new Server({ name: "meridian-hr", version: "0.1.0" }, { capabilities: { tools: {} } });

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    ...config.readTools.map((t) => ({ name: t.name, description: t.description, inputSchema: inputSchema(t.args) })),
    ...config.writeTools.map((t) => ({ name: t.name, description: t.description, inputSchema: inputSchema(t.args) })),
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (req) => {
  const name = req.params.name;
  const args = (req.params.arguments ?? {}) as Record<string, string>;
  try {
    await ensureSession();
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
    return { content: [{ type: "text" as const, text: `ERROR: ${(e as Error).message}` }], isError: true };
  }
});

await server.connect(new StdioServerTransport());
console.error(`meridian-hr MCP server ready (persona=${persona}, base=${config.baseUrl})`);
