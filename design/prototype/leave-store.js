// Persistent Leave data layer for the Meridian HR fixture.
// Stands in for the server-side leave tables a Thymeleaf app would own.
// Everything lives in localStorage so the loop survives full page reloads.
//
// v2 adds a production-shaped lifecycle: every request carries an immutable
// event timeline, decisions capture a note, employees can withdraw, approvers
// can undo, and per-user notifications are derived from the events.

const KEY = "meridian.leave.v2";
const READ_KEY = "meridian.leave.read.v1";

// ---- Display metadata (shared by all Leave pages) ----
export const TYPE_META = {
  annual:      { label: "Annual leave",     short: "Annual",      dot: "#3f7cc4", tag: "AL", tint: "#e9f0f9", color: "#2f6aa8" },
  sick:        { label: "Sick leave",       short: "Sick",        dot: "#5aa17f", tag: "SL", tint: "#ecf5f0", color: "#3d8564" },
  personal:    { label: "Personal leave",   short: "Personal",    dot: "#c99b4e", tag: "PL", tint: "#faf3e6", color: "#9a6a1a" },
  parental:    { label: "Parental leave",   short: "Parental",    dot: "#9b7fc4", tag: "PA", tint: "#f0ecf8", color: "#6a4fa8" },
  unpaid:      { label: "Unpaid leave",     short: "Unpaid",      dot: "#8894a3", tag: "UP", tint: "#eef1f4", color: "#5a6472" },
  bereavement: { label: "Bereavement",      short: "Bereavement", dot: "#b56b8f", tag: "BV", tint: "#f7ecf1", color: "#8f4f6f" },
};

export const STATUS_META = {
  pending:   { label: "Pending",        pillBg: "#fbf1de", pillFg: "#9a6a1a", cat: "pending" },
  escalated: { label: "Escalated",      pillBg: "#e8eefb", pillFg: "#3a5aa8", cat: "pending" },
  info:      { label: "Info requested", pillBg: "#eef3fb", pillFg: "#3a5aa8", cat: "pending" },
  approved:  { label: "Approved",       pillBg: "#e6f3ec", pillFg: "#2f6f4f", cat: "approved" },
  posted:    { label: "Posted",         pillBg: "#eef1f4", pillFg: "#6b7480", cat: "approved" },
  rejected:  { label: "Rejected",       pillBg: "#fbe9e7", pillFg: "#b23b2e", cat: "rejected" },
  cancelled: { label: "Withdrawn",      pillBg: "#eef1f4", pillFg: "#6b7480", cat: "cancelled" },
};

export const APPROVERS = {
  "david.okonkwo": { name: "D. Okonkwo",     init: "DO", bg: "#c47f3f" },
  "priya.nair":    { name: "P. Nair (HR)",   init: "PN", bg: "#4a9d7a" },
};

// ---- Event / timeline metadata ----
// iconPath is a stroke-only 24x24 SVG path; the drawer renders it generically.
export const EVENT_META = {
  submitted:      { label: "Submitted",        color: "#2f6aa8", iconPath: "M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" },
  routed:         { label: "Routed to approver", color: "#8894a3", iconPath: "M5 12h14M13 6l6 6-6 6" },
  escalated:      { label: "Escalated to HR",   color: "#c68a2a", iconPath: "M12 2l3 7h7l-5.5 4.5L18 21l-6-4-6 4 1.5-7.5L2 9h7z" },
  info_requested: { label: "Info requested",    color: "#3a5aa8", iconPath: "M12 16v-4M12 8h.01M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z" },
  approved:       { label: "Approved",          color: "#2f6f4f", iconPath: "M20 6L9 17l-5-5" },
  rejected:       { label: "Rejected",          color: "#b23b2e", iconPath: "M18 6L6 18M6 6l12 12" },
  cancelled:      { label: "Withdrawn",         color: "#6b7480", iconPath: "M18 6L6 18M6 6l12 12" },
  posted:         { label: "Posted to calendar", color: "#6b7480", iconPath: "M3 4h18v16H3zM3 10h18M8 2v4M16 2v4" },
};

function fmt(iso) {
  const d = new Date(iso + "T00:00:00");
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}
export function formatRange(s, e) {
  return s === e ? fmt(s) : fmt(s) + " – " + fmt(e);
}

// Timestamp helpers for the event log.
function stampNow() {
  const d = new Date();
  return { at: d.getTime(), atLabel: labelFor(d) };
}
function stampAt(dateStr, hour) {
  const d = new Date(dateStr + "T" + String(hour || 9).padStart(2, "0") + ":00:00");
  return { at: d.getTime(), atLabel: labelFor(d) };
}
function labelFor(d) {
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" }) + " · " +
         d.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
}
function ev(kind, actor, actorName, note, stamp) {
  return { kind, actor, actorName, note: note || "", at: stamp.at, atLabel: stamp.atLabel };
}

