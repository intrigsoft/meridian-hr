// Employee / org-core data layer for the Meridian HR fixture.
// Stands in for the HRIS person, org, comp, and document tables a Thymeleaf
// app would own server-side. Everything persists to localStorage so edits,
// new hires, and status changes survive full reloads — same pattern as the
// leave / recruitment / performance stores.
//
// Model:
//   employees[]  — the person record: identity, employment, org (managerId),
//                  location, comp band, and attached documents.
//   Access is role-derived: HR edits everyone & sees all comp; a manager sees
//   comp for their own reports; an employee sees their own comp only.

import * as org from "./org-store.js";

const KEY = "meridian.people.v3";
const CHANGE_KEY = "meridian.people.changes.v1";

// NOTE: these two arrays are SEED-ONLY. The live, editable taxonomy is owned by
// org-store (Org Structure admin page). getDepartments/getLevels/getBands below
// are the single source of truth every form/dropdown reads. org-store seeds its
// defaults from these same values so nothing drifts on first load.
export const DEPARTMENTS = [
  { id: "Engineering",       color: "#3f7cc4", tint: "#e9f0f9" },
  { id: "Design",            color: "#9a6ab5", tint: "#f0ecf8" },
  { id: "Revenue",           color: "#4a9d7a", tint: "#e9f4ef" },
  { id: "Operations",        color: "#c68a2a", tint: "#faf3e6" },
  { id: "People Operations", color: "#b56b8f", tint: "#f7ecf1" },
];
// Live taxonomy — delegates to org-store so admin edits flow through everywhere.
export function deptMeta(id) { return org.deptMeta(id); }
export function getDepartments() { return org.getDepartments(); }
export function getLevels() { return org.getLevels(); }
export function getBands() { return org.getBands(); }

export const STATUS_META = {
  active:     { label: "Active",     pillBg: "#e6f3ec", pillFg: "#2f6f4f", dot: "#3ecf8e" },
  onboarding: { label: "Onboarding", pillBg: "#e8eefb", pillFg: "#3a5aa8", dot: "#4a86d8" },
  leave:      { label: "On leave",   pillBg: "#faf3e6", pillFg: "#9a6a1a", dot: "#e0a13a" },
  inactive:   { label: "Inactive",   pillBg: "#eef1f4", pillFg: "#6b7480", dot: "#b6bdc6" },
};

export const WORK_MODES = ["Remote", "Hybrid", "On-site"];
export const EMPLOYMENT_TYPES = ["Full-time", "Part-time", "Contract"];
export const LEVELS = ["Junior", "Mid", "Senior", "Staff", "Lead", "Manager", "Executive"];

