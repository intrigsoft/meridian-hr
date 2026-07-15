// Recruitment (ATS) data layer for the Meridian HR fixture.
// Stands in for the server-side ATS tables a Thymeleaf app would own.
// Everything persists to localStorage. Mirrors the onboarding/performance stores.
//
// Model:
//   PEOPLE            — interviewer / owner / recruiter directory
//   SCORECARD_LIBRARY — attribute catalog a requisition's scorecard is built from
//   STAGES            — canonical pipeline stages
//   requisitions      — job openings HR/HM design (scorecard + interview plan + approval)
//   candidates        — applications attached to a req, carrying stage, scorecards,
//                       offer, and (once hired) a link to an Onboarding case.

const REQ_KEY = "meridian.recruitment.reqs.v2";
const CAND_KEY = "meridian.recruitment.cands.v2";

// ---- People (owners / recruiters / interviewers) ----
export const PEOPLE = {
  "priya.nair":    { id: "priya.nair",    name: "Priya Nair",    initials: "PN", bg: "#4a9d7a", title: "HR Business Partner" },
  "david.okonkwo": { id: "david.okonkwo", name: "David Okonkwo", initials: "DO", bg: "#c47f3f", title: "Engineering Manager" },
  "marcus.reid":   { id: "marcus.reid",   name: "Marcus Reid",   initials: "MR", bg: "#6b7db5", title: "Senior Engineer" },
  "aisha.khan":    { id: "aisha.khan",    name: "Aisha Khan",    initials: "AK", bg: "#4a9d9d", title: "Staff Engineer" },
  "nadia.rahman":  { id: "nadia.rahman",  name: "Nadia Rahman",  initials: "NR", bg: "#9a6ab5", title: "Design Manager" },
  "sarah.chen":    { id: "sarah.chen",    name: "Sarah Chen",    initials: "SC", bg: "#3f7cc4", title: "Product Designer" },
  "ken.ito":       { id: "ken.ito",       name: "Ken Ito",       initials: "KI", bg: "#6ba58f", title: "Product Designer" },
  "elena.vasquez": { id: "elena.vasquez", name: "Elena Vasquez", initials: "EV", bg: "#b58f4a", title: "Revenue Manager" },
  "sofia.alvarez": { id: "sofia.alvarez", name: "Sofia Alvarez", initials: "SA", bg: "#6ba58f", title: "Account Executive" },
  "marco.rossi":   { id: "marco.rossi",   name: "Marco Rossi",   initials: "MO", bg: "#6b8fb5", title: "Operations Manager" },
  "julia.novak":   { id: "julia.novak",   name: "Julia Novak",   initials: "JN", bg: "#b56b8f", title: "Program Manager" },
  "alex.vp":       { id: "alex.vp",       name: "Alex Whitfield",initials: "AW", bg: "#7a6bb5", title: "VP, Operations" },
};
export function person(id) { return PEOPLE[id] || { id, name: id || "—", initials: "?", bg: "#c7cdd6", title: "" }; }

export const DEPARTMENTS = ["Engineering", "Design", "Revenue", "Operations", "People Operations"];
export const LEVELS = ["Junior", "Mid", "Senior", "Staff", "Lead", "Manager"];
export const SOURCES = ["Referral", "LinkedIn", "Job board", "CV corpus", "Inbound", "Agency"];

// ---- Scorecard attribute library (a req picks a subset) ----
export const SCORECARD_LIBRARY = [
  { id: "coding",    name: "Coding" },
  { id: "system",    name: "System design" },
  { id: "problem",   name: "Problem solving" },
  { id: "comm",      name: "Communication" },
  { id: "collab",    name: "Collaboration" },
  { id: "product",   name: "Product sense" },
  { id: "craft",     name: "Craft & taste" },
  { id: "ownership", name: "Ownership" },
  { id: "sales",     name: "Sales acumen" },
  { id: "domain",    name: "Domain expertise" },
  { id: "culture",   name: "Values / culture add" },
];
export function attr(id) { return SCORECARD_LIBRARY.find((a) => a.id === id) || { id, name: id }; }

