/**
 * Declarative tool catalog. The engine (engine.ts) is generic; every tool below is
 * just data pointing it at one Meridian route. Adding a domain = adding entries here,
 * not writing new code. This is what makes the surface loop-able.
 *
 * Two channels per read row:
 *   - `id`      EXACT   — lifted from an attribute via regex (the write handle).
 *   - `summary` FUZZY   — the row's sanitized visible text (what the model reasons over).
 */

export interface IdField {
  /** Attribute on the ROW ANCHOR element to read the id from (e.g. a form's `action`). */
  attr: string;
  /** Regex with one capture group that extracts the id from that attribute value. */
  extract: string;
}

export interface ReadTool {
  name: string;
  description: string;
  path: string;
  row: {
    /** Selector for a stable per-row anchor (prefer semantic hooks: a form action, a link). */
    anchor: string;
    /** Optional selector to climb from the anchor to the full row container for the summary. */
    container?: string;
  };
  fields: {
    id?: IdField;
    summary: { text: true };
  };
  /** Text that means "legitimately empty / not permitted" → return []. Absence of both
   *  rows and any marker means the page shape changed → fail loud. */
  emptyMarkers?: string[];
}

export interface WriteArg {
  required: boolean;
  description: string;
  /** true → substituted into the path (`${name}`); false/absent → sent as a form field. */
  inPath?: boolean;
}

export interface WriteTool {
  name: string;
  description: string;
  path: string;
  /** Fixed form fields always sent (e.g. Meridian's `back=approvals`). */
  form: Record<string, string>;
  args: Record<string, WriteArg>;
  /** Generic CSRF harvest — inert for Meridian (no spring-security); real legacy apps set it. */
  csrf?: { harvestFrom: string; selector: string; attr?: string; field: string } | null;
  /** Post-write proof: re-fetch `path` and assert `absentText` is gone. */
  verify?: { path: string; absentText: string };
}

export interface AdapterConfig {
  baseUrl: string;
  readTools: ReadTool[];
  writeTools: WriteTool[];
}

export const config: AdapterConfig = {
  baseUrl: process.env.MERIDIAN_BASE_URL ?? "https://meridian-hr-staging.up.railway.app",

  readTools: [
    {
      name: "list_pending_approvals",
      description:
        "List leave requests awaiting the signed-in approver's decision. Each item has an `id` " +
        "(pass to approve_leave / reject_leave) and a human-readable `summary`. Returns an empty " +
        "list when the queue is clear or the caller is not an approver.",
      path: "/approvals",
      row: {
        anchor: "form[action*='/leave/'][action*='/approve']",
        container: "div[style*='padding:16px 18px']",
      },
      fields: {
        id: { attr: "action", extract: "/leave/(.+)/approve" },
        summary: { text: true },
      },
      emptyMarkers: ["Queue clear", "No approvals for your role"],
    },
  ],

  writeTools: [
    {
      name: "approve_leave",
      description: "Approve one pending leave request by id (obtained from list_pending_approvals).",
      path: "/leave/${id}/approve",
      form: { back: "approvals" },
      args: { id: { required: true, inPath: true, description: "Leave request id, e.g. R-1048" } },
      csrf: null,
      verify: { path: "/approvals", absentText: "/leave/${id}/approve" },
    },
    {
      name: "reject_leave",
      description: "Reject one pending leave request by id, with a required reason note shown to the requester.",
      path: "/leave/${id}/reject",
      form: { back: "approvals" },
      args: {
        id: { required: true, inPath: true, description: "Leave request id, e.g. R-1048" },
        note: { required: true, description: "Reason for rejection (shown to the requester)." },
      },
      csrf: null,
      verify: { path: "/approvals", absentText: "/leave/${id}/approve" },
    },
  ],
};