// accessRole maps a record to the session role model (employee | manager | hr)
// so nav scope and edit rights stay consistent with session.js.
function seed() {
  return [
    { id: "alex.whitfield", first: "Alex", last: "Whitfield", initials: "AW", avatarBg: "#7a6bb5",
      title: "VP, Operations", dept: "Operations", level: "Executive", accessRole: "hr",
      managerId: null, email: "alex.whitfield@meridian.co", phone: "+1 415 555 0114",
      location: "San Francisco, CA", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2019-02-04", status: "active", salary: 265000, currency: "USD", band: "E1",
      documents: [ { name: "Employment agreement", type: "Contract", date: "2019-02-04" } ] },

    { id: "priya.nair", first: "Priya", last: "Nair", initials: "PN", avatarBg: "#4a9d7a",
      title: "HR Business Partner", dept: "People Operations", level: "Manager", accessRole: "hr",
      managerId: "alex.whitfield", email: "priya.nair@meridian.co", phone: "+1 415 555 0132",
      location: "San Francisco, CA", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2021-06-14", status: "active", salary: 158000, currency: "USD", band: "M3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2021-05-20" }, { name: "Employment agreement", type: "Contract", date: "2021-06-14" } ] },

    // Engineering
    { id: "david.okonkwo", first: "David", last: "Okonkwo", initials: "DO", avatarBg: "#c47f3f",
      title: "Engineering Manager", dept: "Engineering", level: "Manager", accessRole: "manager",
      managerId: "alex.whitfield", email: "david.okonkwo@meridian.co", phone: "+1 512 555 0188",
      location: "Austin, TX", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2020-09-01", status: "active", salary: 192000, currency: "USD", band: "M4",
      documents: [ { name: "Offer letter", type: "Offer", date: "2020-08-10" }, { name: "Employment agreement", type: "Contract", date: "2020-09-01" } ] },
    { id: "marcus.reid", first: "Marcus", last: "Reid", initials: "MR", avatarBg: "#6b7db5",
      title: "Senior Engineer", dept: "Engineering", level: "Senior", accessRole: "employee",
      managerId: "david.okonkwo", email: "marcus.reid@meridian.co", phone: "+1 512 555 0199",
      location: "Austin, TX", workMode: "Remote", employmentType: "Full-time",
      startDate: "2022-01-17", status: "active", salary: 148000, currency: "USD", band: "IC4",
      documents: [ { name: "Offer letter", type: "Offer", date: "2021-12-15" } ] },
    { id: "aisha.khan", first: "Aisha", last: "Khan", initials: "AK", avatarBg: "#4a9d9d",
      title: "Staff Engineer", dept: "Engineering", level: "Staff", accessRole: "employee",
      managerId: "david.okonkwo", email: "aisha.khan@meridian.co", phone: "+1 206 555 0143",
      location: "Seattle, WA", workMode: "Remote", employmentType: "Full-time",
      startDate: "2020-11-30", status: "active", salary: 176000, currency: "USD", band: "IC5",
      documents: [ { name: "Offer letter", type: "Offer", date: "2020-11-02" } ] },
    { id: "tom.bradley", first: "Tom", last: "Bradley", initials: "TB", avatarBg: "#6ba58f",
      title: "Engineer", dept: "Engineering", level: "Mid", accessRole: "employee",
      managerId: "david.okonkwo", email: "tom.bradley@meridian.co", phone: "+1 512 555 0177",
      location: "Austin, TX", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2023-03-06", status: "leave", salary: 122000, currency: "USD", band: "IC3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2023-02-10" } ] },
    { id: "james.okoro", first: "James", last: "Okoro", initials: "JO", avatarBg: "#7a6bb5",
      title: "Engineer", dept: "Engineering", level: "Mid", accessRole: "employee",
      managerId: "david.okonkwo", email: "james.okoro@meridian.co", phone: "+1 617 555 0121",
      location: "Boston, MA", workMode: "Remote", employmentType: "Full-time",
      startDate: "2025-08-22", status: "active", salary: 128000, currency: "USD", band: "IC3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2022-07-29" } ] },

    // Design
    { id: "nadia.rahman", first: "Nadia", last: "Rahman", initials: "NR", avatarBg: "#9a6ab5",
      title: "Design Manager", dept: "Design", level: "Manager", accessRole: "manager",
      managerId: "alex.whitfield", email: "nadia.rahman@meridian.co", phone: "+1 415 555 0166",
      location: "San Francisco, CA", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2021-02-08", status: "active", salary: 168000, currency: "USD", band: "M3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2021-01-18" } ] },
    { id: "sarah.chen", first: "Sarah", last: "Chen", initials: "SC", avatarBg: "#3f7cc4",
      title: "Product Designer", dept: "Design", level: "Mid", accessRole: "employee",
      managerId: "nadia.rahman", email: "sarah.chen@meridian.co", phone: "+1 415 555 0155",
      location: "San Francisco, CA", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2022-05-16", status: "active", salary: 132000, currency: "USD", band: "IC3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2022-04-25" } ] },
    { id: "ken.ito", first: "Ken", last: "Ito", initials: "KI", avatarBg: "#6ba58f",
      title: "Product Designer", dept: "Design", level: "Senior", accessRole: "employee",
      managerId: "nadia.rahman", email: "ken.ito@meridian.co", phone: "+1 415 555 0190",
      location: "San Francisco, CA", workMode: "On-site", employmentType: "Full-time",
      startDate: "2021-10-04", status: "active", salary: 141000, currency: "USD", band: "IC4",
      documents: [ { name: "Offer letter", type: "Offer", date: "2021-09-13" } ] },
    { id: "anna.kim", first: "Anna", last: "Kim", initials: "AN", avatarBg: "#b56b8f",
      title: "Designer", dept: "Design", level: "Junior", accessRole: "employee",
      managerId: "nadia.rahman", email: "anna.kim@meridian.co", phone: "+1 213 555 0138",
      location: "Los Angeles, CA", workMode: "Remote", employmentType: "Full-time",
      startDate: "2026-03-08", status: "leave", salary: 104000, currency: "USD", band: "IC2",
      documents: [ { name: "Offer letter", type: "Offer", date: "2023-12-11" } ] },

    // Revenue
    { id: "elena.vasquez", first: "Elena", last: "Vasquez", initials: "EV", avatarBg: "#b58f4a",
      title: "Revenue Manager", dept: "Revenue", level: "Manager", accessRole: "manager",
      managerId: "alex.whitfield", email: "elena.vasquez@meridian.co", phone: "+1 305 555 0102",
      location: "Miami, FL", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2020-04-20", status: "active", salary: 164000, currency: "USD", band: "M3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2020-03-30" } ] },
    { id: "sofia.alvarez", first: "Sofia", last: "Alvarez", initials: "SA", avatarBg: "#6ba58f",
      title: "Account Executive", dept: "Revenue", level: "Senior", accessRole: "employee",
      managerId: "elena.vasquez", email: "sofia.alvarez@meridian.co", phone: "+1 305 555 0119",
      location: "Miami, FL", workMode: "On-site", employmentType: "Full-time",
      startDate: "2025-11-14", status: "active", salary: 118000, currency: "USD", band: "IC3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2022-10-24" } ] },

    // Operations
    { id: "marco.rossi", first: "Marco", last: "Rossi", initials: "MO", avatarBg: "#6b8fb5",
      title: "Operations Manager", dept: "Operations", level: "Manager", accessRole: "manager",
      managerId: "alex.whitfield", email: "marco.rossi@meridian.co", phone: "+1 415 555 0175",
      location: "San Francisco, CA", workMode: "Hybrid", employmentType: "Full-time",
      startDate: "2021-07-26", status: "active", salary: 152000, currency: "USD", band: "M3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2021-07-05" } ] },
    { id: "julia.novak", first: "Julia", last: "Novak", initials: "JN", avatarBg: "#b56b8f",
      title: "Program Manager", dept: "Operations", level: "Senior", accessRole: "employee",
      managerId: "marco.rossi", email: "julia.novak@meridian.co", phone: "+1 415 555 0183",
      location: "Denver, CO", workMode: "Remote", employmentType: "Full-time",
      startDate: "2026-06-15", status: "onboarding", salary: 126000, currency: "USD", band: "IC3",
      documents: [ { name: "Offer letter", type: "Offer", date: "2023-05-15" } ] },
  ];
}

