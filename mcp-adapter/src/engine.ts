import * as cheerio from "cheerio";
import { Session } from "./http.js";
import { textOf } from "./sanitize.js";
import type { AdapterConfig, ReadTool, WriteTool } from "./config.js";

/** A tool-level failure the server surfaces to the model as an error result. */
export class ToolError extends Error {}

/** A read row: its sanitized `summary` plus any exact handle fields (id, or empId+week, …). */
export interface Row {
  summary: string;
  [field: string]: string;
}

/** Replace `${name}` in a template with args. Reads encode (path segments + query values);
 *  writes don't (ids are simple business keys already safe in a path). */
function fill(tpl: string, args: Record<string, string>, encode = false): string {
  return tpl.replace(/\$\{(\w+)\}/g, (_, k: string) => {
    const v = args[k] ?? "";
    return encode ? encodeURIComponent(v) : v;
  });
}

/**
 * The generic HTML front-door. It speaks the legacy app's own HTTP + HTML protocol
 * (as a logged-in user), so the app's native RBAC gates every action for free — the
 * write handle for a row only exists in the HTML when this session is allowed to act.
 */
export class FrontDoor {
  readonly session: Session;
  private persona: string | null = null;

  constructor(private cfg: AdapterConfig) {
    this.session = new Session(cfg.baseUrl);
  }

  /** Establish the caller's session. Demo bootstrap = persona login; in production the
   *  hub forwards the user's real session cookie and this step is pass-through. */
  async bootstrap(persona: string): Promise<void> {
    await this.session.get("/login"); // DeviceFilter mints meridian_device here
    const r = await this.session.post("/login", { userId: persona });
    if (r.status !== 302) throw new ToolError(`login as '${persona}' failed: HTTP ${r.status}`);
    this.persona = persona;
  }

  async read(tool: ReadTool, args: Record<string, string> = {}): Promise<Row[]> {
    const path = fill(tool.path, args, true);
    const { status, body } = await this.session.get(path);
    if (status !== 200) throw new ToolError(`${tool.name}: GET ${path} → HTTP ${status} (auth redirect?)`);

    const $ = cheerio.load(body);
    const anchors = $(tool.row.anchor).toArray();

    if (anchors.length === 0) {
      if (tool.emptyMarkers?.some((m) => body.includes(m))) return [];
      throw new ToolError(
        `${tool.name}: 0 rows matched '${tool.row.anchor}' and no empty-marker present — ` +
          `page shape changed or the session lost its identity.`,
      );
    }

    return anchors.map((a) => {
      const $a = $(a);
      const container = tool.row.container ? $a.closest(tool.row.container) : $a;
      const node = container.length ? container : $a;
      const row: Row = { summary: textOf(node) };
      for (const [key, f] of Object.entries(tool.fields.handle ?? {})) {
        const el = f.selector ? $a.find(f.selector).first() : $a;
        if (f.text) {
          row[key] = el.text().replace(/\s+/g, " ").trim();
        } else {
          const raw = el.attr(f.attr ?? "") ?? "";
          row[key] = f.extract ? (raw.match(new RegExp(f.extract))?.[1] ?? "") : raw.trim();
        }
      }
      return row;
    });
  }

  async write(tool: WriteTool, args: Record<string, string>): Promise<{ ok: boolean; id: string }> {
    const path = fill(tool.path, args);

    const form: Record<string, string> = { ...tool.form };
    for (const [k, spec] of Object.entries(tool.args)) {
      if (!spec.inPath && args[k] != null) form[k] = args[k];
    }

    // Generic CSRF harvest — no-op for Meridian.
    if (tool.csrf?.harvestFrom) {
      const { body } = await this.session.get(tool.csrf.harvestFrom);
      const token = cheerio.load(body)(tool.csrf.selector).attr(tool.csrf.attr ?? "value");
      if (token) form[tool.csrf.field] = token;
    }

    const r = await this.session.post(path, form);
    if (r.status !== 302 && r.status !== 200) {
      throw new ToolError(`${tool.name}: POST ${path} → HTTP ${r.status}`);
    }

    // Post-write proof: re-run the associated read and assert this row's handle is gone.
    if (tool.verify) {
      const rt = this.cfg.readTools.find((t) => t.name === tool.verify!.via);
      if (!rt) throw new ToolError(`${tool.name}: verify read-tool '${tool.verify.via}' not found`);
      // Pass the write's args through — the verify read may need them (e.g. a caseId in its path).
      const rows = await this.read(rt, args);
      const handle = Object.fromEntries(tool.handle.map((k) => [k, args[k]]));
      if (rows.some((row) => tool.handle.every((k) => row[k] === args[k]))) {
        throw new ToolError(`${tool.name}: write did not take — handle ${JSON.stringify(handle)} still present.`);
      }
    }

    return { ok: true, id: tool.handle.map((k) => args[k]).join("/") };
  }
}
