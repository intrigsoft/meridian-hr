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
  /** The arg's value is a JSON object; the engine expands each entry to a form field
   *  `<prefix><key>=<value>` instead of sending the arg itself. This is how declarative
   *  tools express the app's dynamic per-item fields (review scores `s_<compId>`,
   *  scorecard ratings `rating_<attrId>`, leave allowances `allow_<typeId>`). */
  expandJson?: { prefix: string };
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
  /** For creates that 302 to the new entity's page: a regex (one capture group) run against
   *  the redirect Location; the capture is returned as `created` (the new record's id). */
  returns?: { fromLocation: string };
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
      // "Active exits" (a stats-card label) always renders for allowed viewers — covers the
      // legitimately-empty page once every case is completed.
      emptyMarkers: ["No offboarding in progress", "Active exits", "available to managers"],
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

    // ---- profile (HR-approval side lives on the employee's directory page) ----
    {
      name: "list_profile_change_approvals",
      description:
        "List an employee's sensitive profile-change requests awaiting HR approval (legal name, bank, tax). " +
        "Each has `empId`, `cid` (pass both to approve_profile_change) and a `summary` (field, old → new). " +
        "HR only; empty otherwise.",
      path: "/directory/${empId}",
      args: { empId: { required: true, description: "Employee id whose pending changes to list, e.g. sarah.chen" } },
      row: {
        anchor: "form[action*='/change/'][action*='/approve']",
        container: "div[style*='padding:12px 14px']",
      },
      fields: {
        handle: {
          empId: { attr: "action", extract: "/directory/([^/]+)/change/" },
          cid: { attr: "action", extract: "/change/([^/]+)/approve" },
        },
        summary: { text: true },
      },
      // "Job title" always renders on a directory profile → empty (no pending changes / not HR), not fail-loud.
      emptyMarkers: ["Job title"],
    },

    // ---- performance (read-only) ----
    {
      name: "list_cycles",
      description:
        "List performance review cycles (HR). Each has an `id` and a `summary` (name, type, date range, " +
        "participants, status, completion %). HR only.",
      path: "/performance?tab=cycles",
      row: { anchor: "div[style*='padding:18px 20px']" },
      fields: {
        // id comes from the card's own design/view link (…?cycle=<id>), scoped to the card.
        handle: { id: { selector: "a[href*='cycle=']", attr: "href", extract: "cycle=([^&]+)" } },
        summary: { text: true },
      },
      emptyMarkers: ["Review cycles", "Calibrate"],
    },
    {
      name: "list_reviews",
      description:
        "List performance reviews for the active cycle. Each has `cycle`, `emp` and a `summary` (name, title, " +
        "dept, reviewer, stage, self/manager/calibrated scores). HR sees all; a manager sees their team; an " +
        "employee sees only their own.",
      path: "/performance?tab=reviews",
      row: { anchor: "a[href*='/performance/review']" },
      fields: {
        handle: {
          cycle: { attr: "href", extract: "cycle=([^&]+)" },
          emp: { attr: "href", extract: "emp=([^&]+)" },
        },
        summary: { text: true },
      },
      emptyMarkers: ["No reviews", "No matches", "Calibrate"],
    },

    // ---- analytics (read-only, single dashboard region) ----
    {
      name: "get_analytics",
      description:
        "Get the workforce analytics dashboard: headcount / active / onboarding / on-leave / open-roles KPIs, " +
        "headcount by department / status / work-mode / level, and review-cycle completion. Manager/HR only; " +
        "an employee gets a restricted message with no data.",
      path: "/analytics",
      // The content fragment renders as this wrapper — one region, scoped out of the nav shell.
      row: { anchor: "div[style*='max-width:1040px']" },
      fields: { summary: { text: true } },
    },

    // ---- admin (read-only detail reads; HR only, else "HR access required") ----
    {
      name: "get_admin_settings",
      description:
        "Get the workspace policy settings: working days, leave types, blackout periods, holidays, review " +
        "competencies, and job-change types. HR only.",
      path: "/settings",
      row: { anchor: "div[style*='max-width:900px']" },
      fields: { summary: { text: true } },
    },
    {
      name: "get_org_structure",
      description: "Get the org structure: departments (with lead + headcount), levels, and comp bands. HR only.",
      path: "/org",
      row: { anchor: "div[style*='max-width:900px']" },
      fields: { summary: { text: true } },
    },
    {
      name: "get_roles_and_access",
      description:
        "Get roles & access: who holds each access role (Employee / Manager / HR) plus the role×permission " +
        "matrix. HR only.",
      path: "/roles",
      row: { anchor: "div[style*='max-width:900px']" },
      fields: { summary: { text: true } },
    },
    {
      name: "get_audit_log",
      description:
        "Get the unified audit log — recent events synthesized from every domain (hires, leave decisions, " +
        "profile/job changes, exits) with actor, action, and timestamp. HR only.",
      path: "/audit",
      row: { anchor: "div[style*='max-width:940px']" },
      fields: { summary: { text: true } },
    },

    // ---- leave (self-service side) ----
    {
      name: "list_my_leave",
      description:
        "List the signed-in user's OWN leave requests (all statuses). Each has an `id` (pass to " +
        "withdraw_leave_request while it is still open) and a `summary` (type, dates, days, submitted, " +
        "approver, status). Available to everyone.",
      path: "/leave",
      row: { anchor: "a.lv-row" },
      fields: {
        handle: { id: { attr: "href", extract: "req=([^&]+)" } },
        summary: { text: true },
      },
      emptyMarkers: ["No requests in this view"],
    },
    {
      name: "list_leave_balances",
      description:
        "Get the signed-in user's leave balance tiles — one row per bucket (Annual, Sick, Personal, " +
        "Carryover) with remaining days and usage. Available to everyone.",
      path: "/leave",
      row: { anchor: "div[style*='border-radius:10px; padding:16px']" },
      fields: { summary: { text: true } },
    },

    // ---- time (self-service side) ----
    {
      name: "get_my_timesheet",
      description:
        "Get the signed-in user's timesheet grid for a week: the week label, status (Open / Submitted / " +
        "Approved), per-day tags (holiday / leave / logged hours once locked), and the week total vs " +
        "target. Hours still being edited live in form inputs and are not part of the text — the total " +
        "is. Omit `week` for the current week.",
      path: "/time?week=${week}",
      args: { week: { required: false, description: "Week-start date (a Monday), e.g. 2026-07-13. Omit for this week." } },
      // The timesheet grid IS the save form — anchoring on it keeps the status pill,
      // week label, and total in the summary (the page wrapper would scope them away
      // with the rest of the form).
      row: { anchor: "form[action='/time/save']" },
      fields: { summary: { text: true } },
    },

    // ---- profile (self-service side) ----
    {
      name: "get_my_profile",
      description:
        "Get the signed-in user's own profile: about-you fields, employment summary, bank & tax (masked), " +
        "emergency contacts, dependents, skills, and certifications. Fields awaiting HR approval are " +
        "marked pending. Available to everyone.",
      path: "/profile",
      row: { anchor: "div[style*='max-width:860px']" },
      fields: { summary: { text: true } },
    },

    // ---- onboarding templates (HR schema editor) ----
    {
      name: "list_onboarding_templates",
      description:
        "List the onboarding templates (provisioning schemas). Each has a `tplId` (pass to " +
        "get_onboarding_template / update_onboarding_template / delete_onboarding_template) and a " +
        "`summary` (name, role family, step count). HR only; empty otherwise.",
      path: "/onboarding?tab=templates",
      row: { anchor: "a[href^='/onboarding/templates/']" },
      fields: {
        handle: { tplId: { attr: "href", extract: "/onboarding/templates/(.+)" } },
        summary: { text: true },
      },
      emptyMarkers: ["Open onboardings", "managed by managers"],
    },
    {
      name: "get_onboarding_template",
      description:
        "Get one onboarding template's steps. Each row has `tplId`, `stepId`, and `title` (pass tplId + " +
        "stepId to edit_template_step) plus a `summary`. Step settings (system, owner, due offset, " +
        "depends-on, auto/doc toggles) live in form controls — edit them via edit_template_step. HR only.",
      path: "/onboarding/templates/${tplId}",
      args: { tplId: { required: true, description: "Template id from list_onboarding_templates, e.g. tpl-eng" } },
      row: { anchor: "form[action*='/onboarding/templates/'][action*='/step/']" },
      fields: {
        handle: {
          tplId: { attr: "action", extract: "/onboarding/templates/([^/]+)/step/" },
          stepId: { attr: "action", extract: "/step/([^/]+)$" },
          title: { selector: "input[name='title']", attr: "value" },
        },
        summary: { text: true },
      },
      // The editor always renders its "Add step" control, even for a template with zero steps.
      emptyMarkers: ["Add step"],
    },

    // ---- performance (review detail) ----
    {
      name: "get_review",
      description:
        "Get one employee's review page for a cycle (`cycle` + `emp` from list_reviews): stage, self / " +
        "manager / calibrated averages, submitted narratives, and per-competency scores once submitted. " +
        "Scores still being edited live in form controls and are not part of the text. Visible to HR, the " +
        "reviewer, and the employee themselves.",
      path: "/performance/review?cycle=${cycle}&emp=${emp}",
      args: {
        cycle: { required: true, description: "Cycle id, e.g. cyc-h1-2026" },
        emp: { required: true, description: "Employee id, e.g. sarah.chen" },
      },
      row: { anchor: "div[style*='max-width:860px']" },
      fields: { summary: { text: true } },
    },

    // ---- recruitment (full ATS reads) ----
    {
      name: "list_requisitions",
      description:
        "List ALL job requisitions regardless of status (draft, pending approval, open, filled, closed). " +
        "Each has an `id` (pass to get_requisition / get_pipeline and the requisition writes) and a " +
        "`summary` (title, status, dept, level, location, headcount, hiring manager, funnel counts). " +
        "Manager/HR only. For the pending-approval queue only, use list_requisition_approvals.",
      path: "/recruitment",
      row: {
        anchor: "a[href^='/recruitment/req/']:not([href$='/pipeline'])",
        container: "div[style*='padding:18px 20px']",
      },
      fields: {
        handle: { id: { attr: "href", extract: "/recruitment/req/([^/]+)$" } },
        summary: { text: true },
      },
      emptyMarkers: ["No requisitions yet", "managed by hiring managers"],
    },
    {
      name: "get_requisition",
      description:
        "Get one requisition's detail page by id (from list_requisitions): status, role details, scorecard " +
        "attributes, interview panel per round, and pipeline summary. While the requisition is a draft the " +
        "page is the designer — edit it via update_requisition_details / toggle_panel_member. Manager/HR only.",
      path: "/recruitment/req/${id}",
      args: { id: { required: true, description: "Requisition id, e.g. REQ-2044" } },
      row: { anchor: "div[style*='max-width:840px']" },
      fields: { summary: { text: true } },
    },
    {
      name: "get_pipeline",
      description:
        "List the candidates in one requisition's pipeline (from list_requisitions). Each has an `id` " +
        "(pass to get_candidate and the candidate writes) and a `summary` (name, current role, source, " +
        "fit). Stage columns are visible on get_candidate per candidate. Manager/HR only.",
      path: "/recruitment/req/${id}/pipeline",
      args: { id: { required: true, description: "Requisition id, e.g. REQ-2044" } },
      row: { anchor: "a[href^='/recruitment/candidate/']" },
      fields: {
        handle: { id: { attr: "href", extract: "/recruitment/candidate/(.+)$" } },
        summary: { text: true },
      },
      // Stage column headers always render, even with zero candidates.
      emptyMarkers: ["Applied"],
    },
    {
      name: "get_candidate",
      description:
        "Get one candidate's detail page by id (from get_pipeline): current stage, interview debrief " +
        "(scorecard averages, recommendations, comments), notes timeline, and offer state. Manager/HR only.",
      path: "/recruitment/candidate/${id}",
      args: { id: { required: true, description: "Candidate id, e.g. REQ-2044-jonas.meyer" } },
      row: { anchor: "div[style*='max-width:900px']" },
      fields: { summary: { text: true } },
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

    // ---- profile ----
    {
      name: "submit_legal_name_change",
      description:
        "Submit a change to your OWN legal name. Legal name is a sensitive field, so this is routed to HR " +
        "for approval (via list_profile_change_approvals / approve_profile_change) rather than applied now.",
      path: "/profile/info",
      form: {},
      args: { legalName: { required: true, description: "Your new legal name." } },
      handle: [],
      csrf: null,
    },
    {
      name: "approve_profile_change",
      description:
        "Approve one pending profile-change request (from list_profile_change_approvals), applying it to the " +
        "employee's record. HR only.",
      path: "/directory/${empId}/change/${cid}/approve",
      form: {},
      args: {
        empId: { required: true, inPath: true, description: "Employee id, e.g. sarah.chen" },
        cid: { required: true, inPath: true, description: "Change request id from list_profile_change_approvals" },
      },
      handle: ["empId", "cid"],
      csrf: null,
      verify: { via: "list_profile_change_approvals" },
    },
    {
      name: "reject_profile_change",
      description:
        "Reject one pending profile-change request (from list_profile_change_approvals), discarding it " +
        "without touching the employee's record. HR only.",
      path: "/directory/${empId}/change/${cid}/reject",
      form: {},
      args: {
        empId: { required: true, inPath: true, description: "Employee id, e.g. sarah.chen" },
        cid: { required: true, inPath: true, description: "Change request id from list_profile_change_approvals" },
      },
      handle: ["empId", "cid"],
      csrf: null,
      verify: { via: "list_profile_change_approvals" },
    },

    // ---- leave (self-service + approver extras) ----
    {
      name: "submit_leave_request",
      description:
        "Submit a leave request for YOURSELF. The policy engine evaluates it (balance, notice, blackout, " +
        "coverage, sick certificate); a clean request is routed to your manager (or escalated to HR over " +
        "the ceiling) and its new id is returned as `created`. If `created` is absent the policy checks " +
        "failed and nothing was submitted.",
      path: "/leave/new",
      form: {},
      args: {
        type: { required: true, description: "Leave type id: annual, sick, personal, parental, unpaid, or bereavement." },
        startDate: { required: true, description: "First day, e.g. 2026-09-29" },
        endDate: { required: true, description: "Last day, e.g. 2026-10-03" },
        halfDay: { required: false, description: "true for a half-day request (single-day only)." },
        hasAttachment: { required: false, description: "true to attach a certificate (sick leave over the certificate threshold)." },
        cover: { required: false, description: "Who covers while away, e.g. M. Reid" },
        reason: { required: false, description: "Free-text reason shown to the approver." },
      },
      handle: [],
      csrf: null,
      returns: { fromLocation: "req=([^&]+)" },
    },
    {
      name: "withdraw_leave_request",
      description:
        "Withdraw one of YOUR OWN open leave requests by id (from list_my_leave). Only the requester can " +
        "withdraw, and only while the request is still pending / escalated / awaiting info. The request " +
        "stays in your history as cancelled.",
      path: "/leave/${id}/withdraw",
      form: { back: "leave" },
      args: { id: { required: true, inPath: true, description: "Leave request id, e.g. R-1042" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "request_leave_info",
      description:
        "Ask the requester for more information on one pending leave request (from list_pending_approvals) " +
        "instead of deciding it. Approvers only. The note is shown to the requester and the request leaves " +
        "the actionable queue until they respond.",
      path: "/leave/${id}/info",
      form: { back: "approvals" },
      args: {
        id: { required: true, inPath: true, description: "Leave request id, e.g. R-1048" },
        note: { required: true, description: "What you need to know before deciding (shown to the requester)." },
      },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_pending_approvals" },
    },
    {
      name: "undo_leave_decision",
      description:
        "Revert a leave decision (approve / reject / info) by request id, returning it to the pending " +
        "queue. Approvers only — use right after a mistaken decision.",
      path: "/leave/${id}/undo",
      form: { back: "approvals" },
      args: { id: { required: true, inPath: true, description: "Leave request id whose decision to revert" } },
      handle: ["id"],
      csrf: null,
    },

    // ---- time (self-service) ----
    {
      name: "save_timesheet",
      description:
        "Save hours on YOUR OWN timesheet for a week without submitting it. Pass the week-start date and " +
        "hours per day; omitted days keep 0. Not possible once the week is approved.",
      path: "/time/save",
      form: {},
      args: {
        week: { required: true, description: "Week-start date (a Monday), e.g. 2026-07-13" },
        mon: { required: false, description: "Hours worked Monday, e.g. 8" },
        tue: { required: false, description: "Hours worked Tuesday" },
        wed: { required: false, description: "Hours worked Wednesday" },
        thu: { required: false, description: "Hours worked Thursday" },
        fri: { required: false, description: "Hours worked Friday" },
        sat: { required: false, description: "Hours worked Saturday" },
        sun: { required: false, description: "Hours worked Sunday" },
      },
      handle: ["week"],
      csrf: null,
    },
    {
      name: "submit_timesheet",
      description:
        "Submit YOUR OWN timesheet for a week for approval (also saves the hours passed). It lands in " +
        "your manager's / HR's pending strip (list_time_approvals on their side). Re-submitting a " +
        "submitted week replaces it.",
      path: "/time/submit",
      form: {},
      args: {
        week: { required: true, description: "Week-start date (a Monday), e.g. 2026-07-13" },
        mon: { required: false, description: "Hours worked Monday, e.g. 8" },
        tue: { required: false, description: "Hours worked Tuesday" },
        wed: { required: false, description: "Hours worked Wednesday" },
        thu: { required: false, description: "Hours worked Thursday" },
        fri: { required: false, description: "Hours worked Friday" },
        sat: { required: false, description: "Hours worked Saturday" },
        sun: { required: false, description: "Hours worked Sunday" },
      },
      handle: ["week"],
      csrf: null,
    },

    // ---- profile (self-service) ----
    {
      name: "submit_bank_change",
      description:
        "Submit changes to YOUR OWN bank & tax details. Every changed field is sensitive and routes to HR " +
        "for approval (list_profile_change_approvals / approve_profile_change) — nothing applies " +
        "immediately. Pass only the fields you want to change.",
      path: "/profile/bank",
      form: {},
      args: {
        bank_bankName: { required: false, description: "Bank name" },
        bank_accountName: { required: false, description: "Account holder name" },
        bank_accountLast4: { required: false, description: "Account number (last 4 digits)" },
        bank_routingLast4: { required: false, description: "Routing number (last 4 digits)" },
        taxIds_ssnLast4: { required: false, description: "Tax ID / SSN (last 4 digits)" },
        taxIds_nationalId: { required: false, description: "National ID" },
      },
      handle: [],
      csrf: null,
    },

    // ---- directory (HR people management) ----
    {
      name: "add_employee",
      description:
        "Add a new employee to the directory. HR only. First and last name are required; the rest default " +
        "sensibly. Returns the new employee's `id` as `created` (their directory handle for get_employee, " +
        "edit_employee, set_employee_status).",
      path: "/directory/new",
      form: {},
      args: {
        first: { required: true, description: "First name" },
        last: { required: true, description: "Last name" },
        title: { required: false, description: "Job title, e.g. Product Designer" },
        dept: { required: false, description: "Department name (must exist — see get_org_structure)" },
        level: { required: false, description: "Level name, e.g. Senior" },
        managerId: { required: false, description: "Manager's employee id, e.g. david.okonkwo" },
        location: { required: false, description: "Location, e.g. Remote · US" },
        workMode: { required: false, description: "Remote, Hybrid, or On-site" },
        startDate: { required: false, description: "Start date, e.g. 2026-08-01" },
        salary: { required: false, description: "Annual salary (digits only), e.g. 145000" },
      },
      handle: [],
      csrf: null,
      returns: { fromLocation: "/directory/([^/?]+)$" },
    },
    {
      name: "edit_employee",
      description:
        "Edit one employee's employment record (from search_directory / get_employee): title, department, " +
        "level, employment type, work mode, location, and optionally salary. All six non-salary fields are " +
        "submitted together — pass current values for anything you don't want to change. HR only.",
      path: "/directory/${id}/edit",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Employee id, e.g. sarah.chen" },
        title: { required: true, description: "Job title" },
        dept: { required: true, description: "Department name" },
        level: { required: true, description: "Level name" },
        employmentType: { required: true, description: "Full-time, Part-time, or Contract" },
        workMode: { required: true, description: "Remote, Hybrid, or On-site" },
        location: { required: true, description: "Location" },
        salary: { required: false, description: "Annual salary (digits only); omit to keep current" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "set_employee_status",
      description:
        "Set one employee's status: active, onboarding, leave (on leave), or inactive. HR only. The change " +
        "is recorded in the employee's history and the audit log.",
      path: "/directory/${id}/status",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Employee id, e.g. sarah.chen" },
        status: { required: true, description: "One of: active, onboarding, leave, inactive" },
      },
      handle: ["id"],
      csrf: null,
    },

    // ---- job changes (raise + withdraw) ----
    {
      name: "request_job_change",
      description:
        "Raise a job-change request (promotion / transfer / comp / reclass) for an employee, effective on " +
        "a date; HR approves it (approve_job_change). Managers raise for their reports; HR for anyone. " +
        "Pass only the f_* fields the change type touches — promotion: f_title/f_level/f_salary/f_band; " +
        "transfer: f_dept/f_managerId/f_title/f_workMode/f_location; comp: f_salary/f_band; reclass: " +
        "f_employmentType/f_title/f_level. Fields equal to the current record are ignored; at least one " +
        "must differ.",
      path: "/job-changes/new",
      form: {},
      args: {
        emp: { required: true, description: "Employee id the change is for, e.g. sarah.chen" },
        type: { required: true, description: "Change type: promotion, transfer, comp, or reclass" },
        eff: { required: true, description: "Effective date, e.g. 2026-08-01 (future = scheduled on approval)" },
        reason: { required: false, description: "Business justification shown to the approver." },
        f_title: { required: false, description: "New job title" },
        f_level: { required: false, description: "New level name" },
        f_salary: { required: false, description: "New annual salary (digits only)" },
        f_band: { required: false, description: "New comp band id, e.g. B3" },
        f_dept: { required: false, description: "New department name" },
        f_managerId: { required: false, description: "New manager's employee id" },
        f_workMode: { required: false, description: "New work mode: Remote, Hybrid, or On-site" },
        f_location: { required: false, description: "New location" },
        f_employmentType: { required: false, description: "New employment type: Full-time, Part-time, or Contract" },
      },
      handle: [],
      csrf: null,
    },
    {
      name: "cancel_job_change",
      description:
        "Withdraw one pending job-change request by id (from list_job_changes) before it is decided. The " +
        "requester or HR can withdraw.",
      path: "/job-changes/${id}/cancel",
      form: {},
      args: { id: { required: true, inPath: true, description: "Job-change request id" } },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_job_changes" },
    },

    // ---- onboarding (start / steps / convert) ----
    {
      name: "start_onboarding",
      description:
        "Start onboarding a new hire from the template for their role family. HR only. Returns the new " +
        "case id as `created` (pass to list_onboarding_steps and the step actions).",
      path: "/onboarding/new",
      form: {},
      args: {
        name: { required: true, description: "The hire's full name, e.g. Anna Kovacs" },
        role: { required: true, description: "Role family: design, eng, sales, or general — picks the template." },
        title: { required: false, description: "Job title, e.g. Product Designer" },
        start: { required: false, description: "Start date, e.g. 2026-07-20" },
        manager: { required: false, description: "Manager's name" },
        email: { required: false, description: "Work email; derived from the name when omitted." },
      },
      handle: [],
      csrf: null,
      returns: { fromLocation: "/onboarding/case/(.+)$" },
    },
    {
      name: "upload_onboarding_doc",
      description:
        "Mark a blocked onboarding step's required document as verified (from list_onboarding_steps' " +
        "blocked entries — the doc name shows in the case view), releasing the held step. Manager/HR only.",
      path: "/onboarding/case/${caseId}/step/${stepId}/upload",
      form: {},
      args: {
        caseId: { required: true, inPath: true, description: "Onboarding case id, e.g. onb-anna" },
        stepId: { required: true, inPath: true, description: "Step id, e.g. contract" },
        doc: { required: true, description: "The document's name as shown on the step, e.g. Signed contract" },
      },
      handle: ["caseId", "stepId"],
      csrf: null,
    },
    {
      name: "reopen_onboarding_step",
      description:
        "Reopen a DONE onboarding step (undo a mistaken completion). The step returns to in-progress and " +
        "shows again in list_onboarding_steps. Manager/HR only.",
      path: "/onboarding/case/${caseId}/step/${stepId}/reopen",
      form: {},
      args: {
        caseId: { required: true, inPath: true, description: "Onboarding case id, e.g. onb-anna" },
        stepId: { required: true, inPath: true, description: "Step id, e.g. payroll" },
        title: { required: true, description: "The step's title (shown in the confirmation toast)." },
      },
      handle: ["caseId", "stepId"],
      csrf: null,
    },
    {
      name: "convert_onboarding_to_employee",
      description:
        "Convert a 100%-complete onboarding case into a directory employee record. HR only; only offered " +
        "once every step is done and the case hasn't been converted yet.",
      path: "/onboarding/case/${caseId}/convert",
      form: {},
      args: { caseId: { required: true, inPath: true, description: "Onboarding case id, e.g. onb-anna" } },
      handle: ["caseId"],
      csrf: null,
    },

    // ---- onboarding templates (HR schema editor) ----
    {
      name: "create_onboarding_template",
      description:
        "Create a new onboarding template for a role family. HR only. Returns the new template id as " +
        "`created` (pass to get_onboarding_template / add_template_step to build it out). Template edits " +
        "apply to FUTURE cases only.",
      path: "/onboarding/templates",
      form: {},
      args: {
        name: { required: false, description: "Template name, e.g. Marketing hire" },
        role: { required: true, description: "Role family it serves: design, eng, sales, or general" },
      },
      handle: [],
      csrf: null,
      returns: { fromLocation: "/onboarding/templates/(.+)$" },
    },
    {
      name: "update_onboarding_template",
      description:
        "Update an onboarding template's name, role family, and/or description (from " +
        "list_onboarding_templates). Pass only the fields to change. HR only.",
      path: "/onboarding/templates/${tplId}/meta",
      form: {},
      args: {
        tplId: { required: true, inPath: true, description: "Template id, e.g. tpl-eng" },
        name: { required: false, description: "New template name" },
        role: { required: false, description: "New role family: design, eng, sales, or general" },
        description: { required: false, description: "New description" },
      },
      handle: ["tplId"],
      csrf: null,
    },
    {
      name: "delete_onboarding_template",
      description:
        "Delete an onboarding template (from list_onboarding_templates). HR only. Blocked while any " +
        "active onboarding still uses it — the delete then fails loud.",
      path: "/onboarding/templates/${tplId}/delete",
      form: {},
      args: { tplId: { required: true, inPath: true, description: "Template id to delete" } },
      handle: ["tplId"],
      csrf: null,
      verify: { via: "list_onboarding_templates" },
    },
    {
      name: "add_template_step",
      description:
        "Append a blank step to an onboarding template (it lands at the end titled 'New step'). HR only. " +
        "Fill it in with edit_template_step (get its stepId from get_onboarding_template).",
      path: "/onboarding/templates/${tplId}/steps",
      form: {},
      args: { tplId: { required: true, inPath: true, description: "Template id, e.g. tpl-eng" } },
      handle: ["tplId"],
      csrf: null,
    },
    {
      name: "edit_template_step",
      description:
        "Edit one template step (tplId + stepId from get_onboarding_template). `action` picks the " +
        "operation: save (default — apply the field values), up / down (reorder), auto (toggle Dioschub " +
        "auto-execution), doc (toggle a required document; set docName), or delete (remove the step). " +
        "Field values passed alongside a non-delete action are saved first. HR only.",
      path: "/onboarding/templates/${tplId}/step/${stepId}",
      form: {},
      args: {
        tplId: { required: true, inPath: true, description: "Template id, e.g. tpl-eng" },
        stepId: { required: true, inPath: true, description: "Step id from get_onboarding_template" },
        action: { required: false, description: "save (default), up, down, auto, doc, or delete" },
        title: { required: false, description: "Step title" },
        system: { required: false, description: "Target system id, e.g. okta, gsuite, payroll" },
        owner: { required: false, description: "Owning team, e.g. IT, HR Ops, Manager" },
        dueOffset: { required: false, description: "Due day relative to start date (integer; negative = before)" },
        dependsOn: { required: false, description: "Step id this step waits on; empty to clear" },
        docName: { required: false, description: "Required document name (with the doc toggle on)" },
      },
      handle: ["tplId", "stepId"],
      csrf: null,
    },

    // ---- offboarding (start / complete / cancel) ----
    {
      name: "start_offboarding",
      description:
        "Start an offboarding (exit) case for an employee — creates the exit checklist shown in " +
        "list_offboarding. Manager (own reports) or HR. The employee stays active until the case is " +
        "completed.",
      path: "/offboarding/new",
      form: {},
      args: {
        empId: { required: true, description: "Employee id to offboard, e.g. ken.watanabe" },
        type: { required: true, description: "Exit type: resignation, termination, end_contract, or retirement" },
        lastDay: { required: true, description: "Last working day, e.g. 2026-08-15" },
        reason: { required: false, description: "Free-text reason / context" },
      },
      handle: ["empId"],
      csrf: null,
    },
    {
      name: "complete_offboarding",
      description:
        "Complete an offboarding case (from list_offboarding) once EVERY checklist task is done — marks " +
        "the employee inactive in the directory. Fails loud if tasks remain. Manager/HR only.",
      path: "/offboarding/${caseId}/complete",
      form: {},
      args: { caseId: { required: true, inPath: true, description: "Offboarding case id, e.g. off-seed-ken" } },
      handle: ["caseId"],
      csrf: null,
      verify: { via: "list_offboarding" },
    },
    {
      name: "cancel_offboarding",
      description:
        "Cancel an in-progress offboarding case (from list_offboarding) — the employee is NOT exited and " +
        "the checklist is discarded. Manager/HR only.",
      path: "/offboarding/${caseId}/cancel",
      form: {},
      args: { caseId: { required: true, inPath: true, description: "Offboarding case id" } },
      handle: ["caseId"],
      csrf: null,
      verify: { via: "list_offboarding" },
    },

    // ---- performance: cycle lifecycle (HR) ----
    {
      name: "create_cycle",
      description:
        "Create a new DRAFT review cycle. HR only. Returns the new cycle id as `created` — then shape it " +
        "with rename_cycle / set_cycle_timeline / the competency tools, and launch_cycle when ready.",
      path: "/performance/cycle/new",
      form: {},
      args: {},
      handle: [],
      csrf: null,
      returns: { fromLocation: "cycle=([^&]+)" },
    },
    {
      name: "launch_cycle",
      description:
        "Launch a DRAFT cycle (from list_cycles), opening self-assessments for every participant. HR only. " +
        "Requires a name, weights totalling exactly 100%, and at least one participant — otherwise nothing " +
        "launches.",
      path: "/performance/cycle/${id}/launch",
      form: {},
      args: { id: { required: true, inPath: true, description: "Cycle id, e.g. cyc-h1-2026" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "close_cycle",
      description: "Close an ACTIVE review cycle (from list_cycles) — reviews lock and it becomes read-only. HR only.",
      path: "/performance/cycle/${id}/close",
      form: {},
      args: { id: { required: true, inPath: true, description: "Cycle id to close" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "delete_cycle",
      description: "Delete a DRAFT review cycle (from list_cycles). Only drafts can be deleted. HR only.",
      path: "/performance/cycle/${id}/delete",
      form: {},
      args: { id: { required: true, inPath: true, description: "Draft cycle id to delete" } },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_cycles" },
    },

    // ---- performance: cycle designer (HR, draft cycles) ----
    {
      name: "rename_cycle",
      description: "Rename a review cycle in the designer (draft cycles only take effect). HR only.",
      path: "/performance/designer/name",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id, e.g. cyc-1720000000" },
        name: { required: true, description: "New cycle name, e.g. H2 2026 Review" },
      },
      handle: ["cycle"],
      csrf: null,
    },
    {
      name: "set_cycle_timeline",
      description:
        "Set a cycle's type and/or timeline dates in the designer. Pass only what changes. HR only.",
      path: "/performance/designer/timeline",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id" },
        type: { required: false, description: "Cycle type id, e.g. annual, mid_year (see the designer's options)" },
        startDate: { required: false, description: "Cycle start, e.g. 2026-07-01" },
        selfDue: { required: false, description: "Self-assessment due date" },
        mgrDue: { required: false, description: "Manager assessment due date" },
        calibrationDate: { required: false, description: "Calibration date (cycle end)" },
      },
      handle: ["cycle"],
      csrf: null,
    },
    {
      name: "set_competency_weight",
      description:
        "Set one competency's weight (%) in a cycle. Weights must total exactly 100% before the cycle can " +
        "launch — use balance_cycle_weights to even them out. HR only.",
      path: "/performance/designer/weight",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id" },
        comp: { required: true, description: "Competency id, e.g. exec, craft, collab, comm, owner" },
        weight: { required: true, description: "Weight percentage (integer), e.g. 25" },
      },
      handle: ["cycle", "comp"],
      csrf: null,
    },
    {
      name: "add_cycle_competency",
      description:
        "Add a competency from the workspace library to a cycle (weight starts at 0). Competency ids come " +
        "from the library shown in get_admin_settings. HR only.",
      path: "/performance/designer/comp/add",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id" },
        comp: { required: true, description: "Library competency id to add" },
      },
      handle: ["cycle", "comp"],
      csrf: null,
    },
    {
      name: "remove_cycle_competency",
      description: "Remove a competency from a cycle (a cycle keeps at least one). HR only.",
      path: "/performance/designer/comp/remove",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id" },
        comp: { required: true, description: "Competency id to remove" },
      },
      handle: ["cycle", "comp"],
      csrf: null,
    },
    {
      name: "balance_cycle_weights",
      description: "Auto-balance a cycle's competency weights to total exactly 100%. HR only.",
      path: "/performance/designer/balance",
      form: {},
      args: { cycle: { required: true, description: "Cycle id" } },
      handle: ["cycle"],
      csrf: null,
    },

    // ---- performance: review flow ----
    {
      name: "submit_self_review",
      description:
        "Submit the self-assessment for a review (cycle + emp from list_reviews). The employee themselves " +
        "(or HR) may submit, once. `scores` is a JSON object of competency id → 1-5 score, e.g. " +
        '{"exec": 4, "craft": 3} — competency ids and current state come from get_review.',
      path: "/performance/review/self",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id, e.g. cyc-h1-2026" },
        emp: { required: true, description: "Employee id the review is about" },
        narrative: { required: false, description: "Self-assessment narrative" },
        scores: { required: true, description: 'JSON object of competency id → score (1-5), e.g. {"exec": 4, "craft": 3}', expandJson: { prefix: "s_" } },
      },
      handle: ["cycle", "emp"],
      csrf: null,
    },
    {
      name: "submit_manager_review",
      description:
        "Submit the manager assessment for a review, opening calibration. The assigned reviewer (or HR) " +
        "may submit, after the self-assessment is in. `scores` is a JSON object of competency id → 1-5 " +
        "score (see get_review).",
      path: "/performance/review/manager",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id" },
        emp: { required: true, description: "Employee id the review is about" },
        narrative: { required: false, description: "Manager assessment narrative" },
        scores: { required: true, description: 'JSON object of competency id → score (1-5), e.g. {"exec": 4}', expandJson: { prefix: "s_" } },
      },
      handle: ["cycle", "emp"],
      csrf: null,
    },
    {
      name: "commit_calibration",
      description:
        "Set final calibrated scores and commit the review (the reviewer or HR, after the manager " +
        "assessment). `scores` is a JSON object of competency id → final 1-5 score. Committing locks the " +
        "review; HR can reopen_calibration.",
      path: "/performance/review/commit",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id" },
        emp: { required: true, description: "Employee id the review is about" },
        scores: { required: true, description: 'JSON object of competency id → calibrated score (1-5)', expandJson: { prefix: "s_" } },
      },
      handle: ["cycle", "emp"],
      csrf: null,
    },
    {
      name: "reopen_calibration",
      description: "Reopen a COMMITTED review's calibration (undo the commit). HR only.",
      path: "/performance/review/reopen",
      form: {},
      args: {
        cycle: { required: true, description: "Cycle id" },
        emp: { required: true, description: "Employee id the review is about" },
      },
      handle: ["cycle", "emp"],
      csrf: null,
    },

    // ---- recruitment: requisition lifecycle ----
    {
      name: "create_requisition",
      description:
        "Create a new DRAFT requisition. HR only. Returns the new requisition id as `created` — then fill " +
        "it in with update_requisition_details / toggle_panel_member and submit_requisition when ready.",
      path: "/recruitment/req/new",
      form: {},
      args: {},
      handle: [],
      csrf: null,
      returns: { fromLocation: "/recruitment/req/([^/]+)$" },
    },
    {
      name: "update_requisition_details",
      description:
        "Save a DRAFT requisition's role details (from get_requisition's designer). All fields are " +
        "submitted together — pass current values for anything unchanged. Manager/HR only.",
      path: "/recruitment/req/${id}/details",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Requisition id, e.g. REQ-2060" },
        title: { required: true, description: "Role title, e.g. Senior Product Designer" },
        dept: { required: true, description: "Department name" },
        level: { required: true, description: "Level name" },
        headcount: { required: false, description: "Openings (integer, default 1)" },
        location: { required: true, description: "Location, e.g. Remote · US" },
        ownerId: { required: true, description: "Hiring manager's employee id" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "toggle_panel_member",
      description:
        "Toggle one person on/off a DRAFT requisition's interview panel for a stage (get_requisition shows " +
        "the rounds and current panel). Manager/HR only.",
      path: "/recruitment/req/${id}/panel/toggle",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Requisition id" },
        stage: { required: true, description: "Interview stage id, e.g. screen, interview, onsite" },
        person: { required: true, description: "Employee id to toggle on the panel" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "submit_requisition",
      description:
        "Submit a DRAFT requisition for approval (it then shows in list_requisition_approvals for HR). " +
        "Needs a role title, at least one scorecard attribute, and every interview round staffed — " +
        "otherwise it stays a draft. Manager/HR only.",
      path: "/recruitment/req/${id}/submit",
      form: {},
      args: { id: { required: true, inPath: true, description: "Requisition id to submit" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "reject_requisition",
      description:
        "Reject a requisition pending approval (from list_requisition_approvals) — it returns to draft for " +
        "rework. HR only.",
      path: "/recruitment/req/${id}/reject",
      form: {},
      args: { id: { required: true, inPath: true, description: "Requisition id to reject" } },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_requisition_approvals" },
    },
    {
      name: "delete_requisition",
      description: "Delete a DRAFT requisition (from list_requisitions). Only drafts can be deleted. HR only.",
      path: "/recruitment/req/${id}/delete",
      form: {},
      args: { id: { required: true, inPath: true, description: "Draft requisition id to delete" } },
      handle: ["id"],
      csrf: null,
      verify: { via: "list_requisitions" },
    },
    {
      name: "close_requisition",
      description:
        "Close an OPEN requisition, marking it filled (default) or just closed (`filled=false`). " +
        "Manager/HR only.",
      path: "/recruitment/req/${id}/close",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Requisition id to close" },
        filled: { required: false, description: "true (default) = filled; false = closed unfilled" },
      },
      handle: ["id"],
      csrf: null,
    },

    // ---- recruitment: pipeline + candidate ----
    {
      name: "add_candidate",
      description:
        "Add a candidate to an OPEN requisition's pipeline (they start at Applied). Manager/HR only. The " +
        "new candidate's id appears in get_pipeline as `<reqId>-<name-slug>`.",
      path: "/recruitment/req/${id}/candidate",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Requisition id, e.g. REQ-2044" },
        name: { required: true, description: "Candidate's full name" },
        currentRole: { required: false, description: "Current role & company, e.g. Staff Eng, GitLab" },
        source: { required: false, description: "Source, e.g. LinkedIn, Referral, Agency" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "submit_scorecard",
      description:
        "Submit YOUR interview scorecard for a candidate at a scored stage they have reached (interview " +
        "or onsite). Only panel members of that round can submit; resubmitting replaces yours. `ratings` " +
        "is a JSON object of scorecard attribute id → 1-5 rating; keys must be attribute ids on the " +
        "requisition's scorecard (library ids: coding, system, problem, comm, collab, product, craft, " +
        "ownership, sales, domain, culture) and at least one valid rating is required or the app refuses " +
        "the card.",
      path: "/recruitment/candidate/${id}/scorecard",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Candidate id from get_pipeline" },
        stage: { required: true, description: "The scored stage id the candidate has reached: interview or onsite" },
        rec: { required: true, description: "Recommendation: strong_yes, yes, no, or strong_no" },
        comment: { required: false, description: "Debrief comment" },
        ratings: { required: true, description: 'JSON object of attribute id → rating (1-5), e.g. {"problem": 4, "comm": 5}', expandJson: { prefix: "rating_" } },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "add_candidate_note",
      description: "Post a note on a candidate's timeline (visible to everyone driving the pipeline). Manager/HR only.",
      path: "/recruitment/candidate/${id}/note",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Candidate id" },
        text: { required: true, description: "The note text" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "advance_candidate",
      description:
        "Advance a candidate to the next pipeline stage (Applied → Screen → Interview → Onsite → Offer). " +
        "Not possible from offer / hired / rejected — use the offer tools or reopen_candidate. Manager/HR only.",
      path: "/recruitment/candidate/${id}/advance",
      form: {},
      args: { id: { required: true, inPath: true, description: "Candidate id to advance" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "reject_candidate",
      description: "Reject a candidate (moves them to the Rejected column), optionally with a reason. Manager/HR only.",
      path: "/recruitment/candidate/${id}/reject",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Candidate id to reject" },
        reason: { required: false, description: "Rejection reason, e.g. Not a level fit" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "reopen_candidate",
      description: "Reopen a REJECTED candidate — they return to the Screen stage. Manager/HR only.",
      path: "/recruitment/candidate/${id}/reopen",
      form: {},
      args: { id: { required: true, inPath: true, description: "Rejected candidate id to reopen" } },
      handle: ["id"],
      csrf: null,
    },

    // ---- recruitment: offer flow ----
    {
      name: "make_offer",
      description:
        "Draft an offer for a candidate at the Interview/Onsite stage (moves them to Offer, pending " +
        "approval — approve_offer next). Manager/HR only.",
      path: "/recruitment/candidate/${id}/offer/make",
      form: {},
      args: {
        id: { required: true, inPath: true, description: "Candidate id" },
        base: { required: true, description: "Base salary (integer), e.g. 185000" },
        bonus: { required: true, description: "Bonus (integer), e.g. 20000" },
        equity: { required: true, description: "Equity percentage (number), e.g. 0.25" },
        level: { required: true, description: "Offered level, e.g. Senior" },
        startDate: { required: true, description: "Proposed start date, e.g. 2026-09-01" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "approve_offer",
      description: "Approve a drafted offer (VP/HR sign-off) so it can be extended to the candidate. HR only.",
      path: "/recruitment/candidate/${id}/offer/approve",
      form: {},
      args: { id: { required: true, inPath: true, description: "Candidate id with a drafted offer" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "extend_offer",
      description: "Extend an approved offer to the candidate. Manager/HR only.",
      path: "/recruitment/candidate/${id}/offer/extend",
      form: {},
      args: { id: { required: true, inPath: true, description: "Candidate id with an approved offer" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "accept_offer",
      description:
        "Record the candidate accepting the extended offer — hires them and automatically opens an " +
        "onboarding case (linked from get_candidate). Manager/HR only.",
      path: "/recruitment/candidate/${id}/offer/accept",
      form: {},
      args: { id: { required: true, inPath: true, description: "Candidate id with an extended offer" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "decline_offer",
      description: "Record the candidate declining the extended offer. Manager/HR only.",
      path: "/recruitment/candidate/${id}/offer/decline",
      form: {},
      args: { id: { required: true, inPath: true, description: "Candidate id with an extended offer" } },
      handle: ["id"],
      csrf: null,
    },

    // ---- admin: policy settings (HR only) ----
    {
      name: "set_work_week_target",
      description: "Set the weekly working-hours target (0-80) used by the timesheet grid. HR only.",
      path: "/settings/workweek",
      form: {},
      args: { targetHours: { required: true, description: "Weekly target hours (integer), e.g. 40" } },
      handle: [],
      csrf: null,
    },
    {
      name: "set_leave_allowances",
      description:
        "Set annual leave allowances per leave type. `allowances` is a JSON object of leave-type id → " +
        'days, e.g. {"annual": 26, "sick": 12}. Type ids: annual, sick, personal, parental, unpaid, ' +
        "bereavement (see get_admin_settings). HR only.",
      path: "/settings/allowances",
      form: {},
      args: {
        allowances: { required: true, description: 'JSON object of leave-type id → allowance days, e.g. {"annual": 26}', expandJson: { prefix: "allow_" } },
      },
      handle: [],
      csrf: null,
    },
    {
      name: "set_leave_rules",
      description:
        "Set the leave approval rules: notice days required, the manager approval ceiling (days — above it " +
        "escalates to HR), and the sick-certificate threshold. Pass only what changes. HR only.",
      path: "/settings/rules",
      form: {},
      args: {
        noticeDays: { required: false, description: "Minimum notice days before leave starts" },
        ceilingDays: { required: false, description: "Manager approval ceiling in days; longer requests escalate to HR" },
        sickCertDays: { required: false, description: "Sick days after which a certificate is required" },
      },
      handle: [],
      csrf: null,
    },
    {
      name: "add_blackout",
      description:
        "Add a blackout period (dates leave requests are blocked for all teams). The new blackout's id " +
        "(bo-…) appears in the Settings page's remove control. HR only.",
      path: "/settings/blackouts/add",
      form: {},
      args: {
        label: { required: false, description: "Blackout label, e.g. Year-end freeze" },
        start: { required: true, description: "First blocked day, e.g. 2026-12-22" },
        end: { required: true, description: "Last blocked day, e.g. 2026-12-31" },
      },
      handle: [],
      csrf: null,
    },
    {
      name: "remove_blackout",
      description: "Remove a blackout period by its id (bo-…). HR only.",
      path: "/settings/blackouts/remove",
      form: {},
      args: { id: { required: true, description: "Blackout id, e.g. bo-seed-yearend" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "add_holiday",
      description: "Add a public holiday to the workspace calendar (excluded from leave-day counts and timesheets). HR only.",
      path: "/settings/holidays/add",
      form: {},
      args: {
        date: { required: true, description: "Holiday date, e.g. 2026-12-25" },
        name: { required: false, description: "Holiday name, e.g. Christmas Day" },
      },
      handle: [],
      csrf: null,
    },
    {
      name: "remove_holiday",
      description: "Remove a public holiday by its id (h…). HR only.",
      path: "/settings/holidays/remove",
      form: {},
      args: { id: { required: true, description: "Holiday id, e.g. h1720000000000" } },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "add_competency",
      description:
        "Add a competency to the workspace review library (usable in cycles via add_cycle_competency). HR only.",
      path: "/settings/competencies/add",
      form: {},
      args: { name: { required: true, description: "Competency name, e.g. Mentorship" } },
      handle: [],
      csrf: null,
    },
    {
      name: "update_competency",
      description:
        "Rename a library competency and/or update its description. Competency ids: exec, craft, collab, " +
        "comm, owner for the seeded ones; c<timestamp> for added ones. HR only.",
      path: "/settings/competencies/update",
      form: {},
      args: {
        id: { required: true, description: "Competency id, e.g. craft" },
        name: { required: false, description: "New name" },
        blurb: { required: false, description: "New one-line description" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "remove_competency",
      description:
        "Remove a library competency. Blocked while any review cycle references it, and the library keeps " +
        "at least one. HR only.",
      path: "/settings/competencies/remove",
      form: {},
      args: { id: { required: true, description: "Competency id to remove" } },
      handle: ["id"],
      csrf: null,
    },

    // ---- admin: org structure (HR only) ----
    {
      name: "add_department",
      description: "Add a department to the org structure (name must be unique). HR only.",
      path: "/org/departments/add",
      form: {},
      args: { name: { required: true, description: "Department name, e.g. Marketing" } },
      handle: [],
      csrf: null,
    },
    {
      name: "remove_department",
      description: "Remove a department by name. Blocked while any employee is still assigned to it. HR only.",
      path: "/org/departments/remove",
      form: {},
      args: { name: { required: true, description: "Department name to remove" } },
      handle: [],
      csrf: null,
    },
    {
      name: "add_level",
      description: "Add a level to the org's level ladder (appended at the bottom; name must be unique). HR only.",
      path: "/org/levels/add",
      form: {},
      args: { name: { required: true, description: "Level name, e.g. Principal" } },
      handle: [],
      csrf: null,
    },
    {
      name: "remove_level",
      description: "Remove a level by name. Blocked while any employee is at that level. HR only.",
      path: "/org/levels/remove",
      form: {},
      args: { name: { required: true, description: "Level name to remove" } },
      handle: [],
      csrf: null,
    },
    {
      name: "add_comp_band",
      description:
        "Add a new comp band to a track (IC, Manager, or Executive) — it gets the next band id on that " +
        "track (see get_org_structure). Set its label/range with update_comp_band. HR only.",
      path: "/org/bands/add",
      form: {},
      args: { track: { required: true, description: "Track: IC, Manager, or Executive" } },
      handle: [],
      csrf: null,
    },
    {
      name: "update_comp_band",
      description:
        "Update a comp band's label and/or salary range (band ids like B3 come from get_org_structure). " +
        "Max must be at least min. HR only.",
      path: "/org/bands/update",
      form: {},
      args: {
        id: { required: true, description: "Band id, e.g. B3" },
        label: { required: false, description: "New band label, e.g. Senior IC" },
        min: { required: false, description: "Range minimum (integer salary)" },
        max: { required: false, description: "Range maximum (integer salary)" },
      },
      handle: ["id"],
      csrf: null,
    },
    {
      name: "remove_comp_band",
      description: "Remove a comp band by id. Blocked while any employee is on that band. HR only.",
      path: "/org/bands/remove",
      form: {},
      args: { id: { required: true, description: "Band id to remove" } },
      handle: ["id"],
      csrf: null,
    },

    // ---- admin: roles & access (HR only) ----
    {
      name: "set_access_role",
      description:
        "Set an employee's access role: employee, manager, or hr (the RBAC gate for every surface — see " +
        "get_roles_and_access). You cannot change your own role, and the last HR executive cannot be " +
        "demoted. HR only.",
      path: "/roles/set",
      form: {},
      args: {
        empId: { required: true, description: "Employee id whose access to change" },
        role: { required: true, description: "New access role: employee, manager, or hr" },
      },
      handle: ["empId"],
      csrf: null,
    },
  ],
};
