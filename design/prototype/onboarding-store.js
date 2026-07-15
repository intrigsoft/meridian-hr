// Onboarding data layer for the Meridian HR fixture.
// Stands in for the server-side onboarding tables + orchestration engine a
// Thymeleaf app would own. Everything persists to localStorage.
//
// Model:
//   SYSTEMS   — catalog of downstream systems Dioschub writes to
//   OWNERS    — who is accountable for a step
//   templates — role-based onboarding SCHEMAS (ordered step definitions).
//               A new hire's role picks the schema.
//   cases     — running onboarding INSTANCES created from a template, each
//               carrying per-step progress. Step status is COMPUTED from
//               completion + document uploads + dependencies, so completing a
//               step automatically releases whatever was waiting on it.

const TPL_KEY = "meridian.onboarding.templates.v1";
const CASE_KEY = "meridian.onboarding.cases.v1";

// ---- Downstream systems catalog (tag styling shared across screens) ----
export const SYSTEMS = {
  azure_ad:   { label: "Azure AD",     color: "#2f6aa8", bg: "#e9f0f9" },
  google_ws:  { label: "Google WS",    color: "#3d8564", bg: "#ecf5f0" },
  slack:      { label: "Slack",        color: "#7a5aa8", bg: "#f0ecf8" },
  servicenow: { label: "ServiceNow",   color: "#7a5aa8", bg: "#f0ecf8" },
  workday:    { label: "Workday Pay",  color: "#9a6a1a", bg: "#f7f1e4" },
  genetec:    { label: "Genetec",      color: "#b23b2e", bg: "#fbeae8" },
  docebo:     { label: "Docebo LMS",   color: "#5a6472", bg: "#eef1f4" },
  onepass:    { label: "1Password",    color: "#2f6aa8", bg: "#e9f0f9" },
  manual:     { label: "Manual",       color: "#6b7480", bg: "#eef1f4" },
};

export const OWNERS = ["People Ops", "IT", "Facilities", "Payroll", "Hiring Manager", "Security"];

// Roles a schema can target. Used to auto-match a template to a new hire.
export const ROLES = [
  { id: "design",  label: "Design / Product", dept: "Design" },
  { id: "eng",     label: "Engineering",      dept: "Engineering" },
  { id: "sales",   label: "Sales / GTM",      dept: "Revenue" },
  { id: "general", label: "General / Other",  dept: "Operations" },
];

let _sid = 0;
function sid() { return "s" + (Date.now().toString(36)) + (++_sid); }

// A step definition inside a template schema.
function step(order, title, system, owner, opts) {
  opts = opts || {};
  return {
    id: opts.id || ("st" + order),
    order,
    title,
    system,           // key into SYSTEMS
    owner,            // string from OWNERS
    requiresDoc: opts.requiresDoc || null,   // e.g. "Signed I-9" or null
    dependsOn: opts.dependsOn || null,       // step id this waits on, or null
    autoAssign: !!opts.autoAssign,           // Dioschub fires automatically once unblocked
    dueOffset: opts.dueOffset == null ? 0 : opts.dueOffset, // days from start date
  };
}

// ---- Seed templates: role-based schemas ----
function seedTemplates() {
  const spine = (roleId, extra) => {
    const s = [
      step(1, "Create identity & directory account", "azure_ad", "IT", { id: "identity", autoAssign: true, dueOffset: -2 }),
      step(2, "Provision email & calendar", "google_ws", "IT", { id: "email", dependsOn: "identity", autoAssign: true, dueOffset: -2 }),
      step(3, "Order hardware", "servicenow", "IT", { id: "hardware", dependsOn: "identity", dueOffset: -5 }),
      step(4, "Enroll in payroll & benefits", "workday", "Payroll", { id: "payroll", dependsOn: "identity", dueOffset: 0 }),
      step(5, "Issue building badge & access", "genetec", "Facilities", { id: "badge", dependsOn: "identity", requiresDoc: "Signed I-9", dueOffset: 0 }),
      step(6, "Assign compliance training", "docebo", "People Ops", { id: "training", dependsOn: "badge", autoAssign: true, dueOffset: 1 }),
    ];
    return (extra || []).length ? s.concat(extra) : s;
  };

  return [
    {
      id: "tpl-design", name: "Design / Product hire", role: "design", dept: "Design",
      description: "Standard schema for designers and PMs.",
      updatedAt: Date.now() - 86400000 * 9,
      steps: spine("design", [
        step(7, "Add to design tooling (Figma, Slack)", "slack", "Hiring Manager", { id: "tools", dependsOn: "email", dueOffset: 1 }),
      ]),
    },
    {
      id: "tpl-eng", name: "Engineering hire", role: "eng", dept: "Engineering",
      description: "Adds source access + hardware security key for engineers.",
      updatedAt: Date.now() - 86400000 * 4,
      steps: spine("eng", [
        step(7, "Provision source & CI access", "onepass", "IT", { id: "source", dependsOn: "identity", dueOffset: 0 }),
        step(8, "Issue hardware security key", "onepass", "Security", { id: "yubikey", dependsOn: "source", requiresDoc: "Key acknowledgement", dueOffset: 0 }),
      ]),
    },
    {
      id: "tpl-sales", name: "Sales / GTM hire", role: "sales", dept: "Revenue",
      description: "Adds CRM seat and territory assignment.",
      updatedAt: Date.now() - 86400000 * 2,
      steps: spine("sales", [
        step(7, "Assign CRM seat & territory", "slack", "Hiring Manager", { id: "crm", dependsOn: "email", dueOffset: 1 }),
      ]),
    },
    {
      id: "tpl-general", name: "General employee", role: "general", dept: "Operations",
      description: "Baseline schema for any role without a specialised template.",
      updatedAt: Date.now() - 86400000 * 20,
      steps: spine("general"),
    },
  ];
}

