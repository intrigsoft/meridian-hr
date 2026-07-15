package com.meridian.hr.people;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * People Ops / Directory: the searchable employee list (grid / list / org-chart
 * views) and the 360° employee profile. HR can add people and edit employment +
 * status inline; comp is gated to HR and the person's own management chain.
 * Ported from the fixture's Directory + Employee screens.
 */
@Controller
public class DirectoryController {

    private final PeopleService people;
    private final SessionContext session;
    private final AccessPolicy policy;

    public DirectoryController(PeopleService people, SessionContext session, AccessPolicy policy) {
        this.people = people;
        this.session = session;
        this.policy = policy;
    }

    // ---------------- directory list ----------------

    @GetMapping("/directory")
    public String directory(@RequestParam(required = false) String q,
                            @RequestParam(required = false, defaultValue = "all") String dept,
                            @RequestParam(required = false, defaultValue = "grid") String view,
                            @RequestParam(required = false) String add,
                            Model model) {
        boolean isHr = policy.can(Permission.DIRECTORY_MANAGE);
        String query = q == null ? "" : q.trim();
        String needle = query.toLowerCase();

        List<PersonRow> rows = new ArrayList<>();
        for (Employee e : people.all()) {
            if (!"all".equals(dept) && !dept.equals(e.dept)) continue;
            if (!needle.isEmpty()) {
                String hay = (e.fullName() + " " + e.title + " " + e.email + " " + e.dept).toLowerCase();
                if (!hay.contains(needle)) continue;
            }
            rows.add(personRow(e));
        }

        PeopleService.Stats stats = people.stats();

        model.addAttribute("stats", stats);
        model.addAttribute("statTiles", statTiles(stats));
        model.addAttribute("deptChips", deptChips(stats, dept));
        model.addAttribute("query", query);
        model.addAttribute("dept", dept);
        model.addAttribute("view", view);
        model.addAttribute("rows", rows);
        model.addAttribute("orgRows", orgRows());
        model.addAttribute("isEmpty", !"org".equals(view) && rows.isEmpty());
        model.addAttribute("isHr", isHr);
        // add-employee modal (HR only)
        model.addAttribute("addOpen", isHr && add != null);
        model.addAttribute("deptOptions", people.departmentNames());
        model.addAttribute("levelOptions", people.levels());
        model.addAttribute("modeOptions", List.of("Remote", "Hybrid", "On-site"));
        model.addAttribute("managerOptions", managerOptions());
        model.addAttribute("active", "directory");
        model.addAttribute("noteTitle", stats.headcount() + " employees");
        return "directory";
    }

