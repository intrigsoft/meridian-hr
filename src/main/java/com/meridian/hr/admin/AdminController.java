package com.meridian.hr.admin;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.JobChange;
import com.meridian.hr.domain.LeaveRequest;
import com.meridian.hr.domain.OffboardingCase;
import com.meridian.hr.domain.OrgConfig;
import com.meridian.hr.domain.PolicyConfig;
import com.meridian.hr.domain.ProfileChange;
import com.meridian.hr.domain.Role;
import com.meridian.hr.jobchange.JobChangeMeta;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.performance.PerformanceMeta;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.security.RolePermissions;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin surfaces (HR only, read-only in the sample): Settings (policy config), Org Structure
 * (departments / levels / comp bands), Roles &amp; Access (who holds which access role), and a
 * unified Audit Log synthesized from every domain's event trail. Editing these is deferred.
 */
@Controller
public class AdminController {

    private final PeopleService people;
    private final SessionContext session;
    private final AccessPolicy policy;

    public AdminController(PeopleService people, SessionContext session, AccessPolicy policy) {
        this.people = people;
        this.session = session;
        this.policy = policy;
    }

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private boolean guard(Model model, String active) {
        Permission required = switch (active) {
            case "org" -> Permission.ADMIN_ORG;
            case "roles" -> Permission.ADMIN_ROLES;
            case "audit" -> Permission.AUDIT_VIEW;
            default -> Permission.ADMIN_SETTINGS;
        };
        boolean hr = policy.can(required);
        model.addAttribute("active", active);
        model.addAttribute("isHr", hr);
        return hr;
    }

    // ===================== settings =====================

    @GetMapping("/settings")
    public String settings(Model model) {
        if (!guard(model, "settings")) return "admin/settings";
        PolicyConfig p = session.workspace().policy;
        model.addAttribute("policy", p);
        model.addAttribute("workingDays", String.join(", ", p.workingDays));
        model.addAttribute("leaveTypes", p.leaveTypes);
        model.addAttribute("blackouts", p.blackouts);
        model.addAttribute("holidays", p.holidaysSorted());
        model.addAttribute("competencies", PerformanceMeta.library());
        model.addAttribute("changeTypes", JobChangeMeta.types());
        return "admin/settings";
    }

    // ===================== org structure =====================

    @GetMapping("/org")
    public String org(Model model) {
        if (!guard(model, "org")) return "admin/org";
        OrgConfig org = session.workspace().org;
        List<DeptRow> depts = new ArrayList<>();
        for (OrgConfig.Department d : org.departments) {
            Employee lead = d.lead == null ? null : people.get(d.lead);
            int count = 0;
            for (Employee e : people.all()) {
                if (d.id.equals(e.dept)) count++;
            }
            depts.add(new DeptRow(d.id, d.color, d.tint, lead == null ? "—" : lead.fullName(), count));
        }
        model.addAttribute("depts", depts);
        model.addAttribute("levels", org.levels);
        model.addAttribute("bands", org.bands);
        return "admin/org";
    }

    // ===================== roles & access =====================

    @GetMapping("/roles")
    public String roles(Model model) {
        if (!guard(model, "roles")) return "admin/roles";
        List<RoleGroup> groups = new ArrayList<>();
        for (Role r : List.of(Role.HR, Role.MANAGER, Role.EMPLOYEE)) {
            List<UserRow> users = new ArrayList<>();
            for (Employee e : people.all()) {
                if (e.accessRole == r) {
                    users.add(new UserRow(e.id, e.fullName(), e.initials, e.avatarBg, e.title, e.dept));
                }
            }
            groups.add(new RoleGroup(r.label, roleBlurb(r), users.size(), users));
        }
        model.addAttribute("roleGroups", groups);
        model.addAttribute("permGroups", permissionMatrix());
        model.addAttribute("roleCols", List.of(Role.EMPLOYEE.label, Role.MANAGER.label, Role.HR.label));
        return "admin/roles";
    }

    /** The RBAC catalog as a matrix: permissions grouped by area, each showing which roles hold it. */
    private static List<PermGroup> permissionMatrix() {
        Map<String, List<PermRow>> byGroup = new LinkedHashMap<>();
        for (Permission p : Permission.values()) {
            byGroup.computeIfAbsent(p.group(), k -> new ArrayList<>()).add(new PermRow(
                    p.label(),
                    RolePermissions.has(Role.EMPLOYEE, p),
                    RolePermissions.has(Role.MANAGER, p),
                    RolePermissions.has(Role.HR, p)));
        }
        List<PermGroup> out = new ArrayList<>();
        byGroup.forEach((group, rows) -> out.add(new PermGroup(group, rows)));
        return out;
    }

