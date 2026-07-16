/**
 * Hub-faithful end-to-end verification of the transport + BYOA seam.
 *
 * Unlike verify.ts (which drives the engine directly), this boots the REAL Streamable HTTP
 * server and talks to it with a real MCP Client — one throwaway transport per call, the
 * visitor's identity carried as the `Authorization` header — which is exactly how DioscHub's
 * connect-per-call client behaves (MCP spec 2025-11-25). It proves:
 *
 *   1. tools/list is served over Streamable HTTP.
 *   2. Identity is per-call: the SAME tool returns HR's queue under one bearer and an empty
 *      queue under an employee's — RBAC rides the header, nothing is shared server-side.
 *   3. `session:<meridian_device>` PASS-THROUGH works: a real signed-in device cookie,
 *      forwarded verbatim, acts as that user with no adapter-held credentials.
 *   4. An invalid artifact surfaces as an isError result carrying the `401 unauthorized`
 *      marker DioscHub classifies as AUTH (→ mid-turn re-auth), not a hard crash.
 */
import type { AddressInfo } from "node:net";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { createMeridianHttpServer } from "../src/mcp-http.js";
import { config } from "../src/config.js";

function assert(cond: boolean, msg: string): void {
  if (!cond) throw new Error(msg);
}

/** One connect-per-call round trip with a per-call bearer, mirroring the hub's client. */
async function callAs(url: string, artifact: string, name: string, args: Record<string, string> = {}) {
  const transport = new StreamableHTTPClientTransport(new URL(url), {
    requestInit: { headers: { Authorization: `Bearer ${artifact}` } },
  });
  const client = new Client({ name: "verify-hub", version: "1.0.0" });
  await client.connect(transport);
  try {
    const res = (await client.callTool({ name, arguments: args })) as {
      isError?: boolean;
      content: { type: string; text: string }[];
    };
    return { isError: !!res.isError, text: res.content?.[0]?.text ?? "" };
  } finally {
    await client.close();
  }
}

async function listTools(url: string): Promise<string[]> {
  const transport = new StreamableHTTPClientTransport(new URL(url), {
    requestInit: { headers: { Authorization: "Bearer persona:priya.nair" } },
  });
  const client = new Client({ name: "verify-hub", version: "1.0.0" });
  await client.connect(transport);
  try {
    const { tools } = await client.listTools();
    return tools.map((t) => t.name);
  } finally {
    await client.close();
  }
}

/** Mint a real, signed-in `meridian_device` cookie the way a browser would, so we can prove
 *  the production pass-through path (`session:<cookie>`). Test-only — the server itself never
 *  handles a raw credential like this; it only ever receives the opaque forwarded artifact. */
async function mintSignedInDevice(persona: string): Promise<string> {
  const base = config.baseUrl.replace(/\/$/, "");
  const g = await fetch(`${base}/login`, { redirect: "manual" });
  const setCookie = (g.headers as unknown as { getSetCookie?: () => string[] }).getSetCookie?.() ?? [];
  const device = setCookie.map((c) => c.split(";")[0]).find((p) => p.startsWith("meridian_device="))?.split("=")[1];
  assert(!!device, "could not mint a meridian_device cookie from /login");
  const p = await fetch(`${base}/login`, {
    method: "POST",
    redirect: "manual",
    headers: { "content-type": "application/x-www-form-urlencoded", cookie: `meridian_device=${device}` },
    body: new URLSearchParams({ userId: persona }).toString(),
  });
  assert(p.status === 302 && !(p.headers.get("location") ?? "").includes("/login"), `sign-in as ${persona} failed`);
  return device!;
}

async function main(): Promise<void> {
  const http = createMeridianHttpServer();
  await new Promise<void>((r) => http.listen(0, "127.0.0.1", r));
  const url = `http://127.0.0.1:${(http.address() as AddressInfo).port}/mcp`;
  console.log(`Booted MCP over Streamable HTTP at ${url}`);
  console.log(`Target app: ${config.baseUrl}\n`);

  try {
    // 1. tools/list over HTTP
    const names = await listTools(url);
    console.log(`tools/list → ${names.length} tools`);
    assert(names.length === 28, `expected 28 tools, got ${names.length}`);
    assert(names.includes("list_pending_approvals"), "catalog should include list_pending_approvals");

    // 2. Identity is per-call: RBAC follows the Authorization header
    const hr = await callAs(url, "persona:priya.nair", "list_pending_approvals");
    assert(!hr.isError, `HR read errored: ${hr.text}`);
    const hrRows = JSON.parse(hr.text) as unknown[];
    console.log(`[Bearer persona:priya.nair]  list_pending_approvals → ${hrRows.length} row(s)`);
    assert(hrRows.length > 0, "HR should see a pending queue over the hub path");

    const emp = await callAs(url, "persona:sarah.chen", "list_pending_approvals");
    assert(!emp.isError, `employee read errored: ${emp.text}`);
    const empRows = JSON.parse(emp.text) as unknown[];
    console.log(`[Bearer persona:sarah.chen]  list_pending_approvals → ${empRows.length} row(s) (expect 0)`);
    assert(empRows.length === 0, "employee queue must be empty — RBAC rode the per-call header");

    // 3. session:<meridian_device> pass-through — the production credential-blind path
    const device = await mintSignedInDevice("priya.nair");
    const pass = await callAs(url, `session:${device}`, "list_pending_approvals");
    assert(!pass.isError, `session pass-through errored: ${pass.text}`);
    const passRows = JSON.parse(pass.text) as unknown[];
    console.log(`[Bearer session:<device>]   list_pending_approvals → ${passRows.length} row(s)`);
    assert(passRows.length > 0, "forwarded signed-in device cookie should act as HR");

    // 4. Invalid artifact → isError carrying the 401 marker the hub re-auths on
    const bad = await callAs(url, "session:not-a-real-device", "list_pending_approvals");
    console.log(`[Bearer session:bogus]      → isError=${bad.isError}  "${bad.text.slice(0, 60)}…"`);
    assert(bad.isError, "an unusable session must be an error result");
    assert(bad.text.includes("401 unauthorized"), "auth failure must carry the 401 marker for hub AUTH classification");

    console.log("\n✅ hub-faithful transport + BYOA seam verified end-to-end");
  } finally {
    await new Promise<void>((r) => http.close(() => r()));
  }
}

main().catch((e) => {
  console.error(`\n❌ ${(e as Error).message}`);
  process.exit(1);
});
