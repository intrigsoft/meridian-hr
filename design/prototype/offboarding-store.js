// Offboarding / exit data layer for the Meridian HR fixture.
// Closes the employee lifecycle: an exit case carries a reason, last day, and a
// checklist of exit tasks. Completing a case marks the person inactive in the
// Directory and writes an "Offboarded" event to their job history.
// Persists to localStorage; mirrors the onboarding store's shape.

import * as ppl from "./people-store.js";

const CASE_KEY = "meridian.offboarding.cases.v1";

export const EXIT_TYPES = [
  { id: "resignation", label: "Resignation", color: "#3a5aa8", bg: "#e8eefb" },
  { id: "termination", label: "Termination", color: "#b23b2e", bg: "#fbe9e7" },
  { id: "end_contract", label: "End of contract", color: "#9a6a1a", bg: "#f7f1e0" },
  { id: "retirement", label: "Retirement", color: "#2f6f4f", bg: "#e6f3ec" },
];
export function exitType(id) { return EXIT_TYPES.find((t) => t.id === id) || EXIT_TYPES[0]; }

export const STATUS_META = {
  in_progress: { label: "In progress", pillBg: "#faf3e6", pillFg: "#9a6a1a" },
  completed:   { label: "Completed",   pillBg: "#e6f3ec", pillFg: "#2f6f4f" },
};

// Standard exit checklist — owner is the team responsible.
function defaultChecklist() {
  return [
    { id: "notice", label: "Resignation / notice acknowledged", owner: "HR", done: false },
    { id: "knowledge", label: "Knowledge transfer & handover plan", owner: "Manager", done: false },
    { id: "access", label: "Revoke system access & accounts", owner: "IT", done: false },
    { id: "assets", label: "Collect company assets (laptop, badge)", owner: "IT", done: false },
    { id: "payroll", label: "Final pay & benefits settlement", owner: "Payroll", done: false },
    { id: "exit_interview", label: "Exit interview", owner: "HR", done: false },
    { id: "records", label: "Archive employee records", owner: "HR", done: false },
  ];
}

function allCasesRaw() {
  try { const raw = localStorage.getItem(CASE_KEY); if (!raw) return []; return JSON.parse(raw); }
  catch (e) { return []; }
}
function saveCases(list) { localStorage.setItem(CASE_KEY, JSON.stringify(list)); }

export function allCases() { return allCasesRaw(); }
export function getCaseForEmployee(empId) { return allCasesRaw().find((c) => c.empId === empId) || null; }
export function getCase(id) { return allCasesRaw().find((c) => c.id === id) || null; }

export function startOffboarding(empId, type, lastDay, reason, byId) {
  const list = allCasesRaw();
  const existing = list.find((c) => c.empId === empId && c.status !== "completed");
  if (existing) return existing;
  const emp = ppl.getEmployee(empId);
  const rec = {
    id: "off-" + Date.now(),
    empId, empName: emp ? ppl.fullName(emp) : empId,
    dept: emp ? emp.dept : "", title: emp ? emp.title : "",
    type: type || "resignation", lastDay: lastDay || "", reason: reason || "",
    initiatedBy: byId || "hr", initiatedAt: Date.now(),
    status: "in_progress", checklist: defaultChecklist(), completedAt: null,
  };
  list.push(rec);
  saveCases(list);
  return rec;
}
export function toggleTask(caseId, taskId) {
  const list = allCasesRaw();
  const i = list.findIndex((c) => c.id === caseId);
  if (i < 0) return;
  const c = { ...list[i], checklist: list[i].checklist.map((t) => t.id === taskId ? { ...t, done: !t.done } : t) };
  list[i] = c;
  saveCases(list);
}
export function updateCase(caseId, patch) {
  const list = allCasesRaw();
  const i = list.findIndex((c) => c.id === caseId);
  if (i < 0) return;
  list[i] = { ...list[i], ...patch };
  saveCases(list);
}
export function progressOf(c) {
  const total = c.checklist.length;
  const done = c.checklist.filter((t) => t.done).length;
  return { done, total, pct: total ? Math.round((done / total) * 100) : 0 };
}
// Complete the case → deactivate the employee + write exit history.
export function completeOffboarding(caseId, byId) {
  const list = allCasesRaw();
  const i = list.findIndex((c) => c.id === caseId);
  if (i < 0) return null;
  const c = list[i];
  const t = exitType(c.type);
  ppl.recordExit(c.empId, { type: c.type, typeLabel: t.label, reason: c.reason, lastDay: c.lastDay, by: byId || "HR" });
  list[i] = { ...c, status: "completed", completedAt: Date.now() };
  saveCases(list);
  return list[i];
}
export function cancelOffboarding(caseId) {
  saveCases(allCasesRaw().filter((c) => c.id !== caseId));
}

export function summary() {
  const cases = allCasesRaw();
  const active = cases.filter((c) => c.status === "in_progress");
  return { total: cases.length, active: active.length, completed: cases.length - active.length };
}

export function resetOffboarding() { localStorage.removeItem(CASE_KEY); }
