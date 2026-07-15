// Central configuration / policy store for the Meridian HR fixture.
// This is the single source of truth for the constraints that other modules
// enforce: public holidays, the work week, and leave policy (allowances,
// notice period, manager authority ceiling, blackout windows). HR edits these
// in Settings; time-store and the leave flows read from here so changes take
// effect across the app. Persists to localStorage.

const KEY = "meridian.config.v1";

function defaults() {
  return {
    workweek: { targetHours: 40, workingDays: ["mon", "tue", "wed", "thu", "fri"] },
    leave: {
      noticeDays: 7,        // minimum notice for annual leave
      ceilingDays: 10,      // manager approval authority ceiling (days); above → HR
      sickCertDays: 2,      // sick leave beyond this many days needs a certificate
      types: [
        { id: "annual",      label: "Annual leave",   allowance: 25, color: "#3f7cc4" },
        { id: "sick",        label: "Sick leave",     allowance: 10, color: "#5aa17f" },
        { id: "personal",    label: "Personal leave", allowance: 5,  color: "#c99b4e" },
        { id: "parental",    label: "Parental leave", allowance: 90, color: "#9b7fc4" },
        { id: "unpaid",      label: "Unpaid leave",   allowance: 0,  color: "#8894a3" },
        { id: "bereavement", label: "Bereavement",    allowance: 5,  color: "#b56b8f" },
      ],
      blackouts: [
        { id: "bo-yearend", label: "Year-end freeze", start: "2026-12-20", end: "2026-12-31", scope: "All teams" },
      ],
    },
    holidays: [
      { id: "h1", date: "2026-01-01", name: "New Year's Day" },
      { id: "h2", date: "2026-01-19", name: "MLK Day" },
      { id: "h3", date: "2026-05-25", name: "Memorial Day" },
      { id: "h4", date: "2026-07-03", name: "Independence Day (obs.)" },
      { id: "h5", date: "2026-09-07", name: "Labor Day" },
      { id: "h6", date: "2026-11-26", name: "Thanksgiving" },
      { id: "h7", date: "2026-11-27", name: "Day after Thanksgiving" },
      { id: "h8", date: "2026-12-24", name: "Christmas Eve" },
      { id: "h9", date: "2026-12-25", name: "Christmas Day" },
    ],
  };
}

export function getConfig() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) { const d = defaults(); localStorage.setItem(KEY, JSON.stringify(d)); return d; }
    // shallow-merge missing top-level keys so older saved configs still work
    return { ...defaults(), ...JSON.parse(raw) };
  } catch (e) { return defaults(); }
}
function save(cfg) { localStorage.setItem(KEY, JSON.stringify(cfg)); return cfg; }

// ---- Holidays ----
export function getHolidays() { return getConfig().holidays.slice().sort((a, b) => (a.date < b.date ? -1 : 1)); }
export function addHoliday(date, name) {
  const cfg = getConfig();
  cfg.holidays.push({ id: "h" + Date.now(), date, name: name || "Holiday" });
  return save(cfg);
}
export function updateHoliday(id, patch) {
  const cfg = getConfig();
  const i = cfg.holidays.findIndex((h) => h.id === id);
  if (i >= 0) cfg.holidays[i] = { ...cfg.holidays[i], ...patch };
  return save(cfg);
}
export function removeHoliday(id) {
  const cfg = getConfig();
  cfg.holidays = cfg.holidays.filter((h) => h.id !== id);
  return save(cfg);
}

// ---- Work week ----
export function getWorkweek() { return getConfig().workweek; }
export function setWorkweek(patch) {
  const cfg = getConfig();
  cfg.workweek = { ...cfg.workweek, ...patch };
  return save(cfg);
}

// ---- Leave policy ----
export function getLeave() { return getConfig().leave; }
export function setLeavePolicy(patch) {
  const cfg = getConfig();
  cfg.leave = { ...cfg.leave, ...patch };
  return save(cfg);
}
export function setLeaveTypeAllowance(id, allowance) {
  const cfg = getConfig();
  const t = cfg.leave.types.find((x) => x.id === id);
  if (t) t.allowance = Math.max(0, Number(allowance) || 0);
  return save(cfg);
}
export function leaveType(id) { return getLeave().types.find((t) => t.id === id) || null; }
export function allowanceFor(id) { const t = leaveType(id); return t ? t.allowance : 0; }

// ---- Blackouts ----
export function getBlackouts() { return getConfig().leave.blackouts || []; }
export function addBlackout(label, start, end, scope) {
  const cfg = getConfig();
  cfg.leave.blackouts = cfg.leave.blackouts || [];
  cfg.leave.blackouts.push({ id: "bo-" + Date.now(), label: label || "Blackout", start, end, scope: scope || "All teams" });
  return save(cfg);
}
export function removeBlackout(id) {
  const cfg = getConfig();
  cfg.leave.blackouts = (cfg.leave.blackouts || []).filter((b) => b.id !== id);
  return save(cfg);
}
// Does [start,end] overlap ANY configured blackout? Returns the blackout or null.
export function blackoutOverlap(startISO, endISO) {
  if (!startISO || !endISO) return null;
  return getBlackouts().find((b) => startISO <= b.end && endISO >= b.start) || null;
}

export function resetConfig() { localStorage.removeItem(KEY); }
