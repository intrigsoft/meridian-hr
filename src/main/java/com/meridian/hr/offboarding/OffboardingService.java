package com.meridian.hr.offboarding;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.domain.OffboardingCase;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exit-lifecycle logic ported from the fixture's {@code offboarding-store.js}. An exit
 * case runs a checklist; completing it delegates to {@link PeopleService#recordExit} to
 * mark the person inactive + write exit history. HR sees every case; a manager sees only
 * their direct reports'. Operates on the calling device's {@link Workspace}.
 */
@Service
public class OffboardingService {

    private final SessionContext session;
    private final PeopleService people;

    public OffboardingService(SessionContext session, PeopleService people) {
        this.session = session;
        this.people = people;
    }

    private Workspace ws() {
        return session.workspace();
    }

    // ---- exit-type metadata (mirrors EXIT_TYPES) ----

    public record ExitType(String id, String label, String color, String bg) {
    }

    private static final List<ExitType> EXIT_TYPES = List.of(
            new ExitType("resignation", "Resignation", "#3a5aa8", "#e8eefb"),
            new ExitType("termination", "Termination", "#b23b2e", "#fbe9e7"),
            new ExitType("end_contract", "End of contract", "#9a6a1a", "#f7f1e0"),
            new ExitType("retirement", "Retirement", "#2f6f4f", "#e6f3ec"));

    public List<ExitType> exitTypes() {
        return EXIT_TYPES;
    }

    public ExitType exitType(String id) {
        for (ExitType t : EXIT_TYPES) {
            if (t.id().equals(id)) return t;
        }
        return EXIT_TYPES.get(0);
    }

    // ---- reads ----

    public List<OffboardingCase> cases() {
        return ws().offboardingCases;
    }

    public OffboardingCase getCase(String id) {
        if (id == null) return null;
        for (OffboardingCase c : cases()) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    /** Cases visible to the actor (HR: all; manager: own reports), newest first. */
    public List<OffboardingCase> forActor(Actor actor) {
        boolean hr = actor != null && actor.isHr();
        java.util.Set<String> reportIds = new java.util.HashSet<>();
        if (!hr && actor != null) {
            for (Employee r : people.directReports(actor.userId())) {
                reportIds.add(r.id);
            }
        }
        List<OffboardingCase> out = new ArrayList<>();
        for (OffboardingCase c : cases()) {
            if (hr || reportIds.contains(c.empId)) out.add(c);
        }
        out.sort(Comparator.comparingLong((OffboardingCase c) -> c.initiatedAt).reversed());
        return out;
    }

    public record Summary(int total, int active, int completed) {
    }

    public Summary summary() {
        int active = 0;
        for (OffboardingCase c : cases()) {
            if ("in_progress".equals(c.status)) active++;
        }
        return new Summary(cases().size(), active, cases().size() - active);
    }

    public record Progress(int done, int total, int pct) {
    }

    public Progress progressOf(OffboardingCase c) {
        int total = c.checklist.size();
        int done = (int) c.checklist.stream().filter(t -> t.done).count();
        return new Progress(done, total, total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
    }

    /** Active employees the actor may offboard, excluding anyone with an open case. */
    public List<Employee> candidateEmployees(Actor actor) {
        boolean hr = actor != null && actor.isHr();
        java.util.Set<String> openIds = new java.util.HashSet<>();
        for (OffboardingCase c : cases()) {
            if (!"completed".equals(c.status)) openIds.add(c.empId);
        }
        List<Employee> pool = hr ? people.all() : new ArrayList<>(people.directReports(actor.userId()));
        List<Employee> out = new ArrayList<>();
        for (Employee e : pool) {
            if (e.status != EmployeeStatus.INACTIVE && !openIds.contains(e.id)) out.add(e);
        }
        out.sort(Comparator.comparing(Employee::fullName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ---- mutations ----

    public OffboardingCase start(String empId, String type, String lastDay, String reason, String byId) {
        for (OffboardingCase c : cases()) {
            if (c.empId.equals(empId) && !"completed".equals(c.status)) return c; // already open
        }
        Employee e = people.get(empId);
        OffboardingCase c = new OffboardingCase();
        c.id = "off-" + Long.toString(System.currentTimeMillis(), 36) + cases().size();
        c.empId = empId;
        c.empName = e != null ? e.fullName() : empId;
        c.dept = e != null ? e.dept : "";
        c.title = e != null ? e.title : "";
        c.type = type == null ? "resignation" : type;
        c.lastDay = lastDay == null ? "" : lastDay;
        c.reason = reason == null ? "" : reason;
        c.initiatedBy = byId;
        c.initiatedAt = System.currentTimeMillis();
        c.checklist.addAll(OffboardingCase.defaultChecklist());
        cases().add(c);
        return c;
    }

    public void toggleTask(String caseId, String taskId) {
        OffboardingCase c = getCase(caseId);
        if (c == null || !"in_progress".equals(c.status)) return;
        OffboardingCase.Task t = c.task(taskId);
        if (t != null) t.done = !t.done;
    }

    public boolean complete(String caseId, String byName) {
        OffboardingCase c = getCase(caseId);
        if (c == null || !"in_progress".equals(c.status)) return false;
        if (progressOf(c).done() != c.checklist.size()) return false; // guard: all tasks done
        ExitType t = exitType(c.type);
        people.recordExit(c.empId, c.type, t.label(), c.reason, c.lastDay, byName);
        c.status = "completed";
        c.completedAt = System.currentTimeMillis();
        return true;
    }

    public void cancel(String caseId) {
        cases().removeIf(c -> c.id.equals(caseId));
    }
}
