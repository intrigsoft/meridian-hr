#!/usr/bin/env node
/**
 * HTTP entrypoint — `npm run mcp:http` (or `npm start`). This is what DioscHub connects to.
 * Prefer the platform-injected PORT (Railway/Heroku) so the public proxy can reach us.
 */
import { createMeridianHttpServer } from "./mcp-http.js";
import { config } from "./config.js";

const PORT = Number(process.env.PORT ?? process.env.MERIDIAN_MCP_PORT ?? 5175);

createMeridianHttpServer().listen(PORT, () => {
  // stderr — never pollute a stdout protocol stream.
  console.error(`meridian-hr MCP (HTTP) listening on http://localhost:${PORT}/mcp  (base=${config.baseUrl})`);
});