// ---- Seed data (relationships drive the whole demo) ----
function seedData() {
  return [
    // ===== Sarah Chen's own history (her My Leave) =====
    { id: "R-1042", empId: "sarah.chen", empName: "Sarah Chen", empInitials: "SC", empBg: "#3f7cc4", empRole: "Product Designer",
      type: "annual", startDate: "2026-09-29", endDate: "2026-10-03", days: 5, cover: "M. Reid",
      reason: "Family trip booked over the long weekend.",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "pending", submitted: "Jun 28",
      events: [
        ev("submitted", "sarah.chen", "Sarah Chen", "Family trip booked over the long weekend.", stampAt("2026-06-28", 9)),
        ev("routed", "system", "System", "Within manager authority — routed to D. Okonkwo.", stampAt("2026-06-28", 9)),
      ] },
    { id: "R-0988", empId: "sarah.chen", empName: "Sarah Chen", empInitials: "SC", empBg: "#3f7cc4", empRole: "Product Designer",
      type: "annual", startDate: "2026-08-11", endDate: "2026-08-15", days: 5, cover: "M. Reid",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "approved", submitted: "Jun 10",
      events: [
        ev("submitted", "sarah.chen", "Sarah Chen", "", stampAt("2026-06-10", 11)),
        ev("routed", "system", "System", "Routed to D. Okonkwo.", stampAt("2026-06-10", 11)),
        ev("approved", "david.okonkwo", "D. Okonkwo", "Enjoy — coverage looks fine.", stampAt("2026-06-12", 14)),
      ] },
    { id: "R-0975", empId: "sarah.chen", empName: "Sarah Chen", empInitials: "SC", empBg: "#3f7cc4", empRole: "Product Designer",
      type: "sick", startDate: "2026-06-24", endDate: "2026-06-24", days: 1, cover: "—",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "posted", submitted: "Jun 24",
      events: [
        ev("submitted", "sarah.chen", "Sarah Chen", "Woke up unwell.", stampAt("2026-06-24", 8)),
        ev("approved", "david.okonkwo", "D. Okonkwo", "Auto-approved — under 2 days.", stampAt("2026-06-24", 8)),
        ev("posted", "system", "System", "Posted to team calendar & payroll.", stampAt("2026-06-25", 6)),
      ] },
    { id: "R-0900", empId: "sarah.chen", empName: "Sarah Chen", empInitials: "SC", empBg: "#3f7cc4", empRole: "Product Designer",
      type: "personal", startDate: "2026-05-02", endDate: "2026-05-02", days: 1, cover: "E. Vasquez",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "rejected", submitted: "Apr 28",
      events: [
        ev("submitted", "sarah.chen", "Sarah Chen", "", stampAt("2026-04-28", 15)),
        ev("routed", "system", "System", "Routed to D. Okonkwo.", stampAt("2026-04-28", 15)),
        ev("rejected", "david.okonkwo", "D. Okonkwo", "Design review is locked for that Friday — can we move it a week?", stampAt("2026-04-30", 10)),
      ] },
    { id: "R-0854", empId: "sarah.chen", empName: "Sarah Chen", empInitials: "SC", empBg: "#3f7cc4", empRole: "Product Designer",
      type: "annual", startDate: "2026-03-17", endDate: "2026-03-18", days: 2, cover: "M. Reid",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "posted", submitted: "Mar 1",
      events: [
        ev("submitted", "sarah.chen", "Sarah Chen", "", stampAt("2026-03-01", 9)),
        ev("routed", "system", "System", "Routed to D. Okonkwo.", stampAt("2026-03-01", 9)),
        ev("approved", "david.okonkwo", "D. Okonkwo", "", stampAt("2026-03-03", 13)),
        ev("posted", "system", "System", "Posted to team calendar & payroll.", stampAt("2026-03-19", 6)),
      ] },

    // ===== David's approval queue — within his authority, pending =====
    { id: "R-1050", empId: "marcus.reid", empName: "Marcus Reid", empInitials: "MR", empBg: "#6b7db5", empRole: "Senior Engineer",
      type: "annual", startDate: "2026-08-24", endDate: "2026-08-28", days: 5, cover: "T. Bradley",
      reason: "Pre-booked holiday.",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "pending", submitted: "Jun 30",
      events: [
        ev("submitted", "marcus.reid", "Marcus Reid", "Pre-booked holiday.", stampAt("2026-06-30", 16)),
        ev("routed", "system", "System", "Routed to D. Okonkwo.", stampAt("2026-06-30", 16)),
      ] },
    { id: "R-1051", empId: "tom.bradley", empName: "Tom Bradley", empInitials: "TB", empBg: "#6ba58f", empRole: "Engineer",
      type: "sick", startDate: "2026-07-08", endDate: "2026-07-14", days: 5, cover: "M. Reid", flag: "certificate pending",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "pending", submitted: "Jul 1",
      events: [
        ev("submitted", "tom.bradley", "Tom Bradley", "Minor surgery — recovery week.", stampAt("2026-07-01", 7)),
        ev("routed", "system", "System", "Routed to D. Okonkwo. Flagged: certificate pending.", stampAt("2026-07-01", 7)),
      ] },
    { id: "R-1052", empId: "elena.vasquez", empName: "Elena Vasquez", empInitials: "EV", empBg: "#c07f4f", empRole: "Product Manager",
      type: "annual", startDate: "2026-09-01", endDate: "2026-09-12", days: 8, cover: "M. Reid",
      managerId: "david.okonkwo", approverId: "david.okonkwo", overCeiling: false, status: "pending", submitted: "Jun 26",
      events: [
        ev("submitted", "elena.vasquez", "Elena Vasquez", "", stampAt("2026-06-26", 12)),
        ev("routed", "system", "System", "Routed to D. Okonkwo.", stampAt("2026-06-26", 12)),
      ] },

    // ===== Over ceiling → escalated to HR (Priya). Read-only in David's queue. =====
    { id: "R-1048", empId: "anna.kim", empName: "Anna Kim", empInitials: "AK", empBg: "#b56b8f", empRole: "Designer",
      type: "parental", startDate: "2026-11-03", endDate: "2026-11-28", days: 20, cover: "E. Vasquez",
      managerId: "david.okonkwo", approverId: "priya.nair", overCeiling: true, status: "escalated", submitted: "Jun 15",
      ceilingNote: "Extended parental leave exceeds a manager's 10-day authority ceiling.",
      events: [
        ev("submitted", "anna.kim", "Anna Kim", "Parental leave — expecting in November.", stampAt("2026-06-15", 10)),
        ev("escalated", "system", "System", "Over the 10-day manager ceiling — escalated to HR (P. Nair).", stampAt("2026-06-15", 10)),
      ] },
    { id: "R-1049", empId: "james.okoro", empName: "James Okoro", empInitials: "JO", empBg: "#7a6bb5", empRole: "Engineer",
      type: "unpaid", startDate: "2026-10-06", endDate: "2026-10-24", days: 15, cover: "T. Bradley",
      managerId: "david.okonkwo", approverId: "priya.nair", overCeiling: true, status: "escalated", submitted: "Jun 22",
      ceilingNote: "Extended unpaid leave over 10 days requires higher approval.",
      events: [
        ev("submitted", "james.okoro", "James Okoro", "Personal sabbatical.", stampAt("2026-06-22", 9)),
        ev("escalated", "system", "System", "Over the 10-day manager ceiling — escalated to HR (P. Nair).", stampAt("2026-06-22", 9)),
      ] },
  ];
}