    private static String roleBlurb(Role r) {
        return switch (r) {
            case HR -> "Full access: everyone's records, comp, approvals, and all admin settings.";
            case MANAGER -> "Team access: direct reports' records + comp, plus approvals for their org.";
            default -> "Self-service: own profile, leave, time, and reviews.";
        };
    }

    // ===================== audit log =====================

    @GetMapping("/audit")
    public String audit(Model model) {
        if (!guard(model, "audit")) return "admin/audit";
        Workspace ws = session.workspace();
        List<Entry> entries = new ArrayList<>();

        for (Employee e : ws.employees) {
            for (Employee.HistoryEvent h : e.history) {
                entries.add(new Entry(h.at, h.by, h.label + " — " + e.fullName(), h.detail,
                        cat(h.type), catColor(h.type), catBg(h.type)));
            }
        }
        for (LeaveRequest r : ws.leaveRequests) {
            for (LeaveRequest.Event ev : r.events) {
                entries.add(new Entry(ev.at, ev.actorName, "Leave " + ev.kind + " — " + r.empName,
                        ev.note == null ? "" : ev.note, "Leave", "#2f6aa8", "#e9f0f9"));
            }
        }
        for (ProfileChange c : ws.profileChanges) {
            long at = c.decidedAt != 0 ? c.decidedAt : c.requestedAt;
            entries.add(new Entry(at, c.requestedBy, "Profile change (" + c.status + ") — " + c.label,
                    c.oldValue + " → " + c.newValue, "Profile", "#7a5aa8", "#f0ecf8"));
        }
        for (JobChange j : ws.jobChanges) {
            if (j.decidedAt != 0) {
                entries.add(new Entry(j.decidedAt, j.decidedByName, "Job change (" + j.status + ") — " + j.empName,
                        j.type, "Job change", "#9a6a1a", "#f7f1e0"));
            }
        }
        for (OffboardingCase o : ws.offboardingCases) {
            if ("completed".equals(o.status)) {
                entries.add(new Entry(o.completedAt, "HR", "Offboarded — " + o.empName, o.type, "Exit", "#b23b2e", "#fbeae8"));
            }
        }

        entries.sort(Comparator.comparingLong((Entry e) -> e.at).reversed());
        List<AuditRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(entries.size(), 80); i++) {
            Entry e = entries.get(i);
            rows.add(new AuditRow(e.at == 0 ? "—" : STAMP.format(Instant.ofEpochMilli(e.at)),
                    e.actor == null ? "System" : e.actor, e.action, e.detail, e.category, e.color, e.bg));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("total", entries.size());
        return "admin/audit";
    }

    private static String cat(String type) {
        return switch (type) {
            case "promotion", "comp" -> "Comp/level";
            case "transfer" -> "Transfer";
            case "status", "exit" -> "Status";
            case "join" -> "Hire";
            default -> "Job";
        };
    }

    private static String catColor(String type) {
        return switch (type) {
            case "promotion", "comp" -> "#9a6a1a";
            case "transfer" -> "#3a5aa8";
            case "status", "exit" -> "#b23b2e";
            case "join" -> "#2f6f4f";
            default -> "#5a6472";
        };
    }

    private static String catBg(String type) {
        return switch (type) {
            case "promotion", "comp" -> "#f7f1e0";
            case "transfer" -> "#e8eefb";
            case "status", "exit" -> "#fbeae8";
            case "join" -> "#e6f3ec";
            default -> "#eef1f4";
        };
    }

    private record Entry(long at, String actor, String action, String detail, String category, String color, String bg) {
    }

    // ---- view records ----

    public record DeptRow(String name, String color, String tint, String lead, int count) {
    }

    public record RoleGroup(String label, String blurb, int count, List<UserRow> users) {
    }

    public record PermGroup(String group, List<PermRow> rows) {
    }

    public record PermRow(String label, boolean employee, boolean manager, boolean hr) {
    }

    public record UserRow(String id, String name, String initials, String avatarBg, String title, String dept) {
    }

    public record AuditRow(String when, String actor, String action, String detail, String category, String color, String bg) {
    }
}
