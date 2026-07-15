package com.meridian.hr.people;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.Employee.HistoryEvent;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.domain.OrgConfig;
import com.meridian.hr.domain.ProfileChange;
import com.meridian.hr.domain.Role;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Employee / org-core read + write logic, ported from the fixture's
 * {@code people-store.js}. Operates on the calling device's {@link com.meridian.hr.workspace.Workspace}
 * (resolved via {@link SessionContext}); every mutation edits that isolated copy in
 * place. Access is role-derived: HR sees & edits everyone and all comp; a manager
 * sees comp for their downstream org; an employee sees their own comp only.
 */
@Service
public class PeopleService {

    private final SessionContext session;

    public PeopleService(SessionContext session) {
        this.session = session;
    }

    private List<Employee> emps() {
        return session.workspace().employees;
    }

    private OrgConfig org() {
        return session.workspace().org;
    }

    // ---------------- reads ----------------

    public List<Employee> all() {
        List<Employee> list = new ArrayList<>(emps());
        list.sort(Comparator.comparing(Employee::fullName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Employee get(String id) {
        return session.workspace().employee(id);
    }

    public OrgConfig.Department deptMeta(String dept) {
        return org().deptMeta(dept);
    }

    public List<String> departmentNames() {
        return org().departments.stream().map(d -> d.id).toList();
    }

    public List<String> levels() {
        return org().levels;
    }

    public List<OrgConfig.Band> bands() {
        return org().bands;
    }

    public static final List<String> WORK_MODES = List.of("Remote", "Hybrid", "On-site");
    public static final List<String> EMPLOYMENT_TYPES = List.of("Full-time", "Part-time", "Contract");

    /** Apply a set of job-change field edits, recording a history event per changed field. */
    public void applyJobChange(String empId, java.util.Map<String, String> changes, String byName) {
        Employee e = get(empId);
        if (e == null) return;
        List<HistoryEvent> events = new ArrayList<>();
        for (java.util.Map.Entry<String, String> en : changes.entrySet()) {
            String f = en.getKey();
            String v = en.getValue();
            switch (f) {
                case "title" -> {
                    events.add(historyFor("job", "Title changed", e.title, v, byName));
                    e.title = v;
                }
                case "level" -> {
                    events.add(historyFor("promotion", "Level changed", e.level, v, byName));
                    e.level = v;
                }
                case "dept" -> {
                    events.add(historyFor("transfer", "Department changed", e.dept, v, byName));
                    e.dept = v;
                }
                case "employmentType" -> {
                    events.add(historyFor("job", "Employment type changed", e.employmentType, v, byName));
                    e.employmentType = v;
                }
                case "workMode" -> {
                    events.add(historyFor("job", "Work mode changed", e.workMode, v, byName));
                    e.workMode = v;
                }
                case "location" -> {
                    events.add(historyFor("transfer", "Location changed", e.location, v, byName));
                    e.location = v;
                }
                case "band" -> {
                    events.add(historyFor("comp", "Band changed", e.band, v, byName));
                    e.band = v;
                }
                case "managerId" -> {
                    String beforeN = e.managerId != null && get(e.managerId) != null ? get(e.managerId).fullName() : "—";
                    String afterN = !blank(v) && get(v) != null ? get(v).fullName() : "—";
                    events.add(historyFor("transfer", "Manager changed", beforeN, afterN, byName));
                    e.managerId = blank(v) ? null : v;
                }
                case "salary" -> {
                    Integer nv = parseSalary(v);
                    String before = formatSalary(e.salary, e.currency);
                    String after = formatSalary(nv, e.currency);
                    if (!before.equals(after)) events.add(event("comp", "Compensation changed", before + " → " + after, byName));
                    e.salary = nv;
                }
                default -> {
                }
            }
        }
        e.history.addAll(events);
    }

    private HistoryEvent historyFor(String type, String label, String before, String after, String by) {
        return event(type, label, dash(before) + " → " + dash(after), by);
    }

    public static Integer parseSalary(String s) {
        if (s == null) return null;
        String digits = s.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.valueOf(digits);
    }

    // ---------------- org relationships ----------------

    public Employee managerOf(String id) {
        Employee e = get(id);
        return e == null || e.managerId == null ? null : get(e.managerId);
    }

    public List<Employee> directReports(String id) {
        return emps().stream()
                .filter(e -> id != null && id.equals(e.managerId))
                .sorted(Comparator.comparing(Employee::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Total downstream (recursive) headcount under a person. */
    public int reportCount(String id) {
        int total = 0;
        for (Employee r : directReports(id)) {
            total += 1 + reportCount(r.id);
        }
        return total;
    }

    /** Roots = people with no manager, or whose manager isn't in the set. */
    public List<Employee> orgRoots() {
        return emps().stream()
                .filter(e -> e.managerId == null || get(e.managerId) == null)
                .sorted(Comparator.comparing(Employee::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Manager chain from the top down to (but excluding) the person. */
    public List<Employee> chainOfCommand(String id) {
        List<Employee> chain = new ArrayList<>();
        Employee cur = get(id);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (cur != null && cur.managerId != null && seen.add(cur.managerId)) {
            cur = get(cur.managerId);
            if (cur != null) chain.add(cur);
        }
        java.util.Collections.reverse(chain);
        return chain;
    }

    // ---------------- access control ----------------

    public boolean canViewComp(Actor actor, String empId) {
        if (actor == null) return false;
        if (actor.role() == Role.HR) return true;
        if (actor.userId().equals(empId)) return true;
        if (actor.role() == Role.MANAGER) return isDownstream(actor.userId(), empId);
        return false;
    }

    private boolean isDownstream(String managerId, String empId) {
        for (Employee r : directReports(managerId)) {
            if (r.id.equals(empId) || isDownstream(r.id, empId)) return true;
        }
        return false;
    }

    // ---------------- aggregate stats ----------------

    public Stats stats() {
        List<Employee> all = emps();
        List<DeptCount> byDept = new ArrayList<>();
        for (OrgConfig.Department d : org().departments) {
            long count = all.stream().filter(e -> d.id.equals(e.dept)).count();
            byDept.add(new DeptCount(d.id, d.color, d.tint, (int) count));
        }
        int active = (int) all.stream().filter(e -> e.status == EmployeeStatus.ACTIVE).count();
        int onboarding = (int) all.stream().filter(e -> e.status == EmployeeStatus.ONBOARDING).count();
        int onLeave = (int) all.stream().filter(e -> e.status == EmployeeStatus.LEAVE).count();
        double avgTenure = all.stream().mapToDouble(e -> tenureYears(e.startDate)).average().orElse(0);
        long teams = byDept.stream().filter(d -> d.count() > 0).count();
        return new Stats(all.size(), active, onboarding, onLeave, avgTenure, (int) teams, byDept);
    }

    // ---------------- mutations ----------------

    private static final String[] BG = {
            "#4a9d7a", "#c47f3f", "#6b7db5", "#4a9d9d", "#9a6ab5", "#3f7cc4",
            "#b58f4a", "#6ba58f", "#b56b8f", "#6b8fb5", "#7a6bb5"
    };

    /** Create a person record (routes into onboarding) and add it to the workspace. */
    public Employee add(NewEmployee f) {
        List<Employee> list = emps();
        String first = blankTo(f.first(), "New").trim();
        String last = blankTo(f.last(), "Hire").trim();
        String base = (first + "." + last).toLowerCase().replaceAll("[^a-z.]", "");
        String id = base;
        int n = 2;
        while (findIndex(id) >= 0) {
            id = base + n++;
        }
        Employee e = new Employee(
                id, first, last, BG[list.size() % BG.length],
                blankTo(f.title(), "New role"),
                blankTo(f.dept(), departmentNames().isEmpty() ? "—" : departmentNames().get(0)),
                blankTo(f.level(), "Mid"),
                Role.EMPLOYEE,
                blank(f.managerId()) ? null : f.managerId(),
                base + "@meridian.co", "",
                blankTo(f.location(), "Remote"),
                blankTo(f.workMode(), "Remote"),
                "Full-time",
                blankTo(f.startDate(), LocalDate.now().toString()),
                EmployeeStatus.ONBOARDING,
                f.salary(), "—");
        e.history.add(new HistoryEvent("ev-join-" + id, "join", "Joined Meridian",
                e.title + " · " + e.dept, e.startDate, epoch(e.startDate), "System"));
        list.add(e);
        return e;
    }

    /** Apply an employment edit, recording a job-history event per changed tracked field. */
    public Employee applyEdit(String id, EmployeeEdit edit, String byName) {
        Employee e = get(id);
        if (e == null) return null;
        List<HistoryEvent> events = new ArrayList<>();
        trackText(events, "job", "Title changed", e.title, edit.title(), byName);
        trackText(events, "promotion", "Level changed", e.level, edit.level(), byName);
        trackText(events, "transfer", "Department changed", e.dept, edit.dept(), byName);
        trackText(events, "job", "Employment type changed", e.employmentType, edit.employmentType(), byName);
        String before = formatSalary(e.salary, e.currency);
        String after = formatSalary(edit.salary(), e.currency);
        if (!before.equals(after)) {
            events.add(event("comp", "Compensation changed", before + " → " + after, byName));
        }
        // apply
        e.title = edit.title();
        e.level = edit.level();
        e.dept = edit.dept();
        e.employmentType = edit.employmentType();
        e.workMode = edit.workMode();
        e.location = edit.location();
        e.salary = edit.salary();
        e.history.addAll(events);
        return e;
    }

    /** Deactivate a leaving employee: set INACTIVE, stamp exit info, write an exit history event. */
    public void recordExit(String empId, String type, String typeLabel, String reason, String lastDay, String byName) {
        Employee e = get(empId);
        if (e == null) return;
        e.status = EmployeeStatus.INACTIVE;
        Employee.ExitInfo ex = new Employee.ExitInfo();
        ex.type = type;
        ex.typeLabel = typeLabel;
        ex.reason = reason;
        ex.lastDay = lastDay;
        ex.by = blankTo(byName, "HR");
        ex.completedAt = System.currentTimeMillis();
        e.exit = ex;
        String detail = dash(typeLabel) + (blank(lastDay) ? "" : " · last day " + formatDate(lastDay));
        e.history.add(event("exit", "Offboarded", detail, byName));
    }

    public Employee setStatus(String id, EmployeeStatus status, String byName) {
        Employee e = get(id);
        if (e == null || e.status == status) return e;
        String detail = e.status.label + " → " + status.label;
        e.status = status;
        e.history.add(event("status", "Status changed", detail, byName));
        return e;
    }

    // ---------------- history helpers ----------------

    private void trackText(List<HistoryEvent> out, String type, String label, String before, String after, String by) {
        if (after != null && !java.util.Objects.equals(before, after)) {
            out.add(event(type, label, dash(before) + " → " + dash(after), by));
        }
    }

    private HistoryEvent event(String type, String label, String detail, String by) {
        long at = System.currentTimeMillis();
        String eid = "ev-" + at + "-" + (int) (at % 1000);
        return new HistoryEvent(eid, type, label, detail,
                LocalDate.now().toString(), at, blankTo(by, "HR"));
    }

    public List<HistoryEvent> history(String id) {
        Employee e = get(id);
        if (e == null) return List.of();
        List<HistoryEvent> h = new ArrayList<>(e.history);
        h.sort(Comparator.comparingLong((HistoryEvent x) -> x.at).reversed());
        return h;
    }

    // ---------------- formatting (display) ----------------

    private static final java.time.format.DateTimeFormatter DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH);

    public static String formatDate(String iso) {
        if (blank(iso)) return "—";
        try {
            return LocalDate.parse(iso).format(DATE_FMT);
        } catch (RuntimeException ex) {
            return iso;
        }
    }

    public static String formatSalary(Integer n, String currency) {
        if (n == null) return "—";
        String sym = "USD".equals(currency) ? "$" : "";
        return sym + String.format(java.util.Locale.US, "%,d", n);
    }

    // instance aliases so templates can call ${people.date(..)} / ${people.salary(..)} / ${people.tenure(..)}
    public String date(String iso) {
        return formatDate(iso);
    }

    public String salary(Integer n, String currency) {
        return formatSalary(n, currency);
    }

    public String tenure(String startDate) {
        return tenureLabel(startDate);
    }

    public static String tenureLabel(String startDate) {
        double yrs = tenureYears(startDate);
        if (yrs < 1) {
            int mo = Math.max(1, (int) Math.round(yrs * 12));
            return mo + " mo";
        }
        return (Math.round(yrs * 10) / 10.0) + " yr";
    }

    private static double tenureYears(String startDate) {
        if (blank(startDate)) return 0;
        try {
            long days = ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.now());
            return days / 365.25;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static long epoch(String isoDate) {
        try {
            return LocalDate.parse(isoDate).atTime(9, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        } catch (RuntimeException ex) {
            return System.currentTimeMillis();
        }
    }

    private int findIndex(String id) {
        List<Employee> list = emps();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankTo(String s, String fallback) {
        return blank(s) ? fallback : s;
    }

    private static String dash(String s) {
        return blank(s) ? "—" : s;
    }

    // ================= self-service profile (Profile domain) =================

    /** Paths whose changes route through HR approval instead of applying directly. */
    public static final Set<String> SENSITIVE_PATHS = Set.of(
            "legalName", "bank.bankName", "bank.accountName", "bank.accountLast4",
            "bank.routingLast4", "taxIds.ssnLast4", "taxIds.nationalId");

    public boolean isSensitive(String path) {
        return SENSITIVE_PATHS.contains(path);
    }

    /** Read a dotted profile path (top-level string, or address/bank/taxIds sub-field). */
    public String getPath(Employee e, String path) {
        if (e == null || path == null) return "";
        return switch (path) {
            case "legalName" -> nz(e.legalName);
            case "preferredName" -> nz(e.preferredName);
            case "dob" -> nz(e.dob);
            case "gender" -> nz(e.gender);
            case "pronouns" -> nz(e.pronouns);
            case "personalEmail" -> nz(e.personalEmail);
            case "personalPhone" -> nz(e.personalPhone);
            case "address.line1" -> nz(e.address.line1);
            case "address.city" -> nz(e.address.city);
            case "address.state" -> nz(e.address.state);
            case "address.zip" -> nz(e.address.zip);
            case "bank.bankName" -> nz(e.bank.bankName);
            case "bank.accountName" -> nz(e.bank.accountName);
            case "bank.accountLast4" -> nz(e.bank.accountLast4);
            case "bank.routingLast4" -> nz(e.bank.routingLast4);
            case "taxIds.ssnLast4" -> nz(e.taxIds.ssnLast4);
            case "taxIds.nationalId" -> nz(e.taxIds.nationalId);
            default -> "";
        };
    }

    /** Write a dotted profile path directly (used for non-sensitive edits + on approval). */
    public void setPath(Employee e, String path, String v) {
        if (e == null || path == null) return;
        String value = v == null ? "" : v.trim();
        switch (path) {
            case "legalName" -> e.legalName = value;
            case "preferredName" -> e.preferredName = value;
            case "dob" -> e.dob = value;
            case "gender" -> e.gender = value;
            case "pronouns" -> e.pronouns = value;
            case "personalEmail" -> e.personalEmail = value;
            case "personalPhone" -> e.personalPhone = value;
            case "address.line1" -> e.address.line1 = value;
            case "address.city" -> e.address.city = value;
            case "address.state" -> e.address.state = value;
            case "address.zip" -> e.address.zip = value;
            case "bank.bankName" -> e.bank.bankName = value;
            case "bank.accountName" -> e.bank.accountName = value;
            case "bank.accountLast4" -> e.bank.accountLast4 = value;
            case "bank.routingLast4" -> e.bank.routingLast4 = value;
            case "taxIds.ssnLast4" -> e.taxIds.ssnLast4 = value;
            case "taxIds.nationalId" -> e.taxIds.nationalId = value;
            default -> {
            }
        }
    }

    // ---- completeness ----

    public record CompField(String path, String label) {
    }

    public static final List<CompField> COMPLETENESS_FIELDS = List.of(
            new CompField("legalName", "Legal name"), new CompField("dob", "Date of birth"),
            new CompField("gender", "Gender"), new CompField("pronouns", "Pronouns"),
            new CompField("personalEmail", "Personal email"), new CompField("personalPhone", "Personal phone"),
            new CompField("address.line1", "Home address"), new CompField("emergencyContacts", "Emergency contact"),
            new CompField("taxIds.ssnLast4", "Tax ID"), new CompField("bank.accountLast4", "Bank details"));

    public record Completeness(int pct, int done, int total, List<CompField> missing) {
    }

    public Completeness completeness(Employee e) {
        List<CompField> missing = new ArrayList<>();
        for (CompField f : COMPLETENESS_FIELDS) {
            boolean filled;
            if ("emergencyContacts".equals(f.path())) {
                filled = e.emergencyContacts != null && !e.emergencyContacts.isEmpty();
            } else {
                filled = !getPath(e, f.path()).isBlank();
            }
            if (!filled) missing.add(f);
        }
        int total = COMPLETENESS_FIELDS.size();
        int done = total - missing.size();
        return new Completeness((int) Math.round(done * 100.0 / total), done, total, missing);
    }

    // ---- change requests (sensitive fields) ----

    public ProfileChange requestChange(String empId, String path, String label, String oldV, String newV, String byId) {
        String id = "chg-" + System.currentTimeMillis() + "-" + session.workspace().profileChanges.size();
        ProfileChange c = new ProfileChange(id, empId, path, label, oldV, newV, byId);
        session.workspace().profileChanges.add(c);
        return c;
    }

    public List<ProfileChange> pendingChangesFor(String empId) {
        List<ProfileChange> out = new ArrayList<>();
        for (ProfileChange c : session.workspace().profileChanges) {
            if ("pending".equals(c.status) && (empId == null || empId.equals(c.empId))) out.add(c);
        }
        return out;
    }

    public boolean hasPendingChange(String empId, String path) {
        for (ProfileChange c : session.workspace().profileChanges) {
            if ("pending".equals(c.status) && c.empId.equals(empId) && c.path.equals(path)) return true;
        }
        return false;
    }

    public ProfileChange change(String id) {
        for (ProfileChange c : session.workspace().profileChanges) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public void approveChange(String id, String byId) {
        ProfileChange c = change(id);
        if (c == null || !"pending".equals(c.status)) return;
        setPath(get(c.empId), c.path, c.newValue);
        c.status = "approved";
        c.decidedBy = byId;
        c.decidedAt = System.currentTimeMillis();
    }

    public void rejectChange(String id, String byId) {
        ProfileChange c = change(id);
        if (c == null || !"pending".equals(c.status)) return;
        c.status = "rejected";
        c.decidedBy = byId;
        c.decidedAt = System.currentTimeMillis();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---------------- value types ----------------

    public record Stats(int headcount, int active, int onboarding, int onLeave,
                        double avgTenure, int teams, List<DeptCount> byDept) {
        public String avgTenureLabel() {
            return (Math.round(avgTenure * 10) / 10.0) + " yr";
        }
    }

    public record DeptCount(String id, String color, String tint, int count) {
    }

    /** Add-employee form payload. */
    public record NewEmployee(String first, String last, String title, String dept, String level,
                              String managerId, String location, String workMode, String startDate,
                              Integer salary) {
    }

    /** Employment-edit payload (the fields the Employee page edits in place). */
    public record EmployeeEdit(String title, String dept, String level, String employmentType,
                               String workMode, String location, Integer salary) {
    }
}
