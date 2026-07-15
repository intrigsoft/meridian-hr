/**
 * Declarative tool catalog. The engine (engine.ts) is generic; every tool below is
 * just data pointing it at one Meridian route. Adding a domain = adding entries here,
 * not writing new code. This is what makes the surface loop-able.
 *
 * Two channels per read row:
 *   - the HANDLE fields  EXACT   — lifted from attributes (the write handle; may be composite).
 *   - `summary`          FUZZY   — the row's sanitized visible text (what the model reasons over).
 */

/** Where to read one exact handle field from, relative to the row anchor. */
export interface FieldExtract {
  /** Descendant selector under the row anchor; omit to read the anchor element itself. */
  selector?: string;
  /** Attribute to read (e.g. a form's `action`, or a hidden input's `value`). Ignored when `text`. */
  attr?: string;
  /** Regex with one capture group to pull the id out of the attribute; omit for the whole value. */
  extract?: string;
  /** Read the element's visible text instead of an attribute (for labels that live in markup, not attrs). */
  text?: boolean;
}

export interface ReadTool {
  name: string;
  description: string;
  /** May contain `${arg}` placeholders (path segment or query value); filled from call args, URL-encoded. */
  path: string;
  /** Inputs the caller supplies (e.g. a search query, an employee id). Omit for fixed-path reads. */
  args?: Record<string, { required: boolean; description: string }>;
  row: {
    /** Selector for a stable per-row anchor (prefer semantic hooks: a form action, a link). */
    anchor: string;
    /** Optional selector to climb from the anchor to the full row container for the summary. */
    container?: string;
  };
  fields: {
    /** Named handle fields — together they identify the row for the write tools. */
    handle?: Record<string, FieldExtract>;
    summary: { text: true };
  };
  /** Text that means "legitimately empty / not permitted / page loaded but no rows" → return [].
   *  Absence of both rows and any marker means the page shape changed → fail loud. */
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
  /** Which args identify the row — used to confirm the write took (verify) and to ack it. */
  handle: string[];
  /** Generic CSRF harvest — inert for Meridian (no spring-security); real legacy apps set it. */
  csrf?: { harvestFrom: string; selector: string; attr?: string; field: string } | null;
  /** Post-write proof: re-run read tool `via` and assert this row's handle is gone. */
  verify?: { via: string };
}

export interface AdapterConfig {
  baseUrl: string;
  readTools: ReadTool[];
  writeTools: WriteTool[];
}

