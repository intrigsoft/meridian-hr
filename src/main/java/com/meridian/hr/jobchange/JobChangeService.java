package com.meridian.hr.jobchange;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.domain.JobChange;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job-change lifecycle ported from the fixture's {@code jobchange-store.js}. A manager
 * or HR raises a request with an effective date; HR approves. On approval the change
 * applies immediately (date ≤ today) or is scheduled and applied by {@link #applyDueChanges}
 * when the date arrives. Applying routes through {@link PeopleService#applyJobChange} so it
 * lands in the employee's job history. Operates on the calling device's {@link Workspace}.
 */
@Service
public class JobChangeService {

    private final SessionContext session;
    private final PeopleService people;

    public JobChangeService(SessionContext session, PeopleService people) {
        this.session = session;
        this.people = people;
    }

    private Workspace ws() {
        return session.workspace();
    }

    private static String today() {
        return LocalDate.now().toString();
    }

    // ---- reads ----

    public List<JobChange> all() {
        List<JobChange> list = new ArrayList<>(ws().jobChanges);
        list.sort(Comparator.comparingLong((JobChange r) -> r.createdAt).reversed());
        return list;
    }

    public JobChange get(String id) {
        for (JobChange r : ws().jobChanges) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    /** Requests visible to the actor (HR: all; manager: own reports), newest first. */
    public List<JobChange> forActor(Actor actor) {
        boolean hr = actor != null && actor.isHr();
        java.util.Set<String> reportIds = new java.util.HashSet<>();
        if (!hr && actor != null) {
            for (Employee r : people.directReports(actor.userId())) {
                reportIds.add(r.id);
            }
        }
        List<JobChange> out = new ArrayList<>();
        for (JobChange r : all()) {
            if (hr || reportIds.contains(r.empId)) out.add(r);
        }
        return out;
    }

    public record Summary(int pending, int scheduled, int applied, int total) {
    }

    public Summary summary() {
        int p = 0, s = 0, a = 0;
        for (JobChange r : ws().jobChanges) {
            switch (r.status) {
                case "pending" -> p++;
                case "scheduled" -> s++;
                case "applied" -> a++;
                default -> {
                }
            }
        }
        return new Summary(p, s, a, ws().jobChanges.size());
    }

    // ---- mutations ----

    public JobChange createRequest(String empId, String type, String effectiveDate,
                                   Map<String, String> changes, Map<String, String> fromSnapshot,
                                   String reason, String byId) {
        Employee emp = people.get(empId);
        JobChange r = new JobChange();
        r.id = "jc-" + Long.toString(System.currentTimeMillis(), 36) + ws().jobChanges.size();
        r.empId = empId;
        r.empName = emp != null ? emp.fullName() : empId;
        r.type = type;
        r.effectiveDate = effectiveDate == null || effectiveDate.isBlank() ? today() : effectiveDate;
        r.changes.putAll(changes);
        r.fromSnapshot.putAll(fromSnapshot);
        r.reason = reason == null ? "" : reason;
        r.requestedBy = byId;
        Employee by = people.get(byId);
        r.requestedByName = by != null ? by.fullName() : "HR";
        r.status = "pending";
        r.createdAt = System.currentTimeMillis();
        ws().jobChanges.add(r);
        return r;
    }

    public JobChange approve(String id, String byId) {
        JobChange r = get(id);
        if (r == null || !"pending".equals(r.status)) return r;
        r.decidedBy = byId;
        Employee by = people.get(byId);
        r.decidedByName = by != null ? by.fullName() : "HR";
        r.decidedAt = System.currentTimeMillis();
        if (r.effectiveDate.compareTo(today()) <= 0) {
            applyChange(r);
            r.status = "applied";
            r.appliedAt = System.currentTimeMillis();
        } else {
            r.status = "scheduled";
        }
        return r;
    }

    public void reject(String id, String byId) {
        JobChange r = get(id);
        if (r == null || !"pending".equals(r.status)) return;
        r.status = "rejected";
        r.decidedBy = byId;
        r.decidedAt = System.currentTimeMillis();
    }

    public void cancel(String id) {
        ws().jobChanges.removeIf(r -> r.id.equals(id));
    }

    /** Apply any scheduled changes whose effective date has arrived. Call on page load. */
    public void applyDueChanges() {
        String today = today();
        for (JobChange r : ws().jobChanges) {
            if ("scheduled".equals(r.status) && r.effectiveDate.compareTo(today) <= 0) {
                applyChange(r);
                r.status = "applied";
                r.appliedAt = System.currentTimeMillis();
            }
        }
    }

    private void applyChange(JobChange r) {
        people.applyJobChange(r.empId, r.changes, r.decidedByName != null ? r.decidedByName : "HR");
    }

    // ---- diff display ----

    public record DiffRow(String label, String from, String to) {
    }

    public List<DiffRow> diffRows(JobChange r) {
        List<DiffRow> rows = new ArrayList<>();
        for (Map.Entry<String, String> en : r.changes.entrySet()) {
            String f = en.getKey();
            rows.add(new DiffRow(JobChangeMeta.fieldLabel(f),
                    display(f, r.fromSnapshot.get(f)), display(f, en.getValue())));
        }
        return rows;
    }

    private String display(String field, String raw) {
        if (raw == null || raw.isBlank()) return "—";
        if ("managerId".equals(field)) {
            Employee m = people.get(raw);
            return m != null ? m.fullName() : "—";
        }
        if ("salary".equals(field)) {
            return PeopleService.formatSalary(PeopleService.parseSalary(raw), "USD");
        }
        return raw;
    }

    /** Active employees the actor may raise a change for (HR: all; manager: own reports). */
    public List<Employee> candidateEmployees(Actor actor) {
        boolean hr = actor != null && actor.isHr();
        List<Employee> pool = hr ? people.all() : new ArrayList<>(people.directReports(actor.userId()));
        List<Employee> out = new ArrayList<>();
        for (Employee e : pool) {
            if (e.status != EmployeeStatus.INACTIVE) out.add(e);
        }
        out.sort(Comparator.comparing(Employee::fullName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Current raw value of a field on an employee, for prefilling the change editor. */
    public String currentRaw(Employee e, String field) {
        if (e == null) return "";
        return switch (field) {
            case "title" -> nz(e.title);
            case "level" -> nz(e.level);
            case "dept" -> nz(e.dept);
            case "managerId" -> nz(e.managerId);
            case "salary" -> e.salary == null ? "" : String.valueOf(e.salary);
            case "band" -> nz(e.band);
            case "workMode" -> nz(e.workMode);
            case "location" -> nz(e.location);
            case "employmentType" -> nz(e.employmentType);
            default -> "";
        };
    }

    /** Current display value of a field (for the "Current: …" hint). */
    public String currentDisplay(Employee e, String field) {
        return display(field, currentRaw(e, field));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