// ---- Extended profile (self-service) ----
// Profile fields live as top-level keys on the employee record. Rich seed data
// for a few people; everyone else starts sparse so the completeness meter has
// something to prompt. normalize() backfills defaults + seed on every read.
function emptyProfile() {
  return {
    legalName: "", preferredName: "", dob: "", gender: "", pronouns: "",
    personalEmail: "", personalPhone: "",
    address: { line1: "", city: "", state: "", zip: "" },
    emergencyContacts: [], dependents: [], skills: [], certifications: [], assets: [],
    taxIds: { ssnLast4: "", nationalId: "" },
    bank: { bankName: "", accountName: "", accountLast4: "", routingLast4: "" },
  };
}
const PROFILE_SEED = {
  "sarah.chen": {
    legalName: "Sarah Anne Chen", preferredName: "Sarah", dob: "1993-04-12", gender: "", pronouns: "she/her",
    personalEmail: "sarah.chen.sf@gmail.com", personalPhone: "+1 415 555 0155",
    address: { line1: "482 Valencia St, Apt 3", city: "San Francisco", state: "CA", zip: "94103" },
    emergencyContacts: [{ name: "Daniel Chen", relationship: "Spouse", phone: "+1 415 555 0182" }],
    dependents: [{ name: "Mia Chen", relationship: "Child", dob: "2020-08-03" }],
    skills: [{ name: "Product design", level: "Expert" }, { name: "Prototyping", level: "Advanced" }, { name: "User research", level: "Intermediate" }],
    certifications: [{ name: "NN/g UX Certification", issuer: "Nielsen Norman Group", date: "2021-06-01" }],
    assets: [{ name: "MacBook Pro 16\"", tag: "MRD-3391", assignedDate: "2022-05-16" }, { name: "Studio Display", tag: "MRD-3392", assignedDate: "2022-05-16" }],
    taxIds: { ssnLast4: "4821", nationalId: "" },
    bank: { bankName: "First Republic", accountName: "Sarah A Chen", accountLast4: "6642", routingLast4: "0114" },
  },
  "david.okonkwo": {
    legalName: "David Chukwuemeka Okonkwo", preferredName: "David", dob: "1987-11-02", gender: "Male", pronouns: "he/him",
    personalEmail: "d.okonkwo@gmail.com", personalPhone: "+1 512 555 0188",
    address: { line1: "1204 E 6th St", city: "Austin", state: "TX", zip: "78702" },
    emergencyContacts: [{ name: "Grace Okonkwo", relationship: "Spouse", phone: "+1 512 555 0190" }],
    dependents: [{ name: "Ada Okonkwo", relationship: "Child", dob: "2016-02-14" }, { name: "Emeka Okonkwo", relationship: "Child", dob: "2018-07-21" }],
    skills: [{ name: "Distributed systems", level: "Expert" }, { name: "People management", level: "Advanced" }],
    certifications: [{ name: "AWS Solutions Architect", issuer: "Amazon", date: "2020-03-01" }],
    assets: [{ name: "MacBook Pro 16\"", tag: "MRD-2210", assignedDate: "2020-09-01" }],
    taxIds: { ssnLast4: "7733", nationalId: "" },
    bank: { bankName: "Chase", accountName: "David C Okonkwo", accountLast4: "1180", routingLast4: "0021" },
  },
  "priya.nair": {
    legalName: "Priya Nair", preferredName: "Priya", dob: "1990-06-25", gender: "Female", pronouns: "she/her",
    personalEmail: "priya.nair.hr@gmail.com", personalPhone: "+1 415 555 0132",
    address: { line1: "77 Dolores St", city: "San Francisco", state: "CA", zip: "94110" },
    emergencyContacts: [{ name: "Arjun Nair", relationship: "Spouse", phone: "+1 415 555 0140" }],
    dependents: [], skills: [{ name: "Employee relations", level: "Expert" }, { name: "Comp & benefits", level: "Advanced" }],
    certifications: [{ name: "SHRM-SCP", issuer: "SHRM", date: "2019-09-01" }],
    assets: [{ name: "MacBook Air", tag: "MRD-2601", assignedDate: "2021-06-14" }],
    taxIds: { ssnLast4: "3092", nationalId: "" },
    bank: { bankName: "Wells Fargo", accountName: "Priya Nair", accountLast4: "5521", routingLast4: "0098" },
  },
  "marcus.reid": {
    preferredName: "Marcus", dob: "1991-01-18", pronouns: "he/him", personalPhone: "+1 512 555 0199",
    emergencyContacts: [{ name: "Tara Reid", relationship: "Partner", phone: "+1 512 555 0201" }],
    skills: [{ name: "Backend", level: "Advanced" }], assets: [{ name: "MacBook Pro 14\"", tag: "MRD-3050", assignedDate: "2022-01-17" }],
  },
  "nadia.rahman": {
    legalName: "Nadia Rahman", preferredName: "Nadia", dob: "1988-09-09", gender: "Female", pronouns: "she/her",
    personalPhone: "+1 415 555 0166", emergencyContacts: [{ name: "Sam Rahman", relationship: "Sibling", phone: "+1 415 555 0170" }],
    skills: [{ name: "Design leadership", level: "Expert" }], assets: [{ name: "MacBook Pro 16\"", tag: "MRD-2705", assignedDate: "2021-02-08" }],
  },
};
function normalize(e) {
  const base = emptyProfile();
  const s = PROFILE_SEED[e.id] || {};
  const joinEvent = { id: "ev-join-" + e.id, type: "join", label: "Joined Meridian", detail: e.title + " · " + e.dept, date: e.startDate, at: new Date(e.startDate + "T09:00:00").getTime(), by: "System" };
  return {
    ...base, ...s, ...e,
    address: { ...base.address, ...(s.address || {}), ...(e.address || {}) },
    taxIds: { ...base.taxIds, ...(s.taxIds || {}), ...(e.taxIds || {}) },
    bank: { ...base.bank, ...(s.bank || {}), ...(e.bank || {}) },
    emergencyContacts: e.emergencyContacts || s.emergencyContacts || [],
    dependents: e.dependents || s.dependents || [],
    skills: e.skills || s.skills || [],
    certifications: e.certifications || s.certifications || [],
    assets: e.assets || s.assets || [],
    history: e.history || (HISTORY_SEED[e.id] ? [joinEvent, ...HISTORY_SEED[e.id]] : [joinEvent]),
    exit: e.exit || null,
  };
}

