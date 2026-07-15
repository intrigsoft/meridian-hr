// Performance data layer for the Meridian HR fixture.
// Stands in for the server-side performance tables + review orchestration a
// Thymeleaf app would own. Everything persists to localStorage.
//
// Model mirrors the onboarding store:
//   COMPETENCY_LIBRARY — catalog HR picks from when designing a cycle
//   EMPLOYEES          — the reviewable population (with manager mapping)
//   cycles   — HR-designed review SCHEMAS: competencies + weights, scale,
//              timeline, participants, status (draft | active | closed)
//   reviews  — running review INSTANCES, one per participant per cycle,
//              carrying self / manager / calibrated scores + narratives.
//              A review's STATUS is COMPUTED from what's been submitted.

import * as people from "./people-store.js";

const CYCLE_KEY = "meridian.performance.cycles.v2";
const REVIEW_KEY = "meridian.performance.reviews.v2";

// ---- Competency library (HR picks from this when designing a cycle) ----
// localStorage-backed so HR can edit it in Settings; seeds from this catalog.
const LIB_KEY = "meridian.performance.competencies.v1";
const COMPETENCY_SEED = [
  { id: "exec",     name: "Technical execution", blurb: "Ships high-quality work reliably." },
  { id: "craft",    name: "Craft & quality",     blurb: "Attention to detail and standards." },
  { id: "collab",   name: "Collaboration",       blurb: "Works across teams effectively." },
  { id: "owner",    name: "Ownership",           blurb: "Drives outcomes end-to-end." },
  { id: "comm",     name: "Communication",       blurb: "Clear written and verbal updates." },
  { id: "lead",     name: "Leadership",          blurb: "Elevates and mentors others." },
  { id: "customer", name: "Customer focus",      blurb: "Anchors decisions in user value." },
  { id: "innov",    name: "Innovation",          blurb: "Brings new ideas that move metrics." },
  { id: "reliab",   name: "Reliability",         blurb: "Consistent, dependable delivery." },
  { id: "strategy", name: "Strategic thinking",  blurb: "Connects work to the bigger picture." },
];
export function getLibrary() {
  try {
    const raw = localStorage.getItem(LIB_KEY);
    if (!raw) { localStorage.setItem(LIB_KEY, JSON.stringify(COMPETENCY_SEED)); return COMPETENCY_SEED.slice(); }
    return JSON.parse(raw);
  } catch (e) { return COMPETENCY_SEED.slice(); }
}
function saveLibrary(list) { localStorage.setItem(LIB_KEY, JSON.stringify(list)); return list; }
export function competency(id) { return getLibrary().find((c) => c.id === id) || { id, name: id, blurb: "" }; }
export function addCompetency(name, blurb) {
  const lib = getLibrary();
  let base = (name || "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "") || "comp";
  let id = base, n = 2; while (lib.some((c) => c.id === id)) { id = base + "-" + n; n++; }
  lib.push({ id, name: (name || "New competency").trim(), blurb: (blurb || "").trim() });
  return saveLibrary(lib);
}
export function updateCompetency(id, patch) {
  const lib = getLibrary(); const i = lib.findIndex((c) => c.id === id);
  if (i >= 0) lib[i] = { ...lib[i], ...patch }; return saveLibrary(lib);
}
export function removeCompetency(id) { return saveLibrary(getLibrary().filter((c) => c.id !== id)); }

export const CYCLE_TYPES = [
  { id: "half", label: "Half-yearly review" },
  { id: "annual", label: "Annual review" },
  { id: "quarter", label: "Quarterly check-in" },
  { id: "probation", label: "Probation review" },
];
export function cycleTypeLabel(id) { return (CYCLE_TYPES.find((t) => t.id === id) || {}).label || id; }

// ---- Reviewable population — SINGLE SOURCE OF TRUTH: people-store ----
// Performance no longer keeps its own person list. It derives reviewees from
// the same employee records the Directory / profile use, so there is exactly
// one person record across the whole product. Reviewer = that person's manager.
// Inactive employees are excluded from the reviewable population.
function toReviewee(e) {
  return { id: e.id, name: people.fullName(e), initials: e.initials, avatarBg: e.avatarBg,
    title: e.title, dept: e.dept, managerId: e.managerId };
}
export function employees() {
  return people.allEmployees().filter((e) => e.status !== "inactive").map(toReviewee);
}

// Fallback names for reviewer ids not present as reviewees (kept for safety).
export const REVIEWERS = {
  "priya.nair": { id: "priya.nair", name: "Priya Nair", title: "HR Business Partner" },
};

export function employee(id) {
  const e = people.getEmployee(id);
  return e ? toReviewee(e) : null;
}
export function reviewerName(id) {
  const e = employee(id); if (e) return e.name;
  const r = REVIEWERS[id]; return r ? r.name : "—";
}
export function directReports(managerId) { return employees().filter((e) => e.managerId === managerId); }
export function departments() {
  const set = []; employees().forEach((e) => { if (set.indexOf(e.dept) < 0) set.push(e.dept); }); return set;
}

// ---- Deterministic score generation (stable across reloads) ----
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hashStr(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
function clamp5(n) { return Math.max(1, Math.min(5, Math.round(n))); }

// ---- Seed cycles ----
function comp(id, weight) { return { id, weight }; }
function seedCycles() {
  const now = Date.now();
  const stdComps = [comp("exec", 30), comp("craft", 20), comp("collab", 20), comp("owner", 15), comp("comm", 15)];
  return [
    {
      id: "cyc-h1-2026", name: "H1 2026 Review", type: "half", status: "active",
      startDate: "2026-06-01", selfDue: "2026-06-19", mgrDue: "2026-06-30", calibrationDate: "2026-07-10",
      scaleMax: 5, competencies: stdComps,
      participants: employees().map((e) => e.id),
      createdAt: now - 86400000 * 40, createdBy: "Priya Nair",
    },
    {
      id: "cyc-h2-2025", name: "H2 2025 Review", type: "half", status: "closed",
      startDate: "2025-12-01", selfDue: "2025-12-15", mgrDue: "2025-12-22", calibrationDate: "2026-01-08",
      scaleMax: 5, competencies: stdComps,
      participants: employees().map((e) => e.id),
      createdAt: now - 86400000 * 220, createdBy: "Priya Nair",
    },
    {
      id: "cyc-q3-2026", name: "Q3 2026 Check-in", type: "quarter", status: "draft",
      startDate: "2026-09-01", selfDue: "2026-09-12", mgrDue: "2026-09-20", calibrationDate: "2026-09-30",
      scaleMax: 5, competencies: [comp("exec", 40), comp("collab", 30), comp("owner", 30)],
      participants: employees().filter((e) => e.dept === "Engineering").map((e) => e.id),
      createdAt: now - 86400000 * 3, createdBy: "Priya Nair",
    },
  ];
}

// ---- Seed reviews (instances) ----
const SELF_NOTES = [
  "Delivered my committed roadmap this half and picked up two stretch projects. I'd rate my cross-team collaboration as a clear strength.",
  "Focused on raising the quality bar — fewer regressions, cleaner handoffs. Stakeholder communication is where I invested most.",
  "Led a major initiative end-to-end and mentored a junior. I see myself exceeding expectations on ownership this cycle.",
  "Steady delivery against goals with a few standout wins. Looking to grow into more strategic, cross-org work next half.",
];
const MGR_NOTES = [
  "Strong technical execution — among the most dependable on the team. Growth area is proactively surfacing risk earlier.",
  "Excellent craft and consistently high quality. Collaboration slipped on one cross-team effort; addressed and improving.",
  "Owns outcomes and raises the bar for peers. Ready for more scope; a clear path to the next level.",
  "Meets expectations with a strong trajectory. Communication with stakeholders is the biggest lever for the next half.",
];

// Explicit phase for demo-critical people; everyone else derived by modulo.
const PHASE_OVERRIDE = {
  "sarah.chen": "awaiting_self",     // employee demo has something to do
  "marcus.reid": "in_calibration",   // matches the original calibration screen
  "ravi.menon": "awaiting_manager",  // David has a manager assessment to write
  "tom.bright": "awaiting_manager",
  "aisha.khan": "in_calibration",
  "lena.fischer": "committed",
};

function buildReview(cycle, emp) {
  const rng = mulberry32(hashStr(cycle.id + ":" + emp.id));
  const base = 2.7 + rng() * 1.9; // "true" level 2.7–4.6
  const selfScores = {}, mgrScores = {}, calScores = {};
  cycle.competencies.forEach((c) => {
    const s = clamp5(base + 0.45 + (rng() - 0.35) * 1.4);
    const m = clamp5(base + (rng() - 0.5) * 1.2);
    selfScores[c.id] = s; mgrScores[c.id] = m; calScores[c.id] = m;
  });

  // Marcus keeps the exact scores from the original screen where competencies line up.
  if (emp.id === "marcus.reid") {
    const fixed = { exec: [5, 5, 5], craft: [4, 4, 4], collab: [5, 3, 3], owner: [4, 4, 4], comm: [5, 4, 4] };
    cycle.competencies.forEach((c) => {
      if (fixed[c.id]) { selfScores[c.id] = fixed[c.id][0]; mgrScores[c.id] = fixed[c.id][1]; calScores[c.id] = fixed[c.id][2]; }
    });
  }

  // Phase
  let phase = PHASE_OVERRIDE[emp.id];
  if (!phase) {
    if (cycle.status === "closed") phase = "committed";
    else if (cycle.status === "draft") phase = "awaiting_self";
    else {
      const r = Math.floor(rng() * 10);
      phase = r < 5 ? "committed" : r < 7 ? "in_calibration" : r < 9 ? "awaiting_manager" : "awaiting_self";
    }
  }

  const ni = hashStr(emp.id) % 4;
  const selfNote = SELF_NOTES[ni], mgrNote = MGR_NOTES[(ni + 1) % 4];
  const day = (n) => Date.now() - 86400000 * n;

  const review = {
    cycleId: cycle.id, empId: emp.id, reviewerId: emp.managerId,
    self: { scores: {}, narrative: "", submittedAt: null },
    mgr: { scores: {}, narrative: "", submittedAt: null },
    cal: { scores: null, committed: false, committedAt: null },
  };

  const selfDone = phase !== "awaiting_self";
  const mgrDone = phase === "committed" || phase === "in_calibration";
  const committed = phase === "committed";

  if (selfDone) { review.self = { scores: selfScores, narrative: selfNote, submittedAt: day(18) }; }
  if (mgrDone) { review.mgr = { scores: mgrScores, narrative: mgrNote, submittedAt: day(9) }; }
  if (mgrDone) { review.cal = { scores: { ...calScores }, committed, committedAt: committed ? day(3) : null }; }

  return review;
}

// ---- Persistence ----
export function allCycles() {
  try {
    const raw = localStorage.getItem(CYCLE_KEY);
    if (!raw) { const s = seedCycles(); localStorage.setItem(CYCLE_KEY, JSON.stringify(s)); return s; }
    return JSON.parse(raw);
  } catch (e) { return seedCycles(); }
}
function saveCycles(list) { localStorage.setItem(CYCLE_KEY, JSON.stringify(list)); }
export function getCycle(id) { return allCycles().find((c) => c.id === id) || null; }
export function activeCycle() { const list = allCycles(); return list.find((c) => c.status === "active") || list[0] || null; }

export function saveCycle(cycle) {
  const list = allCycles();
  const idx = list.findIndex((c) => c.id === cycle.id);
  if (idx >= 0) list[idx] = cycle; else list.unshift(cycle);
  saveCycles(list);
  return cycle;
}
export function createCycle(name) {
  const cycle = {
    id: "cyc-" + Math.random().toString(36).slice(2, 8),
    name: name || "New review cycle", type: "half", status: "draft",
    startDate: "2026-07-01", selfDue: "2026-07-15", mgrDue: "2026-07-25", calibrationDate: "2026-08-05",
    scaleMax: 5,
    competencies: [comp("exec", 40), comp("collab", 30), comp("owner", 30)],
    participants: [],
    createdAt: Date.now(), createdBy: "Priya Nair",
  };
  const list = allCycles(); list.unshift(cycle); saveCycles(list);
  return cycle;
}
export function deleteCycle(id) {
  saveCycles(allCycles().filter((c) => c.id !== id));
  // orphan reviews left in place are harmless; drop them for tidiness
  const all = allReviewsRaw();
  saveReviews(all.filter((r) => r.cycleId !== id));
}
export function launchCycle(id) {
  const list = allCycles();
  const c = list.find((x) => x.id === id); if (!c) return;
  c.status = "active";
  saveCycles(list);
  ensureReviews(c); // materialize instances for every participant
}
export function closeCycle(id) {
  const list = allCycles();
  const c = list.find((x) => x.id === id); if (!c) return;
  c.status = "closed"; saveCycles(list);
}

// ---- Reviews (instances) ----
function allReviewsRaw() {
  try {
    const raw = localStorage.getItem(REVIEW_KEY);
    if (!raw) { const s = seedReviews(); localStorage.setItem(REVIEW_KEY, JSON.stringify(s)); return s; }
    return JSON.parse(raw);
  } catch (e) { return seedReviews(); }
}
function saveReviews(list) { localStorage.setItem(REVIEW_KEY, JSON.stringify(list)); }
function seedReviews() {
  const out = [];
  seedCycles().forEach((cy) => {
    if (cy.status === "draft") return; // drafts have no instances yet
    cy.participants.forEach((eid) => { const e = employee(eid); if (e) out.push(buildReview(cy, e)); });
  });
  return out;
}
// Make sure every participant of a cycle has a review row (used on launch).
function ensureReviews(cycle) {
  const all = allReviewsRaw();
  let changed = false;
  cycle.participants.forEach((eid) => {
    if (!all.find((r) => r.cycleId === cycle.id && r.empId === eid)) {
      all.push({
        cycleId: cycle.id, empId: eid, reviewerId: (employee(eid) || {}).managerId || null,
        self: { scores: {}, narrative: "", submittedAt: null },
        mgr: { scores: {}, narrative: "", submittedAt: null },
        cal: { scores: null, committed: false, committedAt: null },
      });
      changed = true;
    }
  });
  if (changed) saveReviews(all);
}

export function reviewsForCycle(cycleId) { return allReviewsRaw().filter((r) => r.cycleId === cycleId); }
export function getReview(cycleId, empId) { return allReviewsRaw().find((r) => r.cycleId === cycleId && r.empId === empId) || null; }
export function saveReview(review) {
  const all = allReviewsRaw();
  const idx = all.findIndex((r) => r.cycleId === review.cycleId && r.empId === review.empId);
  if (idx >= 0) all[idx] = review; else all.push(review);
  saveReviews(all);
  return review;
}

// ---- Mutations used by the Review screen ----
export function submitSelf(cycleId, empId, scores, narrative) {
  const r = getReview(cycleId, empId) || { cycleId, empId, reviewerId: (employee(empId) || {}).managerId, self: {}, mgr: { scores: {}, narrative: "", submittedAt: null }, cal: { scores: null, committed: false } };
  r.self = { scores: { ...scores }, narrative: narrative || "", submittedAt: Date.now() };
  return saveReview(r);
}
export function submitManager(cycleId, empId, scores, narrative) {
  const r = getReview(cycleId, empId); if (!r) return null;
  r.mgr = { scores: { ...scores }, narrative: narrative || "", submittedAt: Date.now() };
  if (!r.cal || !r.cal.scores) r.cal = { scores: { ...scores }, committed: false, committedAt: null }; // seed calibration from manager
  return saveReview(r);
}
export function setCalibratedScore(cycleId, empId, compId, value) {
  const r = getReview(cycleId, empId); if (!r || !r.cal) return null;
  if (r.cal.committed) return r;
  const scores = { ...(r.cal.scores || {}) };
  scores[compId] = Math.max(1, Math.min(5, value));
  r.cal = { ...r.cal, scores };
  return saveReview(r);
}
export function commitCalibration(cycleId, empId) {
  const r = getReview(cycleId, empId); if (!r || !r.cal) return null;
  r.cal = { ...r.cal, committed: true, committedAt: Date.now() };
  return saveReview(r);
}
export function reopenCalibration(cycleId, empId) {
  const r = getReview(cycleId, empId); if (!r || !r.cal) return null;
  r.cal = { ...r.cal, committed: false, committedAt: null };
  return saveReview(r);
}

// ---- Status computation ----
// awaiting_self → awaiting_manager → in_calibration → committed
export function reviewStatus(r) {
  if (!r) return "awaiting_self";
  if (!r.self || !r.self.submittedAt) return "awaiting_self";
  if (!r.mgr || !r.mgr.submittedAt) return "awaiting_manager";
  if (!r.cal || !r.cal.committed) return "in_calibration";
  return "committed";
}
export const STATUS_META = {
  awaiting_self:    { label: "Awaiting self",    short: "Self",   bg: "#eef1f4", fg: "#6b7480", bar: "#c7cdd6", step: 1 },
  awaiting_manager: { label: "Awaiting manager", short: "Manager",bg: "#f7f1e0", fg: "#9a6a1a", bar: "#c68a2a", step: 2 },
  in_calibration:   { label: "In calibration",   short: "Calibrate", bg: "#e9f0f9", fg: "#2f6aa8", bar: "#3f7cc4", step: 3 },
  committed:        { label: "Committed",        short: "Done",   bg: "#e6f3ec", fg: "#2f6f4f", bar: "#4a9d7a", step: 4 },
};

export function weightedAvg(cycle, scores) {
  if (!scores) return null;
  let tot = 0, wsum = 0;
  cycle.competencies.forEach((c) => { const v = scores[c.id]; if (v != null) { tot += v * c.weight; wsum += c.weight; } });
  return wsum ? tot / wsum : null;
}
export function scoreBand(v) {
  if (v == null) return { label: "—", color: "#9aa3ad", bg: "#f1f4f7" };
  if (v >= 4.5) return { label: "Outstanding", color: "#2f6f4f", bg: "#e6f3ec" };
  if (v >= 3.5) return { label: "Exceeds",      color: "#2f6aa8", bg: "#e9f0f9" };
  if (v >= 2.5) return { label: "Meets",        color: "#9a6a1a", bg: "#f7f1e0" };
  return { label: "Below", color: "#b23b2e", bg: "#fbeae8" };
}
// Cell background for a raw 1–5 score.
export function cellColor(v) {
  return v >= 5 ? "#3d8564" : v >= 4 ? "#3f7cc4" : v >= 3 ? "#c68a2a" : v >= 2 ? "#c0563f" : "#a84334";
}

// ---- Report roll-ups ----
export function completionFor(cycleId) {
  const rows = reviewsForCycle(cycleId);
  const counts = { awaiting_self: 0, awaiting_manager: 0, in_calibration: 0, committed: 0 };
  rows.forEach((r) => { counts[reviewStatus(r)]++; });
  const total = rows.length;
  const done = counts.committed;
  return { counts, total, done, pct: total ? Math.round((done / total) * 100) : 0 };
}
// Distribution of committed calibrated overall scores, bucketed into 5 bands.
export function distributionFor(cycleId) {
  const cycle = getCycle(cycleId);
  const rows = reviewsForCycle(cycleId).filter((r) => r.cal && r.cal.committed);
  const bands = [
    { key: "below", label: "Below (1–2.4)", min: 0, color: "#c0563f" },
    { key: "meets", label: "Meets (2.5–3.4)", min: 2.5, color: "#c68a2a" },
    { key: "strong", label: "Exceeds (3.5–4.4)", min: 3.5, color: "#3f7cc4" },
    { key: "top", label: "Outstanding (4.5–5)", min: 4.5, color: "#3d8564" },
  ];
  const counts = { below: 0, meets: 0, strong: 0, top: 0 };
  rows.forEach((r) => {
    const v = weightedAvg(cycle, r.cal.scores);
    const k = v >= 4.5 ? "top" : v >= 3.5 ? "strong" : v >= 2.5 ? "meets" : "below";
    counts[k]++;
  });
  const total = rows.length || 1;
  return bands.map((b) => ({ ...b, count: counts[b.key], pct: Math.round((counts[b.key] / total) * 100) }));
}
// Per-competency average self vs manager across submitted reviews (calibration gaps).
export function gapsFor(cycleId) {
  const cycle = getCycle(cycleId);
  const rows = reviewsForCycle(cycleId).filter((r) => r.self && r.self.submittedAt && r.mgr && r.mgr.submittedAt);
  return cycle.competencies.map((c) => {
    let sSum = 0, mSum = 0, n = 0;
    rows.forEach((r) => {
      const s = r.self.scores[c.id], m = r.mgr.scores[c.id];
      if (s != null && m != null) { sSum += s; mSum += m; n++; }
    });
    const self = n ? sSum / n : 0, mgr = n ? mSum / n : 0;
    return { id: c.id, name: competency(c.id).name, weight: c.weight, self, mgr, delta: self - mgr, n };
  });
}
export function perEmployee(cycleId) {
  const cycle = getCycle(cycleId);
  return reviewsForCycle(cycleId).map((r) => {
    const e = employee(r.empId) || { name: r.empId, dept: "—" };
    return {
      empId: r.empId, name: e.name, initials: e.initials, avatarBg: e.avatarBg, title: e.title, dept: e.dept,
      reviewer: reviewerName(r.reviewerId), status: reviewStatus(r),
      self: weightedAvg(cycle, r.self && r.self.submittedAt ? r.self.scores : null),
      mgr: weightedAvg(cycle, r.mgr && r.mgr.submittedAt ? r.mgr.scores : null),
      cal: weightedAvg(cycle, r.cal && r.cal.committed ? r.cal.scores : null),
    };
  });
}

export function resetPerformance() {
  localStorage.removeItem(CYCLE_KEY);
  localStorage.removeItem(REVIEW_KEY);
}