export function allRequests() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) {
      const seeded = seedData();
      localStorage.setItem(KEY, JSON.stringify(seeded));
      return seeded;
    }
    return JSON.parse(raw);
  } catch (e) {
    return seedData();
  }
}

function saveAll(list) {
  localStorage.setItem(KEY, JSON.stringify(list));
}

export function getRequest(id) {
  return allRequests().find((r) => r.id === id) || null;
}

export function addRequest(req) {
  const list = allRequests();
  const n = 1053 + list.filter((r) => /^R-10[5-9]\d$/.test(r.id)).length;
  const id = "R-" + n;
  const s = stampNow();
  const events = [ev("submitted", req.empId, req.empName, req.reason || "", s)];
  if (req.overCeiling) {
    events.push(ev("escalated", "system", "System",
      "Over the 10-day manager ceiling — escalated to HR (P. Nair).", s));
  } else {
    const appr = APPROVERS[req.approverId];
    events.push(ev("routed", "system", "System",
      "Within manager authority — routed to " + (appr ? appr.name : "your approver") + ".", s));
  }
  const full = { id, submitted: fmt(new Date().toISOString().slice(0, 10)), createdAt: s.at, events, ...req };
  list.unshift(full);
  saveAll(list);
  return full;
}

// decision: "approved" | "rejected" | "info". Records an event with an optional note.
export function decide(id, decision, byUserId, note) {
  const list = allRequests();
  const idx = list.findIndex((r) => r.id === id);
  if (idx < 0) return;
  const actor = APPROVERS[byUserId];
  const actorName = actor ? actor.name : byUserId;
  const kind = decision === "info" ? "info_requested" : decision;
  const r = { ...list[idx] };
  r.status = decision;
  r.decidedBy = byUserId;
  r.decidedAt = Date.now();
  r.events = [...(r.events || []), ev(kind, byUserId, actorName, note, stampNow())];
  list[idx] = r;
  saveAll(list);
}