// Seed a couple of internal moves so the job history isn't just "Joined".
const HISTORY_SEED = {
  "david.okonkwo": [
    { id: "ev-d1", type: "promotion", label: "Promoted to Engineering Manager", detail: "Senior Engineer → Engineering Manager", date: "2022-04-01", at: new Date("2022-04-01T09:00:00").getTime(), by: "Alex Whitfield" },
  ],
  "sarah.chen": [
    { id: "ev-s1", type: "comp", label: "Compensation review", detail: "$124,000 → $132,000", date: "2024-01-15", at: new Date("2024-01-15T09:00:00").getTime(), by: "Nadia Rahman" },
  ],
};

export function allEmployees() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) { const s = seed(); localStorage.setItem(KEY, JSON.stringify(s)); return s.map(normalize); }
    return JSON.parse(raw).map(normalize);
  } catch (e) { return seed().map(normalize); }
}
function saveAll(list) { localStorage.setItem(KEY, JSON.stringify(list)); }

export function getEmployee(id) {
  return allEmployees().find((e) => e.id === id) || null;
}
export function fullName(e) { return e ? (e.first + " " + e.last) : "—"; }

// Fields whose changes are recorded in the employee's job history.
const TRACKED = [
  { key: "title", label: "Title changed", type: "job" },
  { key: "level", label: "Level changed", type: "promotion" },
  { key: "dept", label: "Department changed", type: "transfer" },
  { key: "managerId", label: "Manager changed", type: "transfer" },
  { key: "salary", label: "Compensation changed", type: "comp" },
  { key: "employmentType", label: "Employment type changed", type: "job" },
  { key: "status", label: "Status changed", type: "status" },
  { key: "accessRole", label: "Access level changed", type: "access" },
];
function makeEvent(type, label, detail, by) {
  const now = new Date();
  return { id: "ev-" + now.getTime() + "-" + Math.floor(Math.random() * 1000), type, label, detail,
    date: now.toISOString().slice(0, 10), at: now.getTime(), by: by || "HR" };
}
export function getHistory(id) {
  const e = getEmployee(id);
  return e ? (e.history || []).slice().sort((a, b) => b.at - a.at) : [];
}

