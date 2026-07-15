// Shared "auth" for the Meridian HR fixture (stands in for the server session a
// Thymeleaf app would carry). The logged-in user drives nav scope AND is the
// identity Dioschub operates as / logs actions under.
export const USERS = {
  "sarah.chen": {
    id: "sarah.chen",
    name: "Sarah Chen",
    first: "Sarah",
    title: "Product Designer",
    dept: "Design",
    email: "sarah.chen@meridian.co",
    role: "employee",
    roleLabel: "Employee",
    initials: "SC",
    avatarBg: "#3f7cc4",
  },
  "david.okonkwo": {
    id: "david.okonkwo",
    name: "David Okonkwo",
    first: "David",
    title: "Engineering Manager",
    dept: "Engineering",
    email: "david.okonkwo@meridian.co",
    role: "manager",
    roleLabel: "Manager",
    initials: "DO",
    avatarBg: "#c47f3f",
  },
  "priya.nair": {
    id: "priya.nair",
    name: "Priya Nair",
    first: "Priya",
    title: "HR Business Partner",
    dept: "People Operations",
    email: "priya.nair@meridian.co",
    role: "hr",
    roleLabel: "HR Executive",
    initials: "PN",
    avatarBg: "#4a9d7a",
  },
};

const KEY = "meridian.session";

export function getSession() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const id = JSON.parse(raw).id;
    return USERS[id] || null;
  } catch (e) {
    return null;
  }
}

export function signIn(id) {
  if (!USERS[id]) return false;
  localStorage.setItem(KEY, JSON.stringify({ id, at: Date.now() }));
  return true;
}

export function signOut() {
  localStorage.removeItem(KEY);
}

// Redirect to login if no session. Returns the user or null.
export function requireSession() {
  const u = getSession();
  if (!u) {
    location.replace("Login.dc.html");
    return null;
  }
  return u;
}
