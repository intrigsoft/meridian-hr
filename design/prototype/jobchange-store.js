// Job-change (promotion / transfer / comp) request data layer.
// A manager or HR raises a change request against an employee with an EFFECTIVE
// DATE. HR approves; on approval the change either applies immediately (effective
// date today/past) or is SCHEDULED and applied automatically once the date
// arrives (applyDueChanges runs on page load). Applying routes through
// people-store.updateEmployee so it lands in the employee's job history.
// Persists to localStorage.

import * as ppl from "./people-store.js";

const KEY = "meridian.jobchanges.v1";

// Change-type catalog. localStorage-backed so HR can edit which fields each
// type touches (Settings → Job-change types); seeds from this default.
const CT_KEY = "meridian.jobchanges.types.v1";
export const CHANGE_TYPES = [
  { id: "promotion", label: "Promotion", color: "#7a5aa8", bg: "#f0ecf8", fields: ["title", "level", "salary", "band"] },
  { id: "transfer", label: "Transfer", color: "#3a5aa8", bg: "#e8eefb", fields: ["dept", "managerId", "title", "workMode", "location"] },
  { id: "comp", label: "Compensation", color: "#9a6a1a", bg: "#f7f1e0", fields: ["salary", "band"] },
  { id: "reclass", label: "Reclassification", color: "#2f6f4f", bg: "#e6f3ec", fields: ["employmentType", "title", "level"] },
];
export const ALL_CHANGE_FIELDS = ["title", "level", "dept", "managerId", "salary", "band", "workMode", "location", "employmentType"];
export function getChangeTypes() {
  try {
    const raw = localStorage.getItem(CT_KEY);
    if (!raw) { localStorage.setItem(CT_KEY, JSON.stringify(CHANGE_TYPES)); return CHANGE_TYPES.map((t) => ({ ...t })); }
    return JSON.parse(raw);
  } catch (e) { return CHANGE_TYPES.map((t) => ({ ...t })); }
}
function saveChangeTypes(list) { localStorage.setItem(CT_KEY, JSON.stringify(list)); return list; }
export function changeType(id) { const list = getChangeTypes(); return list.find((t) => t.id === id) || list[0]; }
export function setTypeFields(id, fields) {
  const list = getChangeTypes(); const i = list.findIndex((t) => t.id === id);
  if (i >= 0) list[i] = { ...list[i], fields }; return saveChangeTypes(list);
}
export function updateChangeType(id, patch) {
  const list = getChangeTypes(); const i = list.findIndex((t) => t.id === id);
  if (i >= 0) list[i] = { ...list[i], ...patch }; return saveChangeTypes(list);
}

// Human labels for the fields a request can change.
export const FIELD_LABELS = {
  title: "Job title", level: "Level", dept: "Department", managerId: "Manager",
  salary: "Base salary", band: "Band", workMode: "Work mode", location: "Location",
  employmentType: "Employment type",
};

export const STATUS_META = {
  pending:   { label: "Pending approval", pillBg: "#faf3e6", pillFg: "#9a6a1a" },
  scheduled: { label: "Scheduled",        pillBg: "#e8eefb", pillFg: "#3a5aa8" },
  applied:   { label: "Applied",          pillBg: "#e6f3ec", pillFg: "#2f6f4f" },
  rejected:  { label: "Rejected",         pillBg: "#fbe9e7", pillFg: "#b23b2e" },
};

function allRaw() { try { return JSON.parse(localStorage.getItem(KEY)) || []; } catch (e) { return []; } }
function save(list) { localStorage.setItem(KEY, JSON.stringify(list)); }

function todayISO() { return new Date().toISOString().slice(0, 10); }

export function allRequests() { return allRaw().slice().sort((a, b) => b.createdAt - a.createdAt); }
export function getRequest(id) { return allRaw().find((r) => r.id === id) || null; }
export function requestsFor(empId) { return allRequests().filter((r) => r.empId === empId); }

// changes: { field: newValue } — only fields that differ from current.
export function createRequest(empId, type, effectiveDate, changes, reason, byId) {
  const emp = ppl.getEmployee(empId);
  const list = allRaw();
  const rec = {
    id: "jc-" + Date.now() + "-" + Math.floor(Math.random() * 1000),
    empId, empName: emp ? ppl.fullName(emp) : empId,
    type, effectiveDate: effectiveDate || todayISO(),
    changes: changes || {}, reason: reason || "",
    requestedBy: byId, requestedByName: (ppl.getEmployee(byId) ? ppl.fullName(ppl.getEmployee(byId)) : "HR"),
    status: "pending", createdAt: Date.now(), decidedBy: null, decidedAt: null, appliedAt: null,
  };
  list.push(rec);
  save(list);
  return rec;
}

function applyChange(rec) {
  const patch = { ...rec.changes, _by: rec.decidedByName || "HR" };
  ppl.updateEmployee(rec.empId, patch);
}

export function approveRequest(id, byId) {
  const list = allRaw();
  const i = list.findIndex((r) => r.id === id);
  if (i < 0) return null;
  const rec = list[i];
  rec.decidedBy = byId;
  rec.decidedByName = ppl.getEmployee(byId) ? ppl.fullName(ppl.getEmployee(byId)) : "HR";
  rec.decidedAt = Date.now();
  if (rec.effectiveDate <= todayISO()) {
    applyChange(rec);
    rec.status = "applied";
    rec.appliedAt = Date.now();
  } else {
    rec.status = "scheduled";
  }
  list[i] = rec;
  save(list);
  return rec;
}
export function rejectRequest(id, byId) {
  const list = allRaw();
  const i = list.findIndex((r) => r.id === id);
  if (i < 0) return;
  list[i] = { ...list[i], status: "rejected", decidedBy: byId, decidedAt: Date.now() };
  save(list);
}
export function cancelRequest(id) { save(allRaw().filter((r) => r.id !== id)); }

// Apply any scheduled changes whose effective date has arrived. Call on load.
export function applyDueChanges() {
  const list = allRaw();
  const today = todayISO();
  let changed = false;
  list.forEach((rec) => {
    if (rec.status === "scheduled" && rec.effectiveDate <= today) {
      applyChange(rec);
      rec.status = "applied";
      rec.appliedAt = Date.now();
      changed = true;
    }
  });
  if (changed) save(list);
  return changed;
}

export function summary() {
  const list = allRaw();
  return {
    pending: list.filter((r) => r.status === "pending").length,
    scheduled: list.filter((r) => r.status === "scheduled").length,
    applied: list.filter((r) => r.status === "applied").length,
    total: list.length,
  };
}

// Present a request's changes as [{label, from, to}] rows for display.
export function diffRows(rec) {
  const emp = ppl.getEmployee(rec.empId) || {};
  return Object.keys(rec.changes).map((f) => {
    const to = rec.changes[f];
    let from = emp[f];
    let toDisp = to, fromDisp = from;
    if (f === "managerId") { toDisp = to ? ppl.fullName(ppl.getEmployee(to)) : "—"; fromDisp = from ? ppl.fullName(ppl.getEmployee(from)) : "—"; }
    else if (f === "salary") { toDisp = ppl.formatSalary(to, "USD"); fromDisp = ppl.formatSalary(from, "USD"); }
    return { label: FIELD_LABELS[f] || f, from: fromDisp || "—", to: toDisp || "—" };
  });
}

export function resetJobChanges() { localStorage.removeItem(KEY); }