export function updateEmployee(id, patch) {
  const list = allEmployees();
  const i = list.findIndex((e) => e.id === id);
  if (i < 0) return null;
  const before = list[i];
  const ACCESS_LABELS = { employee: "Employee", manager: "Manager", hr: "HR executive" };
  const fmtV = (k, v) => k === "managerId" ? (v ? fullName(getEmployee(v)) : "—") : (k === "salary" ? formatSalary(v, "USD") : (k === "accessRole" ? (ACCESS_LABELS[v] || v) : (v || "—")));
  const events = [];
  TRACKED.forEach((t) => {
    if (patch[t.key] !== undefined && String(patch[t.key]) !== String(before[t.key])) {
      events.push(makeEvent(t.type, t.label, fmtV(t.key, before[t.key]) + " → " + fmtV(t.key, patch[t.key]), patch._by));
    }
  });
  const clean = { ...patch }; delete clean._by;
  list[i] = { ...before, ...clean, history: events.length ? [...(before.history || []), ...events] : before.history };
  saveAll(list);
  return list[i];
}
export function setStatus(id, status) { return updateEmployee(id, { status }); }

const BG = ["#4a9d7a", "#c47f3f", "#6b7db5", "#4a9d9d", "#9a6ab5", "#3f7cc4", "#b58f4a", "#6ba58f", "#b56b8f", "#6b8fb5", "#7a6bb5"];
export function addEmployee(p) {
  const list = allEmployees();
  const first = (p.first || "New").trim();
  const last = (p.last || "Hire").trim();
  let base = (first + "." + last).toLowerCase().replace(/[^a-z.]/g, "");
  let id = base, n = 2;
  while (list.some((e) => e.id === id)) { id = base + n; n++; }
  const rec = {
    id, first, last,
    initials: (first[0] || "N").toUpperCase() + (last[0] || "H").toUpperCase(),
    avatarBg: BG[list.length % BG.length],
    title: p.title || "New role", dept: p.dept || org.getDepartments()[0].id, level: p.level || "Mid",
    accessRole: p.accessRole || "employee",
    managerId: p.managerId || null,
    email: p.email || (base + "@meridian.co"),
    phone: p.phone || "",
    location: p.location || "Remote", workMode: p.workMode || "Remote",
    employmentType: p.employmentType || "Full-time",
    startDate: p.startDate || new Date().toISOString().slice(0, 10),
    status: p.status || "onboarding",
    salary: p.salary != null ? Number(p.salary) : null, currency: "USD", band: p.band || "—",
    documents: [],
  };
  list.push(rec);
  saveAll(list);
  return rec;
}

