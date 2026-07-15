// Time & attendance data layer for the Meridian HR fixture.
// Stands in for the timesheet / clock / holiday tables an HRIS owns server-side.
// Persists to localStorage; mirrors the people / leave / recruitment stores.
//
// Model:
//   timesheets  — one record per (employee, ISO week starting Monday):
//                 hours logged per weekday + submission status.
//   clock       — the live "clocked-in since" stamp per employee.
//   HOLIDAYS    — company-wide paid holidays (auto-blocked on the timesheet).
// Leave days are pulled from leave-store so an approved absence shows on the grid.

const TS_KEY = "meridian.time.sheets.v1";
const CLK_KEY = "meridian.time.clock.v1";

import * as config from "./config-store.js";

export const DAYS = [
  { id: "mon", label: "Mon" }, { id: "tue", label: "Tue" }, { id: "wed", label: "Wed" },
  { id: "thu", label: "Thu" }, { id: "fri", label: "Fri" }, { id: "sat", label: "Sat" }, { id: "sun", label: "Sun" },
];
// Weekly target hours — read from the central config (HR-configurable in Settings).
export function targetHours() { return config.getWorkweek().targetHours; }
export const TARGET_HOURS = 40; // legacy fallback

export const STATUS_META = {
  open:      { label: "Open",      pillBg: "#eef1f4", pillFg: "#6b7480" },
  submitted: { label: "Submitted", pillBg: "#faf3e6", pillFg: "#9a6a1a" },
  approved:  { label: "Approved",  pillBg: "#e6f3ec", pillFg: "#2f6f4f" },
};

// Company holidays come from the central config (HR-configurable in Settings).
export function holidays() {
  return config.getHolidays().map((h) => ({ date: h.date, name: h.name }));
}

// ---- Date helpers (Monday-based weeks) ----
export function isoDate(d) {
  const y = d.getFullYear(), m = String(d.getMonth() + 1).padStart(2, "0"), day = String(d.getDate()).padStart(2, "0");
  return y + "-" + m + "-" + day;
}
export function parseISO(s) { return new Date(s + "T00:00:00"); }
export function weekStartOf(dateOrIso) {
  const d = typeof dateOrIso === "string" ? parseISO(dateOrIso) : new Date(dateOrIso);
  const day = (d.getDay() + 6) % 7; // 0 = Monday
  d.setDate(d.getDate() - day);
  d.setHours(0, 0, 0, 0);
  return isoDate(d);
}
export function weekDates(weekStart) {
  const start = parseISO(weekStart);
  return DAYS.map((_, i) => { const d = new Date(start); d.setDate(start.getDate() + i); return isoDate(d); });
}
export function weekLabel(weekStart) {
  const dates = weekDates(weekStart);
  const a = parseISO(dates[0]), b = parseISO(dates[6]);
  const fmt = (d, withYear) => d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: withYear ? "numeric" : undefined });
  return fmt(a, false) + " – " + fmt(b, true);
}
export function holidayOn(iso) { return holidays().find((h) => h.date === iso) || null; }
export function upcomingHolidays(fromIso, n) {
  const from = fromIso || isoDate(new Date());
  return holidays().filter((h) => h.date >= from).slice(0, n || 5);
}

// ---- Timesheet persistence ----
function allSheets() {
  try { const raw = localStorage.getItem(TS_KEY); if (!raw) { const s = seed(); localStorage.setItem(TS_KEY, JSON.stringify(s)); return s; } return JSON.parse(raw); }
  catch (e) { return seed(); }
}
function saveSheets(list) { localStorage.setItem(TS_KEY, JSON.stringify(list)); }
function keyOf(empId, weekStart) { return empId + "|" + weekStart; }

function blankDays() { return { mon: 0, tue: 0, wed: 0, thu: 0, fri: 0, sat: 0, sun: 0 }; }