// ---- Seed cases: in-flight onboardings (continuity with the earlier demo) ----
function completed(byName, daysAgo, doc) {
  return { completed: true, completedAt: Date.now() - 86400000 * daysAgo, completedBy: byName || "Dioschub", docUploaded: !!doc };
}
function seedCases() {
  return [
    {
      id: "onb-anna", hireName: "Anna Kim", initials: "AK", avatarBg: "#b56b8f",
      role: "design", roleLabel: "Product Designer", dept: "Design",
      email: "akim@meridian.co", startDate: "2026-07-13", manager: "David Okonkwo",
      templateId: "tpl-design", createdAt: Date.now() - 86400000 * 6,
      steps: {
        identity: completed("Dioschub", 5),
        email: completed("Dioschub", 5),
        hardware: completed("IT · S. Patel", 3),
        // payroll in progress (not complete)
        // badge blocked on I-9 (dependency identity done, but requiresDoc, no upload)
        // training waiting on badge
        // tools in progress
      },
    },
    {
      id: "onb-ravi", hireName: "Ravi Menon", initials: "RM", avatarBg: "#6b7db5",
      role: "eng", roleLabel: "Backend Engineer", dept: "Engineering",
      email: "rmenon@meridian.co", startDate: "2026-07-13", manager: "David Okonkwo",
      templateId: "tpl-eng", createdAt: Date.now() - 86400000 * 6,
      steps: {
        identity: completed("Dioschub", 5),
        email: completed("Dioschub", 5),
        hardware: completed("IT · S. Patel", 4),
        payroll: completed("Payroll", 2),
        source: completed("IT · S. Patel", 2),
        // badge in progress, yubikey waiting, training waiting
      },
    },
    {
      id: "onb-sofia", hireName: "Sofia Alvarez", initials: "SA", avatarBg: "#6ba58f",
      role: "sales", roleLabel: "Account Executive", dept: "Revenue",
      email: "salvarez@meridian.co", startDate: "2026-06-29", manager: "Elena Vasquez",
      templateId: "tpl-sales", createdAt: Date.now() - 86400000 * 20,
      steps: {
        identity: completed("Dioschub", 19),
        email: completed("Dioschub", 19),
        hardware: completed("IT", 17),
        payroll: completed("Payroll", 16),
        badge: completed("Facilities", 15, true),
        training: completed("Dioschub", 14),
        crm: completed("E. Vasquez", 13),
      },
    },
  ];
}

// ---- Persistence ----
export function allTemplates() {
  try {
    const raw = localStorage.getItem(TPL_KEY);
    if (!raw) { const s = seedTemplates(); localStorage.setItem(TPL_KEY, JSON.stringify(s)); return s; }
    return JSON.parse(raw);
  } catch (e) { return seedTemplates(); }
}
function saveTemplates(list) { localStorage.setItem(TPL_KEY, JSON.stringify(list)); }

export function getTemplate(id) { return allTemplates().find((t) => t.id === id) || null; }
export function templateForRole(role) {
  const list = allTemplates();
  return list.find((t) => t.role === role) || list.find((t) => t.role === "general") || list[0] || null;
}

export function saveTemplate(tpl) {
  const list = allTemplates();
  const idx = list.findIndex((t) => t.id === tpl.id);
  tpl.updatedAt = Date.now();
  // renumber step order
  tpl.steps = (tpl.steps || []).map((s, i) => ({ ...s, order: i + 1 }));
  if (idx >= 0) list[idx] = tpl; else list.push(tpl);
  saveTemplates(list);
  return tpl;
}
export function createTemplate(name, role) {
  const r = ROLES.find((x) => x.id === role) || ROLES[3];
  const tpl = {
    id: "tpl-" + Math.random().toString(36).slice(2, 8),
    name: name || "New template", role, dept: r.dept,
    description: "", updatedAt: Date.now(),
    steps: [ step(1, "Create identity & directory account", "azure_ad", "IT", { id: "identity", autoAssign: true, dueOffset: -2 }) ],
  };
  const list = allTemplates(); list.push(tpl); saveTemplates(list);
  return tpl;
}
export function deleteTemplate(id) {
  saveTemplates(allTemplates().filter((t) => t.id !== id));
}
export function newStepDef() {
  return step(99, "New step", "manual", "People Ops", { id: sid() });
}