// ---- Canonical pipeline ----
export const STAGES = [
  { id: "applied",  label: "Applied",         short: "Applied" },
  { id: "screen",   label: "Recruiter screen",short: "Screen" },
  { id: "interview",label: "HM interview",    short: "Interview" },
  { id: "onsite",   label: "Onsite panel",    short: "Onsite" },
  { id: "offer",    label: "Offer",           short: "Offer" },
  { id: "hired",    label: "Hired",           short: "Hired" },
];
export const STAGE_ORDER = STAGES.map((s) => s.id);
export function stageMeta(id) {
  const map = {
    applied:   { label: "Applied",   color: "#6b7480", bg: "#eef1f4" },
    screen:    { label: "Screen",    color: "#2f6aa8", bg: "#e9f0f9" },
    interview: { label: "Interview", color: "#5b3ea8", bg: "#f0ecfd" },
    onsite:    { label: "Onsite",    color: "#7a5aa8", bg: "#f2ecfa" },
    offer:     { label: "Offer",     color: "#9a6a1a", bg: "#f7f1e0" },
    hired:     { label: "Hired",     color: "#2f6f4f", bg: "#e6f3ec" },
    rejected:  { label: "Rejected",  color: "#b23b2e", bg: "#fbeae8" },
  };
  return map[id] || map.applied;
}
// Stages at which interviewers submit scorecards.
export const SCORED_STAGES = ["interview", "onsite"];

// ---- Recommendations ----
export const RECS = {
  strong_yes: { id: "strong_yes", label: "Strong hire", val: 2, color: "#2f6f4f", bg: "#e6f3ec" },
  yes:        { id: "yes",        label: "Hire",        val: 1, color: "#2f6aa8", bg: "#e9f0f9" },
  no:         { id: "no",         label: "No hire",     val: -1, color: "#9a6a1a", bg: "#f7f1e0" },
  strong_no:  { id: "strong_no",  label: "Strong no",   val: -2, color: "#b23b2e", bg: "#fbeae8" },
};
export const REC_ORDER = ["strong_yes", "yes", "no", "strong_no"];

export const REJECTION_REASONS = [
  "Skills mismatch", "Seniority mismatch", "Failed technical screen",
  "Communication concerns", "Compensation misalignment", "Withdrew",
  "Position filled", "Values / culture",
];