export const config: AdapterConfig = {
  baseUrl: process.env.MERIDIAN_BASE_URL ?? "https://meridian-hr-staging.up.railway.app",

  readTools: [
    // ---- leave ----
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
        handle: { id: { attr: "action", extract: "/leave/(.+)/approve" } },
        summary: { text: true },
      },
      emptyMarkers: ["Queue clear", "No approvals for your role"],
    },

    // ---- time ----
    {
      name: "list_time_approvals",
      description:
        "List submitted timesheets awaiting the signed-in approver's decision. Each item has " +
        "`empId` + `week` (pass both to approve_timesheet) and a human-readable `summary`. " +
        "Returns an empty list when nothing is pending or the caller is not an approver.",
      path: "/time",
      row: {
        anchor: "form[action*='/time/approve']",
        container: "div[style*='padding:10px 12px']",
      },
      fields: {
        handle: {
          empId: { selector: "input[name='empId']", attr: "value" },
          week: { selector: "input[name='week']", attr: "value" },
        },
        summary: { text: true },
      },
      emptyMarkers: ["Recent weeks"],
    },

    // ---- job changes ----
    {
      name: "list_job_changes",
      description:
        "List job-change requests (promotion / transfer / comp) awaiting the signed-in approver's " +
        "decision. Each item has an `id` (pass to approve_job_change / reject_job_change) and a " +
        "human-readable `summary` including the proposed diffs. Empty when nothing is pending or the " +
        "caller may not approve.",
      path: "/job-changes",
      row: {
        anchor: "form[action*='/job-changes/'][action*='/approve']",
        container: "div[style*='padding:17px 20px']",
      },
      fields: {
        handle: { id: { attr: "action", extract: "/job-changes/(.+)/approve" } },
        summary: { text: true },
      },
      // "Pending approval" (a stats-card label) = the page loaded for an approver → empty if no rows.
      // "managed by managers" = the restricted panel shown to non-approvers.
      emptyMarkers: ["Pending approval", "managed by managers"],
    },

    // ---- recruitment ----
    {
      name: "list_requisition_approvals",
      description:
        "List job requisitions awaiting the signed-in HR approver's decision (before the role opens). " +
        "Each item has an `id` (pass to approve_requisition) and a human-readable `summary`. Empty when " +
        "nothing is pending approval or the caller may not approve requisitions.",
      path: "/recruitment",
      row: {
        anchor: "form[action*='/recruitment/req/'][action*='/approve']",
        container: "div[style*='padding:18px 20px']",
      },
      fields: {
        handle: { id: { attr: "action", extract: "/recruitment/req/(.+)/approve" } },
        summary: { text: true },
      },
      emptyMarkers: ["Requisitions", "managed by hiring managers"],
    },

    // ---- directory (read-heavy; introduces read args) ----
    {
      name: "search_directory",
      description:
        "Search the employee directory by name, title, email, or department. Returns matching people, " +
        "each with an `id` (pass to get_employee) and a `summary` (name, title, dept, status, location, " +
        "tenure, manager). Available to everyone.",
      path: "/directory?q=${q}",
      args: { q: { required: true, description: "Search text — a name, title, email, or department." } },
      row: {
        anchor: "a[href^='/directory/']",
      },
      fields: {
        handle: { id: { attr: "href", extract: "/directory/(.+)" } },
        summary: { text: true },
      },
      emptyMarkers: ["No one matches your filters"],
    },
    {
      name: "get_employee",
      description:
        "Get one employee's 360° profile by id (from search_directory): role, department, org chain, " +
        "reports, tenure, and job history. Compensation shows only when the caller is allowed to see it.",
      path: "/directory/${id}",
      args: { id: { required: true, description: "Employee id, e.g. sarah.chen" } },
      row: {
        // The content fragment renders as this wrapper — scopes the profile out of the nav shell.
        anchor: "div[style*='max-width:1000px']",
      },
      fields: {
        summary: { text: true },
      },
      // The profile wrapper always renders (even for a bad id → "not found" text), so no empty marker.
    },

    // ---- onboarding (chain: cases → steps → complete) ----
    {
      name: "list_onboarding_cases",
      description:
        "List active onboarding cases (new hires being provisioned). Each has an `id` (pass to " +
        "list_onboarding_steps) and a `summary` (hire, role, start date, schema, progress). Manager/HR only.",
      path: "/onboarding",
      row: { anchor: "a[href^='/onboarding/case/']" },
      fields: {
        handle: { id: { attr: "href", extract: "/onboarding/case/(.+)" } },
        summary: { text: true },
      },
      emptyMarkers: ["No active onboardings", "No matches", "managed by managers"],
    },
    {
      name: "list_onboarding_steps",
      description:
        "List the actionable (in-progress) steps of one onboarding case. Each has `caseId`, `stepId`, and " +
        "`title` (pass all three to complete_onboarding_step) plus a `summary`. Empty when no step is ready.",
      path: "/onboarding/case/${caseId}",
      args: { caseId: { required: true, description: "Onboarding case id, e.g. onb-anna" } },
      row: {
        anchor: "form[action*='/step/'][action*='/complete']",
        container: "div[style*='flex:1; min-width:0']",
      },
      fields: {
        handle: {
          caseId: { attr: "action", extract: "/onboarding/case/([^/]+)/step/" },
          stepId: { attr: "action", extract: "/step/([^/]+)/complete" },
          title: { selector: "input[name='title']", attr: "value" },
        },
        summary: { text: true },
      },
      // A valid status page always shows one of these health labels → empty (no actionable step), not fail-loud.
      emptyMarkers: ["On track", "Complete", "blocked"],
    },

    // ---- offboarding (all cases render on one page; no detail route) ----
    {
      name: "list_offboarding",
      description:
        "List offboarding (exit) cases. Each has an `id` and a `summary` (employee, exit type, status, " +
        "last day, task progress like '3/7 tasks'). Manager sees their reports; HR sees all.",
      path: "/offboarding",
      row: {
        anchor: "form[action*='/offboarding/'][action*='/complete']",
        container: "div[style*='padding:18px 20px']",
      },
      fields: {
        handle: { id: { attr: "action", extract: "/offboarding/(.+)/complete" } },
        summary: { text: true },
      },
      emptyMarkers: ["No offboarding in progress", "available to managers"],
    },
    {
      name: "list_offboarding_tasks",
      description:
        "List the checklist tasks across offboarding cases. Each has `caseId`, `taskId`, and `label` — " +
        "pass caseId + taskId to toggle_offboarding_task to check/uncheck it.",
      path: "/offboarding",
      row: { anchor: "form[action*='/offboarding/'][action*='/task/']" },
      fields: {
        handle: {
          caseId: { attr: "action", extract: "/offboarding/([^/]+)/task/" },
          taskId: { attr: "action", extract: "/task/([^/]+)$" },
          label: { selector: "div[style*='min-width:0']", text: true },
        },
        summary: { text: true },
      },
      emptyMarkers: ["Active exits", "available to managers"],
    },
  ],

  writeTools: [
    // ---- leave ----
    {
      name: "approve_leave",
      description: "Approve one pending leave request by id (obtained from list_pending_approvals).",
      path: "/leave/${id}/approve",
      form: { back: "approvals" },
      args: { id: { required: true, inPath: true, description: "Leave request id, e.g. R-1048" } },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_pending_approvals" },
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
      handle: ["id"],
      csrf: null,
      verify: { via: "list_pending_approvals" },
    },

    // ---- time ----
    {
      name: "approve_timesheet",
      description:
        "Approve one submitted timesheet. Pass the `empId` and `week` from list_time_approvals.",
      path: "/time/approve",
      form: {},
      args: {
        empId: { required: true, description: "Employee id whose timesheet is approved, e.g. marcus.reid" },
        week: { required: true, description: "Week-start date of the timesheet, e.g. 2026-07-06" },
      },
      handle: ["empId", "week"],
      csrf: null,
      verify: { via: "list_time_approvals" },
    },

    // ---- job changes ----
    {
      name: "approve_job_change",
      description:
        "Approve one pending job-change request by id (from list_job_changes). Applies immediately or " +
        "schedules to its effective date, per the request.",
      path: "/job-changes/${id}/approve",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Job-change request id, e.g. jc-seed-sarah.chen-promotion" },
      },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_job_changes" },
    },
    {
      name: "reject_job_change",
      description: "Reject one pending job-change request by id (from list_job_changes).",
      path: "/job-changes/${id}/reject",
      form: {},
      args: { id: { required: true, inPath: true, description: "Job-change request id" } },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_job_changes" },
    },

    // ---- recruitment ----
    {
      name: "approve_requisition",
      description:
        "Approve one requisition pending approval by id (from list_requisition_approvals). Opens the role.",
      path: "/recruitment/req/${id}/approve",
      form: {},
      args: { id: { required: true, inPath: true, description: "Requisition id, e.g. REQ-2052" } },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_requisition_approvals" },
    },

    // ---- onboarding ----
    {
      name: "complete_onboarding_step",
      description:
        "Mark one in-progress onboarding step done (from list_onboarding_steps). Completing a step " +
        "auto-releases any dependent steps.",
      path: "/onboarding/case/${caseId}/step/${stepId}/complete",
      form: {},
      args: {
        caseId: { required: true, inPath: true, description: "Onboarding case id, e.g. onb-anna" },
        stepId: { required: true, inPath: true, description: "Step id, e.g. payroll" },
        title: { required: true, description: "The step's title (from list_onboarding_steps)." },
      },
      handle: ["caseId", "stepId"],
      csrf: null,
      verify: { via: "list_onboarding_steps" },
    },

    // ---- offboarding ----
    {
      name: "toggle_offboarding_task",
      description:
        "Check or uncheck one offboarding checklist task (from list_offboarding_tasks). Toggling flips " +
        "its done state and moves the case's progress.",
      path: "/offboarding/${caseId}/task/${taskId}",
      form: {},
      args: {
        caseId: { required: true, inPath: true, description: "Offboarding case id, e.g. off-seed-ken" },
        taskId: { required: true, inPath: true, description: "Checklist task id, e.g. access" },
      },
      handle: ["caseId", "taskId"],
      csrf: null,
      // No verify-by-absence: a toggled task stays in the list (flips in place). Proven by the case's
      // progress summary changing — asserted in verify.ts.
    },
  ],
};