// ---- Cases (instances) ----
export function allCases() {
  try {
    const raw = localStorage.getItem(CASE_KEY);
    if (!raw) { const s = seedCases(); localStorage.setItem(CASE_KEY, JSON.stringify(s)); return s; }
    return JSON.parse(raw);
  } catch (e) { return seedCases(); }
}
function saveCases(list) { localStorage.setItem(CASE_KEY, JSON.stringify(list)); }
export function getCase(id) { return allCases().find((c) => c.id === id) || null; }

const AVATARS = ["#b56b8f", "#6b7db5", "#6ba58f", "#c07f4f", "#7a6bb5", "#4a9d7a", "#c99b4e"];
function initialsOf(name) {
  return name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join("").toUpperCase() || "?";
}

export function startOnboarding(hire) {
  // hire: { hireName, role, roleLabel, startDate, manager, email }
  const list = allCases();
  const tpl = templateForRole(hire.role);
  const r = ROLES.find((x) => x.id === hire.role) || ROLES[3];
  const c = {
    id: "onb-" + Math.random().toString(36).slice(2, 8),
    hireName: hire.hireName,
    initials: initialsOf(hire.hireName),
    avatarBg: AVATARS[list.length % AVATARS.length],
    role: hire.role,
    roleLabel: hire.roleLabel || r.label,
    dept: r.dept,
    email: hire.email || (hire.hireName.toLowerCase().replace(/[^a-z]+/g, ".") + "@meridian.co"),
    startDate: hire.startDate,
    manager: hire.manager || "—",
    templateId: tpl ? tpl.id : null,
    createdAt: Date.now(),
    steps: {}, // nothing done yet — Dioschub will fire autoAssign steps as they unblock
  };
  list.unshift(c);
  saveCases(list);
  return c;
}

export function completeStep(caseId, stepId, byName) {
  const list = allCases();
  const idx = list.findIndex((c) => c.id === caseId);
  if (idx < 0) return;
  const c = { ...list[idx], steps: { ...list[idx].steps } };
  const prev = c.steps[stepId] || {};
  c.steps[stepId] = { ...prev, completed: true, completedAt: Date.now(), completedBy: byName || "You" };
  list[idx] = c;
  saveCases(list);
}
export function reopenStep(caseId, stepId) {
  const list = allCases();
  const idx = list.findIndex((c) => c.id === caseId);
  if (idx < 0) return;
  const c = { ...list[idx], steps: { ...list[idx].steps } };
  const prev = c.steps[stepId] || {};
  c.steps[stepId] = { ...prev, completed: false, completedAt: null };
  list[idx] = c;
  saveCases(list);
}
export function uploadDoc(caseId, stepId) {
  const list = allCases();
  const idx = list.findIndex((c) => c.id === caseId);
  if (idx < 0) return;
  const c = { ...list[idx], steps: { ...list[idx].steps } };
  const prev = c.steps[stepId] || {};
  c.steps[stepId] = { ...prev, docUploaded: true };
  list[idx] = c;
  saveCases(list);
}
export function deleteCase(id) { saveCases(allCases().filter((c) => c.id !== id)); }

// ---- Status computation ----
// Returns the resolved plan for a case: array of { def, state, status } in order.
// status: done | in_progress | blocked | waiting
export function resolvePlan(c) {
  const tpl = getTemplate(c.templateId);
  if (!tpl) return [];
  const steps = [...tpl.steps].sort((a, b) => a.order - b.order);
  const doneOf = (id) => !!(c.steps[id] && c.steps[id].completed);

  return steps.map((def) => {
    const state = c.steps[def.id] || {};
    let status;
    if (state.completed) {
      status = "done";
    } else if (def.dependsOn && !doneOf(def.dependsOn)) {
      status = "waiting";
    } else if (def.requiresDoc && !state.docUploaded) {
      status = "blocked";
    } else {
      status = "in_progress";
    }
    return { def, state, status };
  });
}

export function progressOf(c) {
  const plan = resolvePlan(c);
  if (!plan.length) return { done: 0, total: 0, pct: 0 };
  const done = plan.filter((p) => p.status === "done").length;
  return { done, total: plan.length, pct: Math.round((done / plan.length) * 100) };
}

export function caseSummary(c) {
  const plan = resolvePlan(c);
  const prog = progressOf(c);
  const blocked = plan.filter((p) => p.status === "blocked").length;
  let health;
  if (prog.pct === 100) health = "complete";
  else if (blocked > 0) health = "blocked";
  else health = "on_track";
  return { ...prog, blocked, health };
}

export function activeSummary() {
  const cases = allCases();
  const active = cases.filter((c) => caseSummary(c).health !== "complete");
  const blocked = cases.filter((c) => caseSummary(c).health === "blocked");
  return { total: cases.length, active: active.length, blocked: blocked.length };
}

export function resetOnboarding() {
  localStorage.removeItem(TPL_KEY);
  localStorage.removeItem(CASE_KEY);
}