// ---- Deterministic RNG (stable seeds) ----
function mulberry32(a) { return function () { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
function hashStr(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
function clamp5(n) { return Math.max(1, Math.min(5, Math.round(n))); }
const AVATARS = ["#6b7db5", "#6ba58f", "#b56b8f", "#c07f4f", "#7a6bb5", "#5a8fb5", "#4a9d9d", "#b58f4a", "#9a6ab5", "#6b8fb5"];
function initialsOf(name) { return name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join("").toUpperCase(); }

// ---- Seed requisitions ----
function plan(stages) { return stages; }
function seedReqs() {
  const now = Date.now();
  const day = 86400000;
  return [
    {
      id: "REQ-2041", title: "Senior Backend Engineer", dept: "Engineering", level: "Senior",
      location: "Remote · EU", headcount: 1, status: "open",
      ownerId: "david.okonkwo", recruiterId: "priya.nair",
      scorecard: ["coding", "system", "problem", "comm", "ownership"],
      interviewPlan: [
        { stageId: "interview", interviewerIds: ["david.okonkwo", "marcus.reid"] },
        { stageId: "onsite", interviewerIds: ["marcus.reid", "aisha.khan", "david.okonkwo"] },
      ],
      approval: { status: "approved", approverId: "alex.vp", at: now - day * 34 },
      createdAt: now - day * 32, openedAt: now - day * 30,
    },
    {
      id: "REQ-2044", title: "Product Designer", dept: "Design", level: "Mid",
      location: "London, UK", headcount: 1, status: "open",
      ownerId: "nadia.rahman", recruiterId: "priya.nair",
      scorecard: ["craft", "product", "comm", "collab", "culture"],
      interviewPlan: [
        { stageId: "interview", interviewerIds: ["nadia.rahman", "sarah.chen"] },
        { stageId: "onsite", interviewerIds: ["nadia.rahman", "ken.ito", "sarah.chen"] },
      ],
      approval: { status: "approved", approverId: "alex.vp", at: now - day * 20 },
      createdAt: now - day * 19, openedAt: now - day * 17,
    },
    {
      id: "REQ-2050", title: "Account Executive", dept: "Revenue", level: "Mid",
      location: "New York, US", headcount: 2, status: "open",
      ownerId: "elena.vasquez", recruiterId: "priya.nair",
      scorecard: ["sales", "comm", "domain", "ownership", "culture"],
      interviewPlan: [
        { stageId: "interview", interviewerIds: ["elena.vasquez"] },
        { stageId: "onsite", interviewerIds: ["elena.vasquez", "sofia.alvarez"] },
      ],
      approval: { status: "approved", approverId: "alex.vp", at: now - day * 12 },
      createdAt: now - day * 11, openedAt: now - day * 9,
    },
    {
      id: "REQ-2038", title: "Staff Engineer", dept: "Engineering", level: "Staff",
      location: "Remote · US", headcount: 1, status: "filled",
      ownerId: "david.okonkwo", recruiterId: "priya.nair",
      scorecard: ["coding", "system", "problem", "ownership", "culture"],
      interviewPlan: [
        { stageId: "interview", interviewerIds: ["david.okonkwo", "aisha.khan"] },
        { stageId: "onsite", interviewerIds: ["aisha.khan", "marcus.reid", "david.okonkwo"] },
      ],
      approval: { status: "approved", approverId: "alex.vp", at: now - day * 95 },
      createdAt: now - day * 92, openedAt: now - day * 90, closedAt: now - day * 34,
    },
    {
      id: "REQ-2052", title: "Operations Analyst", dept: "Operations", level: "Junior",
      location: "Berlin, DE", headcount: 1, status: "pending_approval",
      ownerId: "marco.rossi", recruiterId: "priya.nair",
      scorecard: ["problem", "comm", "domain", "ownership"],
      interviewPlan: [
        { stageId: "interview", interviewerIds: ["marco.rossi", "julia.novak"] },
        { stageId: "onsite", interviewerIds: ["marco.rossi", "julia.novak"] },
      ],
      approval: { status: "pending", approverId: "alex.vp", at: null },
      createdAt: now - day * 3, openedAt: null,
    },
  ];
}

// ---- Seed candidates ----
// Compact spec: [name, currentRole, exp, source, stage] ; scorecards + fit derived.
const CAND_SPEC = {
  "REQ-2041": [
    ["Wei Zhang", "Staff Engineer, Stripe", "8 yrs · Go, Kafka", "Referral", "onsite"],
    ["Daniel Osei", "Senior Eng, Monzo", "7 yrs · Go, k8s", "LinkedIn", "onsite"],
    ["Priyanka Rao", "Tech Lead, Uber", "9 yrs · Go, gRPC", "CV corpus", "interview"],
    ["Lucas Meyer", "Backend Eng, SAP", "6 yrs · Java→Go", "Job board", "interview"],
    ["Aisha Bello", "Engineer, Andela", "5 yrs · Go, AWS", "CV corpus", "screen"],
    ["Tomás Rivera", "Principal, Cloudflare", "10 yrs · Go, Rust", "Referral", "offer"],
    ["Nina Petrova", "Senior Eng, Bolt", "6 yrs · Go, PG", "Inbound", "applied"],
    ["Kwame Mensah", "Eng, Paystack", "4 yrs · Go", "CV corpus", "applied"],
    ["Sara Lindqvist", "Backend Eng, Klarna", "7 yrs · Go, K8s", "LinkedIn", "rejected"],
  ],
  "REQ-2044": [
    ["Mara Devlin", "Product Designer, Figma", "6 yrs", "Referral", "onsite"],
    ["Ibrahim Sy", "Designer, Deezer", "5 yrs", "CV corpus", "interview"],
    ["Chloe Park", "Sr Designer, Revolut", "7 yrs", "LinkedIn", "interview"],
    ["Ravi Anand", "Product Designer, N26", "4 yrs", "Job board", "screen"],
    ["Elsa Berg", "UX Designer, Spotify", "5 yrs", "Inbound", "applied"],
    ["Marcus Webb", "Designer, Wise", "3 yrs", "CV corpus", "rejected"],
  ],
  "REQ-2050": [
    ["Jordan Blake", "AE, Salesforce", "5 yrs · SaaS", "Referral", "onsite"],
    ["Fatima Nasser", "AE, HubSpot", "4 yrs · SaaS", "LinkedIn", "interview"],
    ["Diego Torres", "Sr AE, Datadog", "6 yrs", "Agency", "interview"],
    ["Hannah Cole", "AE, Notion", "3 yrs", "Inbound", "screen"],
    ["Sam Whitfield", "SDR→AE, Gong", "3 yrs", "Job board", "applied"],
    ["Léa Dubois", "AE, Qonto", "5 yrs", "CV corpus", "applied"],
  ],
  "REQ-2038": [
    ["Grace Okoro", "Staff Eng, Shopify", "11 yrs", "Referral", "hired"],
    ["Victor Lang", "Principal, Elastic", "12 yrs", "LinkedIn", "rejected"],
    ["Mei Tan", "Staff Eng, GitLab", "10 yrs", "CV corpus", "rejected"],
  ],
};

function genCandidate(req, spec) {
  const [name, role, exp, source, stage] = spec;
  const rng = mulberry32(hashStr(req.id + ":" + name));
  const strength = 2.9 + rng() * 1.7; // "true" strength 2.9–4.6
  const fit = Math.round(58 + strength * 8 + rng() * 6); // ~80–99

  const cand = {
    id: req.id + "-" + name.toLowerCase().replace(/[^a-z]+/g, "."),
    reqId: req.id, name, initials: initialsOf(name), bg: AVATARS[hashStr(name) % AVATARS.length],
    currentRole: role, exp, source, fit,
    stage: stage === "rejected" ? "rejected" : stage,
    rejected: null,
    appliedAt: Date.now() - 86400000 * (5 + Math.floor(rng() * 25)),
    email: name.toLowerCase().replace(/[^a-z]+/g, ".") + "@example.com",
    summary: "",
    notes: [],
    scorecards: {}, // { stageId: { interviewerId: {ratings, rec, comment, submittedAt} } }
    offer: null,
    onboardingCaseId: null,
  };

  if (stage === "rejected") {
    cand.rejected = { reason: REJECTION_REASONS[hashStr(name) % REJECTION_REASONS.length], at: Date.now() - 86400000 * (3 + Math.floor(rng() * 10)) };
    // rejected candidates usually got at least an interview scorecard
    cand.stage = "rejected";
  }

  cand.summary = name.split(" ")[0] + " brings " + exp.split(" ")[0] + " years across " + (role.split(", ")[1] || "industry") + ". " +
    (fit >= 90 ? "Strong signal on the core competencies for this role." : fit >= 82 ? "Solid all-round profile with a couple of areas to probe." : "Promising but needs validation on depth.");

  // Seed scorecards for anyone who reached interview or beyond (incl. rejected who got that far).
  const reachedIdx = STAGE_ORDER.indexOf(stage === "rejected" ? "interview" : stage);
  req.interviewPlan.forEach((ip) => {
    const ipIdx = STAGE_ORDER.indexOf(ip.stageId);
    if (ipIdx > reachedIdx) return; // haven't reached this round yet
    // For a candidate currently at a scored stage, that round's cards may be partial.
    const isCurrentRound = ip.stageId === stage;
    const cards = {};
    ip.interviewerIds.forEach((iid, k) => {
      // leave the last interviewer of the current round pending, to show "in progress"
      if (isCurrentRound && k === ip.interviewerIds.length - 1 && rng() > 0.4) return;
      const r2 = mulberry32(hashStr(cand.id + ip.stageId + iid));
      const ratings = {};
      req.scorecard.forEach((aid) => { ratings[aid] = clamp5(strength + (r2() - 0.5) * 1.3); });
      const avg = req.scorecard.reduce((s, aid) => s + ratings[aid], 0) / req.scorecard.length;
      const rec = avg >= 4.2 ? "strong_yes" : avg >= 3.4 ? "yes" : avg >= 2.6 ? "no" : "strong_no";
      cards[iid] = {
        ratings, rec,
        comment: rec === "strong_yes" ? "Excellent signal — clears the bar comfortably." :
                 rec === "yes" ? "Solid across the board; a hire for this role." :
                 rec === "no" ? "Some gaps on the core attributes; leaning no." :
                 "Did not meet the bar on the fundamentals.",
        submittedAt: Date.now() - 86400000 * (2 + Math.floor(r2() * 6)),
      };
    });
    if (Object.keys(cards).length) cand.scorecards[ip.stageId] = cards;
  });

  // Offer object for offer/hired stages.
  if (stage === "offer" || stage === "hired") {
    const baseComp = req.dept === "Engineering" ? 145 : req.dept === "Revenue" ? 120 : req.dept === "Design" ? 115 : 95;
    cand.offer = {
      base: baseComp + Math.round(rng() * 20), bonus: 10 + Math.round(rng() * 10), equity: 0.02 + Math.round(rng() * 5) / 100,
      level: req.level, startDate: "2026-08-17",
      status: stage === "hired" ? "accepted" : "extended",
      approverId: "alex.vp", approvedAt: Date.now() - 86400000 * 4,
      extendedAt: Date.now() - 86400000 * 3,
      acceptedAt: stage === "hired" ? Date.now() - 86400000 * 1 : null,
    };
  }
  return cand;
}

function seedCands() {
  const reqs = seedReqs();
  const out = [];
  reqs.forEach((req) => { (CAND_SPEC[req.id] || []).forEach((spec) => out.push(genCandidate(req, spec))); });
  return out;
}

// ---- Persistence ----
export function allReqs() {
  try { const raw = localStorage.getItem(REQ_KEY); if (!raw) { const s = seedReqs(); localStorage.setItem(REQ_KEY, JSON.stringify(s)); return s; } return JSON.parse(raw); }
  catch (e) { return seedReqs(); }
}
function saveReqs(list) { localStorage.setItem(REQ_KEY, JSON.stringify(list)); }
export function getReq(id) { return allReqs().find((r) => r.id === id) || null; }
export function saveReq(req) {
  const list = allReqs(); const idx = list.findIndex((r) => r.id === req.id);
  if (idx >= 0) list[idx] = req; else list.unshift(req);
  saveReqs(list); return req;
}
let _reqn = 2053;
export function createReq() {
  const req = {
    id: "REQ-" + (_reqn++), title: "New requisition", dept: "Engineering", level: "Mid",
    location: "Remote", headcount: 1, status: "draft",
    ownerId: "david.okonkwo", recruiterId: "priya.nair",
    scorecard: ["problem", "comm", "ownership"],
    interviewPlan: [
      { stageId: "interview", interviewerIds: ["david.okonkwo"] },
      { stageId: "onsite", interviewerIds: ["david.okonkwo"] },
    ],
    approval: { status: "none", approverId: "alex.vp", at: null },
    createdAt: Date.now(), openedAt: null,
  };
  const list = allReqs(); list.unshift(req); saveReqs(list); return req;
}
export function deleteReq(id) { saveReqs(allReqs().filter((r) => r.id !== id)); saveCands(allCands().filter((c) => c.reqId !== id)); }
export function submitReqForApproval(id) { const l = allReqs(); const r = l.find((x) => x.id === id); if (!r) return; r.status = "pending_approval"; r.approval = { ...r.approval, status: "pending", at: null }; saveReqs(l); }
export function approveReq(id) { const l = allReqs(); const r = l.find((x) => x.id === id); if (!r) return; r.status = "open"; r.openedAt = Date.now(); r.approval = { ...r.approval, status: "approved", at: Date.now() }; saveReqs(l); }
export function closeReq(id, filled) { const l = allReqs(); const r = l.find((x) => x.id === id); if (!r) return; r.status = filled ? "filled" : "closed"; r.closedAt = Date.now(); saveReqs(l); }

// ---- Candidates ----
export function allCands() {
  try { const raw = localStorage.getItem(CAND_KEY); if (!raw) { const s = seedCands(); localStorage.setItem(CAND_KEY, JSON.stringify(s)); return s; } return JSON.parse(raw); }
  catch (e) { return seedCands(); }
}
function saveCands(list) { localStorage.setItem(CAND_KEY, JSON.stringify(list)); }
export function candidatesForReq(reqId) { return allCands().filter((c) => c.reqId === reqId); }
export function getCandidate(id) { return allCands().find((c) => c.id === id) || null; }
export function saveCandidate(cand) {
  const list = allCands(); const idx = list.findIndex((c) => c.id === cand.id);
  if (idx >= 0) list[idx] = cand; else list.push(cand);
  saveCands(list); return cand;
}
export function addCandidate(reqId, name, currentRole, source) {
  const req = getReq(reqId); if (!req) return null;
  const cand = {
    id: reqId + "-" + name.toLowerCase().replace(/[^a-z]+/g, ".") + "-" + Math.random().toString(36).slice(2, 5),
    reqId, name, initials: initialsOf(name) || "?", bg: AVATARS[hashStr(name + Date.now()) % AVATARS.length],
    currentRole: currentRole || "", exp: "", source: source || "Inbound", fit: 75,
    stage: "applied", rejected: null, appliedAt: Date.now(),
    email: name.toLowerCase().replace(/[^a-z]+/g, ".") + "@example.com",
    summary: "Added manually — awaiting screen.", notes: [], scorecards: {}, offer: null, onboardingCaseId: null,
  };
  return saveCandidate(cand);
}
export function advanceCandidate(id) {
  const c = getCandidate(id); if (!c || c.stage === "rejected") return null;
  const idx = STAGE_ORDER.indexOf(c.stage);
  c.stage = STAGE_ORDER[Math.min(STAGE_ORDER.length - 1, idx + 1)];
  return saveCandidate(c);
}
export function moveCandidate(id, stage) { const c = getCandidate(id); if (!c) return null; c.stage = stage; if (stage !== "rejected") c.rejected = null; return saveCandidate(c); }
export function rejectCandidate(id, reason) { const c = getCandidate(id); if (!c) return null; c.stage = "rejected"; c.rejected = { reason: reason || "Not a fit", at: Date.now() }; return saveCandidate(c); }
export function reopenCandidate(id, stage) { const c = getCandidate(id); if (!c) return null; c.stage = stage || "screen"; c.rejected = null; return saveCandidate(c); }
export function addNote(id, authorId, text) {
  const c = getCandidate(id); if (!c) return null;
  c.notes = [{ authorId, text, at: Date.now() }, ...(c.notes || [])];
  return saveCandidate(c);
}
export function submitScorecard(candId, stageId, interviewerId, ratings, rec, comment) {
  const c = getCandidate(candId); if (!c) return null;
  c.scorecards = { ...(c.scorecards || {}) };
  c.scorecards[stageId] = { ...(c.scorecards[stageId] || {}) };
  c.scorecards[stageId][interviewerId] = { ratings: { ...ratings }, rec, comment: comment || "", submittedAt: Date.now() };
  return saveCandidate(c);
}

// ---- Offers ----
export function makeOffer(candId, offer) {
  const c = getCandidate(candId); if (!c) return null;
  c.offer = { ...offer, status: "pending_approval", approverId: "alex.vp", createdAt: Date.now() };
  c.stage = "offer";
  return saveCandidate(c);
}
export function approveOffer(candId) { const c = getCandidate(candId); if (!c || !c.offer) return null; c.offer = { ...c.offer, status: "approved", approvedAt: Date.now() }; return saveCandidate(c); }
export function extendOffer(candId) { const c = getCandidate(candId); if (!c || !c.offer) return null; c.offer = { ...c.offer, status: "extended", extendedAt: Date.now() }; return saveCandidate(c); }
export function declineOffer(candId) { const c = getCandidate(candId); if (!c || !c.offer) return null; c.offer = { ...c.offer, status: "declined", declinedAt: Date.now() }; c.stage = "rejected"; c.rejected = { reason: "Offer declined", at: Date.now() }; return saveCandidate(c); }

// Accept an offer → mark hired AND start an onboarding case in the onboarding store.
const DEPT_TO_ROLE = { Engineering: "eng", Design: "design", Revenue: "sales" };
export async function acceptOffer(candId) {
  const c = getCandidate(candId); if (!c || !c.offer) return null;
  const req = getReq(c.reqId);
  c.offer = { ...c.offer, status: "accepted", acceptedAt: Date.now() };
  c.stage = "hired";
  saveCandidate(c);
  try {
    const ob = await import("./onboarding-store.js");
    const role = (req && DEPT_TO_ROLE[req.dept]) || "general";
    const kase = ob.startOnboarding({
      hireName: c.name,
      role,
      roleLabel: req ? req.title : "New hire",
      startDate: c.offer.startDate || "2026-08-17",
      manager: req ? person(req.ownerId).name : "—",
      email: c.name.toLowerCase().replace(/[^a-z]+/g, ".") + "@meridian.co",
    });
    c.onboardingCaseId = kase.id;
    saveCandidate(c);
    return kase;
  } catch (e) { return null; }
}

// ---- Roll-ups ----
export function funnelFor(reqId) {
  const cands = candidatesForReq(reqId);
  const counts = {}; STAGE_ORDER.forEach((s) => (counts[s] = 0)); counts.rejected = 0;
  cands.forEach((c) => { counts[c.stage] = (counts[c.stage] || 0) + 1; });
  // active-in-or-past-stage counts for a funnel (cumulative reach)
  const reach = {};
  STAGE_ORDER.forEach((s, i) => {
    reach[s] = cands.filter((c) => {
      if (c.stage === "rejected") return false;
      return STAGE_ORDER.indexOf(c.stage) >= i;
    }).length;
  });
  return { counts, reach, total: cands.length, active: cands.filter((c) => c.stage !== "rejected" && c.stage !== "hired").length };
}
export function reqSummary(req) {
  const f = funnelFor(req.id);
  return { active: f.active, total: f.total, hired: f.counts.hired || 0, offers: f.counts.offer || 0 };
}
// Debrief roll-up for a candidate: per-attribute averages + recommendation tally.
export function debriefFor(candId) {
  const c = getCandidate(candId); if (!c) return null;
  const req = getReq(c.reqId);
  const cards = [];
  Object.keys(c.scorecards || {}).forEach((sid) => {
    Object.keys(c.scorecards[sid]).forEach((iid) => cards.push({ stageId: sid, interviewerId: iid, ...c.scorecards[sid][iid] }));
  });
  const attrs = (req ? req.scorecard : []).map((aid) => {
    let sum = 0, n = 0;
    cards.forEach((cd) => { if (cd.ratings[aid] != null) { sum += cd.ratings[aid]; n++; } });
    return { id: aid, name: attr(aid).name, avg: n ? sum / n : null, n };
  });
  const recTally = {}; REC_ORDER.forEach((r) => (recTally[r] = 0));
  cards.forEach((cd) => { if (cd.rec) recTally[cd.rec] = (recTally[cd.rec] || 0) + 1; });
  const overall = cards.length ? cards.reduce((s, cd) => s + (RECS[cd.rec] ? RECS[cd.rec].val : 0), 0) / cards.length : 0;
  return { cards, attrs, recTally, count: cards.length, overall };
}
// Company-wide reports.
export function reportsAll() {
  const reqs = allReqs();
  const openReqs = reqs.filter((r) => r.status === "open");
  const cands = allCands();

  // Funnel conversion across open reqs (cumulative reach).
  const funnelCounts = {}; STAGE_ORDER.forEach((s) => (funnelCounts[s] = 0));
  cands.forEach((c) => {
    const r = reqs.find((x) => x.id === c.reqId);
    if (!r || (r.status !== "open" && r.status !== "filled")) return;
    if (c.stage === "rejected") { funnelCounts.applied += 1; return; }
    STAGE_ORDER.forEach((s, i) => { if (STAGE_ORDER.indexOf(c.stage) >= i) funnelCounts[s] += 1; });
  });

  // Source effectiveness: candidates per source + hires per source.
  const bySource = {};
  SOURCES.forEach((s) => (bySource[s] = { source: s, total: 0, hired: 0 }));
  cands.forEach((c) => { if (!bySource[c.source]) bySource[c.source] = { source: c.source, total: 0, hired: 0 }; bySource[c.source].total++; if (c.stage === "hired") bySource[c.source].hired++; });

  // Time-to-fill for filled reqs (open → close, days).
  const filled = reqs.filter((r) => r.status === "filled" && r.openedAt && r.closedAt)
    .map((r) => ({ id: r.id, title: r.title, days: Math.round((r.closedAt - r.openedAt) / 86400000) }));
  const avgTtf = filled.length ? Math.round(filled.reduce((s, f) => s + f.days, 0) / filled.length) : null;

  return {
    openCount: openReqs.length, totalReqs: reqs.length,
    totalCandidates: cands.length,
    funnelCounts,
    sources: Object.values(bySource).filter((s) => s.total > 0).sort((a, b) => b.total - a.total),
    filled, avgTtf,
    hires: cands.filter((c) => c.stage === "hired").length,
  };
}

export function resetRecruitment() { localStorage.removeItem(REQ_KEY); localStorage.removeItem(CAND_KEY); }
