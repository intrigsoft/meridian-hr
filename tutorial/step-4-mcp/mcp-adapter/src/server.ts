#!/usr/bin/env node
/**
 * stdio entrypoint — `npm run mcp`. For local drives / MCP Inspector.
 *
 * stdio carries no per-call HTTP headers, so identity comes from the environment:
 *   MERIDIAN_ARTIFACT   e.g. `persona:priya.nair` or `session:<meridian_device>`
 *   MERIDIAN_PERSONA    shorthand → treated as `persona:<value>` (default priya.nair)
 * DioscHub uses the HTTP transport instead (http-server.ts) where auth arrives per call.
 */
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { createMeridianMcpServer } from "./mcp-server.js";
import { config } from "./config.js";

const server = createMeridianMcpServer();
await server.connect(new StdioServerTransport());
console.error(`meridian-hr MCP server (stdio) ready (base=${config.baseUrl})`);