// Undo the most recent decision — pops the last event and restores the open state.
export function revertDecision(id) {
  const list = allRequests();
  const idx = list.findIndex((r) => r.id === id);
  if (idx < 0) return;
  const r = { ...list[idx] };
  const events = [...(r.events || [])];
  if (events.length) events.pop();
  r.events = events;
  r.status = r.overCeiling ? "escalated" : "pending";
  delete r.decidedBy;
  delete r.decidedAt;
  list[idx] = r;
  saveAll(list);
}

// Employee withdraws their own request while it is still open.
export function cancelRequest(id, byUserId, byName) {
  const list = allRequests();
  const idx = list.findIndex((r) => r.id === id);
  if (idx < 0) return;
  const r = { ...list[idx] };
  r.status = "cancelled";
  r.events = [...(r.events || []), ev("cancelled", byUserId, byName || byUserId, "Withdrawn by requester.", stampNow())];
  list[idx] = r;
  saveAll(list);
}

export function forEmployee(empId) {
  return allRequests().filter((r) => r.empId === empId);
}

// Returns { actionable: [...], escalated: [...] } for an approver.
export function forApprover(user) {
  const list = allRequests();
  if (user.role === "manager") {
    return {
      actionable: list.filter((r) => r.managerId === user.id && !r.overCeiling && r.status === "pending"),
      escalated: list.filter((r) => r.managerId === user.id && r.overCeiling && r.status === "escalated"),
    };
  }
  if (user.role === "hr") {
    return {
      actionable: list.filter((r) => r.approverId === user.id && r.status === "escalated"),
      escalated: [],
    };
  }
  return { actionable: [], escalated: [] };
}

export function pendingCountFor(user) {
  return forApprover(user).actionable.length;
}

// ---- Notifications: derived from the event log, per user ----
function lastRead(userId) {
  try {
    const map = JSON.parse(localStorage.getItem(READ_KEY)) || {};
    return map[userId] || 0;
  } catch (e) { return 0; }
}
export function markNotificationsRead(userId) {
  let map = {};
  try { map = JSON.parse(localStorage.getItem(READ_KEY)) || {}; } catch (e) {}
  map[userId] = Date.now();
  localStorage.setItem(READ_KEY, JSON.stringify(map));
}

// Notifications an employee cares about: decisions/updates on their own requests.
// Notifications an approver cares about: requests newly routed / escalated to them.
export function notificationsFor(user) {
  if (!user) return [];
  const seen = lastRead(user.id);
  const out = [];
  allRequests().forEach((r) => {
    const tm = TYPE_META[r.type] || {};
    (r.events || []).forEach((e, i) => {
      let title = null, sub = null;
      if (r.empId === user.id) {
        // employee-facing outcomes
        if (e.kind === "approved") { title = "Your " + (tm.short || r.type).toLowerCase() + " leave was approved"; sub = e.actorName + (e.note ? " · " + e.note : ""); }
        else if (e.kind === "rejected") { title = "Your " + (tm.short || r.type).toLowerCase() + " leave was declined"; sub = e.actorName + (e.note ? " · " + e.note : ""); }
        else if (e.kind === "info_requested") { title = e.actorName + " requested more info"; sub = e.note || (tm.short + " · " + formatRange(r.startDate, r.endDate)); }
        else if (e.kind === "escalated") { title = "Your request was escalated to HR"; sub = tm.short + " · " + formatRange(r.startDate, r.endDate); }
        else if (e.kind === "posted") { title = "Leave posted to calendar"; sub = tm.short + " · " + formatRange(r.startDate, r.endDate); }
      } else if ((user.role === "manager" || user.role === "hr")) {
        // approver-facing inbound work
        const mineManager = user.role === "manager" && r.managerId === user.id && !r.overCeiling && e.kind === "routed" && r.status === "pending";
        const mineHr = user.role === "hr" && r.approverId === user.id && e.kind === "escalated" && r.status === "escalated";
        if (mineManager || mineHr) {
          title = r.empName + " requested " + r.days + "d " + (tm.short || r.type).toLowerCase();
          sub = formatRange(r.startDate, r.endDate) + (mineHr ? " · needs HR approval" : " · awaiting you");
        }
      }
      if (title) out.push({ id: r.id + "-" + i, reqId: r.id, title, sub, at: e.at, atLabel: e.atLabel, unread: e.at > seen });
    });
  });
  return out.sort((a, b) => b.at - a.at).slice(0, 12);
}

export function unreadCountFor(user) {
  return notificationsFor(user).filter((n) => n.unread).length;
}

// Reset helper for demos.
export function resetSeed() {
  localStorage.removeItem(KEY);
  localStorage.removeItem(READ_KEY);
}