    @PostMapping("/directory/new")
    public String addEmployee(@RequestParam String first, @RequestParam String last,
                              @RequestParam(required = false) String title,
                              @RequestParam(required = false) String dept,
                              @RequestParam(required = false) String level,
                              @RequestParam(required = false) String managerId,
                              @RequestParam(required = false) String location,
                              @RequestParam(required = false) String workMode,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String salary,
                              RedirectAttributes ra) {
        if (!policy.can(Permission.DIRECTORY_MANAGE)) {
            return "redirect:/directory";
        }
        if (first == null || first.isBlank() || last == null || last.isBlank()) {
            ra.addFlashAttribute("toast", "First and last name are required.");
            ra.addFlashAttribute("toastDot", "#e0a13a");
            return "redirect:/directory?add";
        }
        Employee rec = people.add(new PeopleService.NewEmployee(
                first, last, title, dept, level, managerId, location, workMode, startDate, parseSalary(salary)));
        ra.addFlashAttribute("toast", rec.fullName() + " added — opening profile.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/directory/" + rec.id;
    }

    // ---------------- employee profile ----------------

    @GetMapping("/directory/{id}")
    public String employee(@PathVariable String id,
                           @RequestParam(required = false) String edit,
                           @RequestParam(required = false, defaultValue = "overview") String tab,
                           Model model) {
        Employee e = people.get(id);
        model.addAttribute("active", "directory");
        if (e == null) {
            model.addAttribute("notFound", true);
            model.addAttribute("noteTitle", "Not found");
            return "employee";
        }
        Actor actor = session.actor();
        boolean isHr = policy.can(Permission.DIRECTORY_MANAGE);
        boolean editing = isHr && edit != null;
        boolean canComp = people.canViewComp(actor, e.id);

        model.addAttribute("notFound", false);
        model.addAttribute("e", e);
        model.addAttribute("people", people);
        model.addAttribute("isHr", isHr);
        model.addAttribute("editing", editing);
        model.addAttribute("canComp", canComp);
        model.addAttribute("deptMeta", people.deptMeta(e.dept));
        model.addAttribute("salaryLabel", PeopleService.formatSalary(e.salary, e.currency));
        model.addAttribute("tenureLabel", PeopleService.tenureLabel(e.startDate));
        model.addAttribute("startDateLabel", PeopleService.formatDate(e.startDate));

        model.addAttribute("deptOptions", people.departmentNames());
        model.addAttribute("levelOptions", people.levels());
        model.addAttribute("typeOptions", List.of("Full-time", "Part-time", "Contract"));
        model.addAttribute("modeOptions", List.of("Remote", "Hybrid", "On-site"));

        // org
        model.addAttribute("chain", chainRows(e));
        model.addAttribute("reports", reportRows(e));

        // job history
        List<HistoryRow> hist = new ArrayList<>();
        for (Employee.HistoryEvent h : people.history(e.id)) {
            hist.add(new HistoryRow(h.label, h.detail, PeopleService.formatDate(h.date),
                    h.by == null ? "HR" : h.by, histColor(h.type)));
        }
        model.addAttribute("history", hist);

        model.addAttribute("tab", tab);
        model.addAttribute("statusOptions", EmployeeStatus.values());
        // HR: sensitive-field change requests awaiting approval on this person.
        model.addAttribute("pendingChanges", isHr ? people.pendingChangesFor(e.id) : java.util.List.of());
        model.addAttribute("noteTitle", e.fullName());
        model.addAttribute("noteSub", e.title + " · " + e.dept);
        return "employee";
    }

    @PostMapping("/directory/{id}/change/{cid}/approve")
    public String approveChange(@PathVariable String id, @PathVariable String cid, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (policy.can(Permission.PROFILE_APPROVE)) {
            people.approveChange(cid, actor.userId());
            ra.addFlashAttribute("toast", "Change approved and applied.");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
        }
        return "redirect:/directory/" + id;
    }

    @PostMapping("/directory/{id}/change/{cid}/reject")
    public String rejectChange(@PathVariable String id, @PathVariable String cid, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (policy.can(Permission.PROFILE_APPROVE)) {
            people.rejectChange(cid, actor.userId());
            ra.addFlashAttribute("toast", "Change rejected.");
            ra.addFlashAttribute("toastDot", "#8894a3");
        }
        return "redirect:/directory/" + id;
    }

    @PostMapping("/directory/{id}/edit")
    public String saveEmployee(@PathVariable String id,
                               @RequestParam String title, @RequestParam String dept,
                               @RequestParam String level, @RequestParam String employmentType,
                               @RequestParam String workMode, @RequestParam String location,
                               @RequestParam(required = false) String salary,
                               RedirectAttributes ra) {
        if (!policy.can(Permission.DIRECTORY_MANAGE)) {
            return "redirect:/directory/" + id;
        }
        Integer sal = parseSalary(salary);
        people.applyEdit(id, new PeopleService.EmployeeEdit(title, dept, level, employmentType,
                workMode, location, sal), actorName());
        ra.addFlashAttribute("toast", "Profile updated.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/directory/" + id;
    }

    @PostMapping("/directory/{id}/status")
    public String changeStatus(@PathVariable String id, @RequestParam String status, RedirectAttributes ra) {
        if (!policy.can(Permission.DIRECTORY_MANAGE)) {
            return "redirect:/directory/" + id;
        }
        EmployeeStatus s = EmployeeStatus.fromKey(status);
        people.setStatus(id, s, actorName());
        ra.addFlashAttribute("toast", "Status set to " + s.label + ".");
        ra.addFlashAttribute("toastDot", "#4a86d8");
        return "redirect:/directory/" + id;
    }

    // ---------------- view-model builders ----------------

    private PersonRow personRow(Employee e) {
        var dm = people.deptMeta(e.dept);
        Employee mgr = people.managerOf(e.id);
        return new PersonRow(e.id, e.fullName(), e.title, e.initials, e.avatarBg,
                e.dept, dm.color, dm.tint,
                e.status.label, e.status.pillBg, e.status.pillFg, e.status.dot,
                e.location, PeopleService.tenureLabel(e.startDate),
                mgr == null ? "—" : mgr.fullName());
    }

    private List<OrgRow> orgRows() {
        List<OrgRow> out = new ArrayList<>();
        for (Employee root : people.orgRoots()) {
            walk(root, 0, out);
        }
        return out;
    }

    private void walk(Employee e, int depth, List<OrgRow> out) {
        var dm = people.deptMeta(e.dept);
        int rc = people.directReports(e.id).size();
        out.add(new OrgRow(e.id, e.fullName(), e.title, e.dept, e.initials, e.avatarBg,
                dm.color, dm.tint, depth * 26, depth > 0,
                rc > 0, rc + (rc == 1 ? " report" : " reports")));
        for (Employee r : people.directReports(e.id)) {
            walk(r, depth + 1, out);
        }
    }

    private List<ChainRow> chainRows(Employee e) {
        List<ChainRow> out = new ArrayList<>();
        int i = 0;
        for (Employee c : people.chainOfCommand(e.id)) {
            out.add(new ChainRow(c.id, c.fullName(), c.title, c.initials, c.avatarBg, i * 18));
            i++;
        }
        return out;
    }

    private List<ReportRow> reportRows(Employee e) {
        List<ReportRow> out = new ArrayList<>();
        for (Employee r : people.directReports(e.id)) {
            out.add(new ReportRow(r.id, r.fullName(), r.title, r.initials, r.avatarBg, r.status.dot));
        }
        return out;
    }

    private List<StatTile> statTiles(PeopleService.Stats s) {
        return List.of(
                new StatTile("Headcount", String.valueOf(s.headcount()), "across " + s.teams() + " teams"),
                new StatTile("Active", String.valueOf(s.active()), "currently working"),
                new StatTile("Onboarding", String.valueOf(s.onboarding()), "ramping up"),
                new StatTile("On leave", String.valueOf(s.onLeave()), "away now"),
                new StatTile("Avg tenure", s.avgTenureLabel(), "company-wide"));
    }

    private List<DeptChip> deptChips(PeopleService.Stats s, String selected) {
        List<DeptChip> chips = new ArrayList<>();
        chips.add(new DeptChip("all", "All", s.headcount(), false, null, "all".equals(selected)));
        for (PeopleService.DeptCount d : s.byDept()) {
            chips.add(new DeptChip(d.id(), d.id(), d.count(), true, d.color(), d.id().equals(selected)));
        }
        return chips;
    }

    private List<ManagerOption> managerOptions() {
        List<ManagerOption> out = new ArrayList<>();
        for (Employee e : people.all()) {
            if (e.accessRole == com.meridian.hr.domain.Role.MANAGER
                    || e.accessRole == com.meridian.hr.domain.Role.HR) {
                out.add(new ManagerOption(e.id, e.fullName() + " · " + e.title));
            }
        }
        return out;
    }

    private String actorName() {
        Employee me = session.currentUser();
        return me == null ? "HR" : me.fullName();
    }

    private static Integer parseSalary(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.valueOf(digits);
    }

    private static String histColor(String type) {
        return switch (type == null ? "" : type) {
            case "join" -> "#2f6f4f";
            case "promotion" -> "#7a5aa8";
            case "transfer" -> "#3a5aa8";
            case "comp" -> "#9a6a1a";
            case "status" -> "#6b7480";
            case "job" -> "#3a4453";
            case "exit" -> "#b23b2e";
            default -> "#8894a3";
        };
    }

    // ---------------- view records ----------------

    public record PersonRow(String id, String name, String title, String initials, String avatarBg,
                            String dept, String deptColor, String deptTint,
                            String statusLabel, String statusBg, String statusFg, String statusDot,
                            String location, String tenure, String managerName) {
    }

    public record OrgRow(String id, String name, String title, String dept, String initials, String avatarBg,
                         String deptColor, String deptTint, int indent, boolean railed,
                         boolean hasReports, String reportLabel) {
    }

    public record ChainRow(String id, String name, String title, String initials, String avatarBg, int indent) {
    }

    public record ReportRow(String id, String name, String title, String initials, String avatarBg, String statusDot) {
    }

    public record HistoryRow(String label, String detail, String dateLabel, String by, String dot) {
    }

    public record StatTile(String label, String value, String sub) {
    }

    public record DeptChip(String id, String label, int count, boolean showDot, String dot, boolean on) {
    }

    public record ManagerOption(String id, String label) {
    }
}
