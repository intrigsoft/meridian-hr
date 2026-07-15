import * as cheerio from "cheerio";
import { Session } from "./http.js";
import { textOf } from "./sanitize.js";
import type { AdapterConfig, ReadTool, WriteTool } from "./config.js";

/** A tool-level failure the server surfaces to the model as an error result. */
export class ToolError extends Error {}

export interface Row {
  id: string | null;
  summary: string;
}

/** Replace `${name}` in a template with args (no encoding; ids are simple business keys). */
function fill(tpl: string, args: Record<string, string>): string {
  return tpl.replace(/\$\{(\w+)\}/g, (_, k: string) => args[k] ?? "");
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

  async read(tool: ReadTool): Promise<Row[]> {
    const { status, body } = await this.session.get(tool.path);
    if (status !== 200) throw new ToolError(`${tool.name}: GET ${tool.path} → HTTP ${status} (auth redirect?)`);

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
      let id: string | null = null;
      if (tool.fields.id) {
        const attr = $a.attr(tool.fields.id.attr) ?? "";
        const m = attr.match(new RegExp(tool.fields.id.extract));
        id = m ? m[1] : null;
      }
      return { id, summary: textOf(node) };
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

    // Post-write proof: the write must have changed the page (fail loud if it didn't).
    if (tool.verify) {
      const after = await this.session.get(tool.verify.path);
      if (after.body.includes(fill(tool.verify.absentText, args))) {
        throw new ToolError(`${tool.name}: write did not take — ${JSON.stringify(args)} still actionable.`);
      }
    }

    return { ok: true, id: args.id ?? "" };
  }
}