// ---- Org relationships ----
export function managerOf(id) { const e = getEmployee(id); return e && e.managerId ? getEmployee(e.managerId) : null; }
export function directReports(id) { return allEmployees().filter((e) => e.managerId === id); }
export function reportCount(id) {
  // total downstream (recursive) headcount
  let total = 0;
  const walk = (mid) => directReports(mid).forEach((r) => { total++; walk(r.id); });
  walk(id);
  return total;
}
export function orgRoots() { const all = allEmployees(); return all.filter((e) => !e.managerId || !all.some((x) => x.id === e.managerId)); }
export function chainOfCommand(id) {
  const chain = []; let cur = getEmployee(id);
  const seen = {};
  while (cur && cur.managerId && !seen[cur.managerId]) { seen[cur.managerId] = 1; cur = getEmployee(cur.managerId); if (cur) chain.push(cur); }
  return chain.reverse();
}

// ---- Access control ----
export function canEditAll(user) { return !!user && user.role === "hr"; }
export function canViewComp(user, empId) {
  if (!user) return false;
  if (user.role === "hr") return true;
  if (user.id === empId) return true;
  if (user.role === "manager") {
    // manager can see comp for anyone in their downstream org
    let ok = false;
    const walk = (mid) => directReports(mid).forEach((r) => { if (r.id === empId) ok = true; walk(r.id); });
    walk(user.id);
    return ok;
  }
  return false;
}

