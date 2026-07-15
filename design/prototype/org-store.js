// Org-structure configuration store for the Dioschub HR fixture.
// HR-executive governance surface: the taxonomy the rest of the product hangs
// employees on — departments, career levels, and compensation bands. Seeds
// from the values people-store already uses so nothing drifts on first load,
// then persists edits to localStorage. Headcount + comp figures shown in the
// admin page are read live from people-store (single population), never stored
// here — this store owns the *definitions*, people-store owns the *people*.

const KEY = "meridian.org.v1";

function defaults() {
  return {
    departments: [
      { id: "Engineering",       color: "#3f7cc4", tint: "#e9f0f9", lead: "david.okonkwo" },
      { id: "Design",            color: "#9a6ab5", tint: "#f0ecf8", lead: "nadia.rahman" },
      { id: "Revenue",           color: "#4a9d7a", tint: "#e9f4ef", lead: "elena.vasquez" },
      { id: "Operations",        color: "#c68a2a", tint: "#faf3e6", lead: "marco.rossi" },
      { id: "People Operations", color: "#b56b8f", tint: "#f7ecf1", lead: "priya.nair" },
    ],
    levels: ["Junior", "Mid", "Senior", "Staff", "Lead", "Manager", "Executive"],
    bands: [
      { id: "IC1", track: "IC",       label: "Associate",           min: 72000,  max: 94000 },
      { id: "IC2", track: "IC",       label: "Engineer / Designer", min: 90000,  max: 118000 },
      { id: "IC3", track: "IC",       label: "Senior IC",           min: 112000, max: 145000 },
      { id: "IC4", track: "IC",       label: "Staff IC",            min: 140000, max: 178000 },
      { id: "IC5", track: "IC",       label: "Principal IC",        min: 170000, max: 212000 },
      { id: "M3",  track: "Manager",  label: "Manager",             min: 150000, max: 192000 },
      { id: "M4",  track: "Manager",  label: "Senior Manager",      min: 182000, max: 228000 },
      { id: "E1",  track: "Executive",label: "Director / VP",       min: 235000, max: 310000 },
    ],
  };
}

export const TRACKS = ["IC", "Manager", "Executive"];
export const DEPT_SWATCHES = [
  { color: "#3f7cc4", tint: "#e9f0f9" }, { color: "#9a6ab5", tint: "#f0ecf8" },
  { color: "#4a9d7a", tint: "#e9f4ef" }, { color: "#c68a2a", tint: "#faf3e6" },
  { color: "#b56b8f", tint: "#f7ecf1" }, { color: "#5a8fb5", tint: "#e8f1f7" },
  { color: "#c0563f", tint: "#fbece8" }, { color: "#6b7db5", tint: "#eceff8" },
];

export function getOrg() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) { const d = defaults(); localStorage.setItem(KEY, JSON.stringify(d)); return d; }
    return { ...defaults(), ...JSON.parse(raw) };
  } catch (e) { return defaults(); }
}
function save(o) { localStorage.setItem(KEY, JSON.stringify(o)); return o; }

// ---- Departments ----
export function getDepartments() { return getOrg().departments; }
export function deptMeta(id) {
  return getDepartments().find((d) => d.id === id) || { id, color: "#8894a3", tint: "#eef1f4" };
}
export function addDepartment(name) {
  const o = getOrg();
  const nm = (name || "New department").trim();
  if (o.departments.some((d) => d.id === nm)) return o;
  const sw = DEPT_SWATCHES[o.departments.length % DEPT_SWATCHES.length];
  o.departments.push({ id: nm, color: sw.color, tint: sw.tint, lead: null });
  return save(o);
}
export function updateDepartment(id, patch) {
  const o = getOrg();
  const i = o.departments.findIndex((d) => d.id === id);
  if (i >= 0) o.departments[i] = { ...o.departments[i], ...patch };
  return save(o);
}
export function removeDepartment(id) {
  const o = getOrg();
  o.departments = o.departments.filter((d) => d.id !== id);
  return save(o);
}

// ---- Levels ----
export function getLevels() { return getOrg().levels; }
export function addLevel(name) {
  const o = getOrg();
  const nm = (name || "").trim();
  if (!nm || o.levels.includes(nm)) return o;
  o.levels.push(nm);
  return save(o);
}
export function removeLevel(name) {
  const o = getOrg();
  o.levels = o.levels.filter((l) => l !== name);
  return save(o);
}
export function moveLevel(name, dir) {
  const o = getOrg();
  const i = o.levels.indexOf(name);
  const j = i + dir;
  if (i < 0 || j < 0 || j >= o.levels.length) return o;
  const tmp = o.levels[i]; o.levels[i] = o.levels[j]; o.levels[j] = tmp;
  return save(o);
}

// ---- Compensation bands ----
export function getBands() { return getOrg().bands; }
export function bandMeta(id) { return getBands().find((b) => b.id === id) || null; }
export function addBand(track) {
  const o = getOrg();
  let n = 1, id;
  const prefix = track === "Manager" ? "M" : track === "Executive" ? "E" : "IC";
  do { id = prefix + n; n++; } while (o.bands.some((b) => b.id === id));
  o.bands.push({ id, track: track || "IC", label: "New band", min: 80000, max: 110000 });
  return save(o);
}
export function updateBand(id, patch) {
  const o = getOrg();
  const i = o.bands.findIndex((b) => b.id === id);
  if (i >= 0) o.bands[i] = { ...o.bands[i], ...patch };
  return save(o);
}
export function removeBand(id) {
  const o = getOrg();
  o.bands = o.bands.filter((b) => b.id !== id);
  return save(o);
}

export function resetOrg() { localStorage.removeItem(KEY); }