function seed() {
  // Seed a couple of prior weeks for Sarah Chen so history isn't empty.
  const today = new Date();
  const thisWk = weekStartOf(today);
  const prev = (n) => { const d = parseISO(thisWk); d.setDate(d.getDate() - 7 * n); return isoDate(d); };
  return [
    { empId: "sarah.chen", weekStart: prev(1), days: { mon: 8, tue: 8, wed: 7.5, thu: 8, fri: 8, sat: 0, sun: 0 }, status: "approved", submittedAt: Date.now() - 6 * 864e5, approvedBy: "nadia.rahman" },
    { empId: "sarah.chen", weekStart: prev(2), days: { mon: 8, tue: 8, wed: 8, thu: 8, fri: 6, sat: 0, sun: 0 }, status: "approved", submittedAt: Date.now() - 13 * 864e5, approvedBy: "nadia.rahman" },
    { empId: "sarah.chen", weekStart: prev(3), days: { mon: 8, tue: 8, wed: 8, thu: 8, fri: 8, sat: 0, sun: 0 }, status: "approved", submittedAt: Date.now() - 20 * 864e5, approvedBy: "nadia.rahman" },
    // A teammate's submitted-but-unapproved week, so a manager has something to action.
    { empId: "marcus.reid", weekStart: prev(1), days: { mon: 8, tue: 8, wed: 8, thu: 8, fri: 9, sat: 0, sun: 0 }, status: "submitted", submittedAt: Date.now() - 2 * 864e5 },
    { empId: "ken.ito", weekStart: prev(1), days: { mon: 8, tue: 7, wed: 8, thu: 8, fri: 8, sat: 0, sun: 0 }, status: "submitted", submittedAt: Date.now() - 1 * 864e5 },
  ];
}

export function getWeek(empId, weekStart) {
  const found = allSheets().find((s) => s.empId === empId && s.weekStart === weekStart);
  return found || { empId, weekStart, days: blankDays(), status: "open" };
}
export function weeksFor(empId) {
  return allSheets().filter((s) => s.empId === empId).sort((a, b) => (a.weekStart < b.weekStart ? 1 : -1));
}
export function totalHours(days) { return DAYS.reduce((s, d) => s + (Number(days[d.id]) || 0), 0); }

export function setDayHours(empId, weekStart, day, hours) {
  const list = allSheets();
  let i = list.findIndex((s) => s.empId === empId && s.weekStart === weekStart);
  const h = Math.max(0, Math.min(24, Number(hours) || 0));
  if (i < 0) { const rec = { empId, weekStart, days: blankDays(), status: "open" }; rec.days[day] = h; list.push(rec); }
  else { if (list[i].status === "approved") return list[i]; list[i] = { ...list[i], status: list[i].status === "submitted" ? "submitted" : "open", days: { ...list[i].days, [day]: h } }; }
  saveSheets(list);
  return getWeek(empId, weekStart);
}
export function submitWeek(empId, weekStart) {
  const list = allSheets();
  let i = list.findIndex((s) => s.empId === empId && s.weekStart === weekStart);
  if (i < 0) { list.push({ empId, weekStart, days: blankDays(), status: "submitted", submittedAt: Date.now() }); }
  else { list[i] = { ...list[i], status: "submitted", submittedAt: Date.now() }; }
  saveSheets(list);
  return getWeek(empId, weekStart);
}
export function approveWeek(empId, weekStart, byId) {
  const list = allSheets();
  const i = list.findIndex((s) => s.empId === empId && s.weekStart === weekStart);
  if (i < 0) return;
  list[i] = { ...list[i], status: "approved", approvedBy: byId, approvedAt: Date.now() };
  saveSheets(list);
}
export function pendingApprovals() { return allSheets().filter((s) => s.status === "submitted"); }

// ---- Live clock ----
function clockMap() { try { return JSON.parse(localStorage.getItem(CLK_KEY)) || {}; } catch (e) { return {}; } }
export function getClock(empId) { return clockMap()[empId] || null; }
export function clockIn(empId) { const m = clockMap(); m[empId] = { since: Date.now() }; localStorage.setItem(CLK_KEY, JSON.stringify(m)); return m[empId]; }
// Clock out returns elapsed hours (rounded to 0.25) and clears the stamp.
export function clockOut(empId) {
  const m = clockMap(); const c = m[empId]; if (!c) return 0;
  const hrs = Math.round(((Date.now() - c.since) / 3600000) * 4) / 4;
  delete m[empId]; localStorage.setItem(CLK_KEY, JSON.stringify(m));
  return hrs;
}

export function resetTime() { localStorage.removeItem(TS_KEY); localStorage.removeItem(CLK_KEY); }