// ---- Aggregate stats for the directory header ----
export function stats() {
  const all = allEmployees();
  const byDept = org.getDepartments().map((d) => ({ ...d, count: all.filter((e) => e.dept === d.id).length }));
  const active = all.filter((e) => e.status === "active").length;
  const onboarding = all.filter((e) => e.status === "onboarding").length;
  const onLeave = all.filter((e) => e.status === "leave").length;
  // average tenure in years
  const now = Date.now();
  const yrs = all.map((e) => (now - new Date(e.startDate + "T00:00:00").getTime()) / (365.25 * 864e5));
  const avgTenure = yrs.length ? (yrs.reduce((a, b) => a + b, 0) / yrs.length) : 0;
  const byType = EMPLOYMENT_TYPES.map((t) => ({ type: t, count: all.filter((e) => e.employmentType === t).length }));
  const byMode = WORK_MODES.map((m) => ({ mode: m, count: all.filter((e) => e.workMode === m).length }));
  return { headcount: all.length, active, onboarding, onLeave, byDept, avgTenure, byType, byMode };
}

export function tenureLabel(startDate) {
  const ms = Date.now() - new Date(startDate + "T00:00:00").getTime();
  const yrs = ms / (365.25 * 864e5);
  if (yrs < 1) { const mo = Math.max(1, Math.round(yrs * 12)); return mo + " mo"; }
  return (Math.round(yrs * 10) / 10) + " yr";
}
export function formatDate(iso) {
  if (!iso) return "—";
  return new Date(iso + "T00:00:00").toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}
export function formatSalary(n, currency) {
  if (n == null) return "—";
  return (currency === "USD" ? "$" : "") + Number(n).toLocaleString("en-US");
}

// ---- Nested path get/set (for profile edits like "bank.accountLast4") ----
export function getByPath(obj, path) {
  return path.split(".").reduce((o, k) => (o == null ? undefined : o[k]), obj);
}
function setByPath(obj, path, value) {
  const keys = path.split(".");
  const last = keys.pop();
  let o = obj;
  keys.forEach((k) => { if (o[k] == null || typeof o[k] !== "object") o[k] = {}; o = o[k]; });
  o[last] = value;
  return obj;
}
// Apply a direct (non-sensitive) profile edit.
export function setProfileField(empId, path, value) {
  const list = allEmployees();
  const i = list.findIndex((e) => e.id === empId);
  if (i < 0) return null;
  const rec = { ...list[i] };
  setByPath(rec, path, value);
  list[i] = rec;
  saveAll(list);
  return rec;
}

// ---- Profile completeness ----
// Fields that count toward a "complete" profile, with friendly labels.
export const COMPLETENESS_FIELDS = [
  { path: "legalName", label: "Legal name" },
  { path: "dob", label: "Date of birth" },
  { path: "gender", label: "Gender" },
  { path: "pronouns", label: "Pronouns" },
  { path: "personalEmail", label: "Personal email" },
  { path: "personalPhone", label: "Personal phone" },
  { path: "address.line1", label: "Home address" },
  { path: "emergencyContacts", label: "Emergency contact" },
  { path: "taxIds.ssnLast4", label: "Tax ID" },
  { path: "bank.accountLast4", label: "Bank details" },
];
export function completeness(emp) {
  const filled = (v) => Array.isArray(v) ? v.length > 0 : !!(v && String(v).trim());
  const missing = COMPLETENESS_FIELDS.filter((f) => !filled(getByPath(emp, f.path)));
  const done = COMPLETENESS_FIELDS.length - missing.length;
  return { pct: Math.round((done / COMPLETENESS_FIELDS.length) * 100), done, total: COMPLETENESS_FIELDS.length, missing };
}

// ---- Sensitive-field change requests (need HR approval) ----
// Sensitive paths route through an approval queue instead of applying directly.
export const SENSITIVE_PATHS = ["legalName", "bank.bankName", "bank.accountName", "bank.accountLast4", "bank.routingLast4", "taxIds.ssnLast4", "taxIds.nationalId"];
export function isSensitivePath(path) { return SENSITIVE_PATHS.indexOf(path) >= 0; }

function allChanges() { try { return JSON.parse(localStorage.getItem(CHANGE_KEY)) || []; } catch (e) { return []; } }
function saveChanges(list) { localStorage.setItem(CHANGE_KEY, JSON.stringify(list)); }

export function requestProfileChange(empId, path, label, oldValue, newValue, byId) {
  const list = allChanges();
  const rec = { id: "chg-" + Date.now() + "-" + Math.floor(Math.random() * 1000), empId, path, label,
    oldValue: oldValue == null ? "" : String(oldValue), newValue: newValue == null ? "" : String(newValue),
    requestedBy: byId, requestedAt: Date.now(), status: "pending" };
  list.push(rec);
  saveChanges(list);
  return rec;
}
export function pendingChangesFor(empId) { return allChanges().filter((c) => c.status === "pending" && (!empId || c.empId === empId)); }
export function allPendingChanges() { return allChanges().filter((c) => c.status === "pending"); }
export function hasPendingChange(empId, path) { return allChanges().some((c) => c.status === "pending" && c.empId === empId && c.path === path); }
export function approveProfileChange(id, byId) {
  const list = allChanges();
  const i = list.findIndex((c) => c.id === id);
  if (i < 0) return;
  const c = list[i];
  setProfileField(c.empId, c.path, c.newValue);
  list[i] = { ...c, status: "approved", decidedBy: byId, decidedAt: Date.now() };
  saveChanges(list);
}
export function rejectProfileChange(id, byId) {
  const list = allChanges();
  const i = list.findIndex((c) => c.id === id);
  if (i < 0) return;
  list[i] = { ...list[i], status: "rejected", decidedBy: byId, decidedAt: Date.now() };
  saveChanges(list);
}

// ---- Lifecycle: exit & onboarding conversion ----
// Mark an employee inactive with exit details + a single history event.
export function recordExit(id, exit) {
  const list = allEmployees();
  const i = list.findIndex((e) => e.id === id);
  if (i < 0) return null;
  const ev = makeEvent("exit", "Offboarded", (exit.typeLabel || "Exit") + " · last day " + formatDate(exit.lastDay), exit.by);
  list[i] = { ...list[i], status: "inactive", exit: { ...exit, completedAt: Date.now() }, history: [...(list[i].history || []), ev] };
  saveAll(list);
  return list[i];
}
// Create a Directory employee record from a completed onboarding case.
export function createFromOnboarding(kase) {
  const parts = (kase.hireName || "New Hire").trim().split(/\s+/);
  return addEmployee({
    first: parts[0] || "New", last: parts.slice(1).join(" ") || "Hire",
    title: kase.roleLabel || "New role", dept: kase.dept || DEPARTMENTS[0].id,
    email: kase.email, startDate: kase.startDate, status: "active",
  });
}
// Convert an onboarding case into a Directory record once, tracked by case id.
const OB_CONVERTED_KEY = "meridian.people.obconverted.v1";
function convertedSet() { try { return JSON.parse(localStorage.getItem(OB_CONVERTED_KEY)) || {}; } catch (e) { return {}; } }
export function isOnboardingConverted(caseId) { return !!convertedSet()[caseId]; }
export function onboardingConvertedId(caseId) { return convertedSet()[caseId] || null; }
export function convertOnboardingCase(kase) {
  const set = convertedSet();
  if (set[kase.id]) return getEmployee(set[kase.id]);
  const rec = createFromOnboarding(kase);
  set[kase.id] = rec.id;
  localStorage.setItem(OB_CONVERTED_KEY, JSON.stringify(set));
  return rec;
}

export function resetSeed() { localStorage.removeItem(KEY); localStorage.removeItem(CHANGE_KEY); localStorage.removeItem(OB_CONVERTED_KEY); }
