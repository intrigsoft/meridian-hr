package com.meridian.hr.jobchange;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.JobChange;
import com.meridian.hr.domain.OrgConfig;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * People Ops → Job changes. Managers and HR raise promotion/transfer/comp requests with
 * an effective date; HR approves (applies now or schedules). The "new request" modal is a
 * GET-recompute form — selecting an employee + change type re-renders the right field
 * editors server-side (like New Leave / New Onboarding). Ports {@code Job Changes.dc.html}.
 */
@Controller
public class JobChangeController {

    private final JobChangeService jobChanges;
    private final PeopleService people;
    private final SessionContext session;

    public JobChangeController(JobChangeService jobChanges, PeopleService people, SessionContext session) {
        this.jobChanges = jobChanges;
        this.people = people;
        this.session = session;
    }

    @GetMapping("/job-changes")
    public String index(@RequestParam(required = false) String add,
                        @RequestParam(required = false) String emp,
                        @RequestParam(defaultValue = "promotion") String type,
                        @RequestParam(required = false) String eff,
                        @RequestParam(required = false) String reason,
                        @RequestParam Map<String, String> all,
                        Model model) {
        jobChanges.applyDueChanges(); // apply any now-due scheduled changes

        Actor actor = session.actor();
        boolean allowed = actor != null && actor.isApprover();
        boolean hr = actor != null && actor.isHr();

        model.addAttribute("active", "jobchanges");
        model.addAttribute("allowed", allowed);
        model.addAttribute("restricted", !allowed);

        JobChangeService.Summary sum = jobChanges.summary();
        model.addAttribute("noteTitle", sum.pending() + " pending");
        model.addAttribute("noteSub", sum.scheduled() + " scheduled · " + sum.applied() + " applied");

        if (!allowed) {
            return "jobchange/job-changes";
        }

        model.addAttribute("stats", List.of(
                new Stat("Pending approval", sum.pending(), "#9a6a1a"),
                new Stat("Scheduled", sum.scheduled(), "#3a5aa8"),
                new Stat("Applied", sum.applied(), "#2f6f4f")));

        List<RequestView> views = new ArrayList<>();
        for (JobChange r : jobChanges.forActor(actor)) {
            views.add(toView(r, actor, hr));
        }
        model.addAttribute("requests", views);
        model.addAttribute("noRequests", views.isEmpty());

        // ---- new-request modal ----
        boolean addOpen = "true".equals(add) || (emp != null && !emp.isBlank());
        model.addAttribute("modalOpen", addOpen);
        List<EmpOption> opts = new ArrayList<>();
        for (Employee e : jobChanges.candidateEmployees(actor)) {
            opts.add(new EmpOption(e.id, e.fullName() + " · " + e.title));
        }
        model.addAttribute("empOptions", opts);
        model.addAttribute("typeOptions", JobChangeMeta.types());
        model.addAttribute("formEmp", emp == null ? "" : emp);
        model.addAttribute("formType", type);
        String effDate = eff == null || eff.isBlank() ? LocalDate.now().plusDays(14).toString() : eff;
        model.addAttribute("formEff", effDate);
        model.addAttribute("formReason", reason == null ? "" : reason);
        model.addAttribute("effHint", effDate.compareTo(LocalDate.now().toString()) <= 0
                ? "Applies immediately on approval." : "Scheduled — applies automatically on this date.");

        Employee target = emp == null || emp.isBlank() ? null : people.get(emp);
        model.addAttribute("hasEmp", target != null);
        if (target != null) {
            model.addAttribute("editFields", editFields(target, type, all));
        } else {
            model.addAttribute("editFields", List.of());
        }
        return "jobchange/job-changes";
    }

    @PostMapping("/job-changes/new")
    public String create(@RequestParam(required = false) String emp,
                         @RequestParam(defaultValue = "promotion") String type,
                         @RequestParam(required = false) String eff,
                         @RequestParam(required = false) String reason,
                         @RequestParam Map<String, String> all,
                         RedirectAttributes ra) {
        Actor actor = session.actor();
        if (actor == null || !actor.isApprover()) return "redirect:/job-changes";
        String empId = emp;
        Employee target = people.get(empId);
        if (target == null || eff == null || eff.isBlank()) {
            ra.addFlashAttribute("toast", "Pick an employee and an effective date.");
            ra.addFlashAttribute("toastDot", "#c98a2a");
            return "redirect:/job-changes?add=true";
        }
        JobChangeMeta.ChangeType ct = JobChangeMeta.type(type);
        Map<String, String> changes = new LinkedHashMap<>();
        Map<String, String> from = new LinkedHashMap<>();
        for (String field : ct.fields()) {
            String raw = all.get("f_" + field);
            if (raw == null || raw.isBlank()) continue;
            String cur = jobChanges.currentRaw(target, field);
            String normNew = "salary".equals(field) ? digits(raw) : raw.trim();
            String normCur = "salary".equals(field) ? digits(cur) : cur;
            if (!normNew.equals(normCur)) {
                changes.put(field, normNew);
                from.put(field, cur);
            }
        }
        if (changes.isEmpty()) {
            ra.addFlashAttribute("toast", "No changes from the current record.");
            ra.addFlashAttribute("toastDot", "#8894a3");
            return "redirect:/job-changes?add=true&emp=" + empId + "&type=" + type;
        }
        jobChanges.createRequest(empId, type, eff, changes, from, reason, actor.userId());
        ra.addFlashAttribute("toast", "Request submitted for approval.");
        ra.addFlashAttribute("toastDot", "#e0a13a");
        return "redirect:/job-changes";
    }

    @PostMapping("/job-changes/{id}/approve")
    public String approve(@PathVariable String id, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (actor == null || !actor.isHr()) return "redirect:/job-changes";
        JobChange r = jobChanges.approve(id, actor.userId());
        if (r != null) {
            ra.addFlashAttribute("toast", ("applied".equals(r.status) ? "Approved & applied " : "Approved (scheduled) ")
                    + r.empName + "'s change.");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
        }
        return "redirect:/job-changes";
    }

    @PostMapping("/job-changes/{id}/reject")
    public String reject(@PathVariable String id, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (actor == null || !actor.isHr()) return "redirect:/job-changes";
        JobChange r = jobChanges.get(id);
        jobChanges.reject(id, actor.userId());
        if (r != null) {
            ra.addFlashAttribute("toast", "Rejected " + r.empName + "'s change.");
            ra.addFlashAttribute("toastDot", "#8894a3");
        }
        return "redirect:/job-changes";
    }

    @PostMapping("/job-changes/{id}/cancel")
    public String cancel(@PathVariable String id, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (actor == null || !actor.isApprover()) return "redirect:/job-changes";
        jobChanges.cancel(id);
        ra.addFlashAttribute("toast", "Request withdrawn.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return "redirect:/job-changes";
    }

    // ---- view building ----

    private RequestView toView(JobChange r, Actor actor, boolean hr) {
        Employee e = people.get(r.empId);
        JobChangeMeta.ChangeType t = JobChangeMeta.type(r.type);
        JobChangeMeta.StatusMeta st = JobChangeMeta.status(r.status);
        boolean pending = "pending".equals(r.status);
        boolean canDecide = hr && pending;
        boolean canWithdraw = pending && (hr || r.requestedBy != null && r.requestedBy.equals(actor.userId()));
        return new RequestView(r.id, r.empName,
                e != null ? e.initials : "?", e != null ? e.avatarBg : "#c7cdd6",
                t.label(), t.bg(), t.color(), st.label(), st.pillBg(), st.pillFg(),
                PeopleService.formatDate(r.effectiveDate), r.requestedByName != null ? r.requestedByName : "HR",
                jobChanges.diffRows(r), r.reason != null && !r.reason.isBlank(), r.reason,
                canWithdraw, canDecide, "scheduled".equals(r.status), "applied".equals(r.status));
    }

    private List<EditField> editFields(Employee target, String type, Map<String, String> all) {
        JobChangeMeta.ChangeType ct = JobChangeMeta.type(type);
        List<EditField> out = new ArrayList<>();
        for (String field : ct.fields()) {
            boolean isSelect = JobChangeMeta.isSelect(field);
            String pending = all.get("f_" + field);
            String value = pending != null ? pending : (isSelect ? jobChanges.currentRaw(target, field) : "");
            out.add(new EditField(field, "f_" + field, JobChangeMeta.fieldLabel(field), isSelect,
                    options(field, target), value, "Current: " + jobChanges.currentDisplay(target, field)));
        }
        return out;
    }

    private List<Option> options(String field, Employee target) {
        List<Option> out = new ArrayList<>();
        switch (field) {
            case "level" -> people.levels().forEach(l -> out.add(new Option(l, l)));
            case "dept" -> people.departmentNames().forEach(d -> out.add(new Option(d, d)));
            case "band" -> {
                for (OrgConfig.Band b : people.bands()) out.add(new Option(b.id, b.id + " · " + b.label));
            }
            case "workMode" -> PeopleService.WORK_MODES.forEach(m -> out.add(new Option(m, m)));
            case "employmentType" -> PeopleService.EMPLOYMENT_TYPES.forEach(m -> out.add(new Option(m, m)));
            case "managerId" -> {
                for (Employee e : people.all()) {
                    if (!e.id.equals(target.id) && e.status != com.meridian.hr.domain.EmployeeStatus.INACTIVE) {
                        out.add(new Option(e.id, e.fullName() + " · " + e.title));
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    // ---- view records ----

    public record Stat(String label, int value, String color) {
    }

    public record EmpOption(String id, String label) {
    }

    public record Option(String id, String label) {
    }

    public record EditField(String field, String name, String label, boolean isSelect,
                            List<Option> options, String value, String current) {
    }

    public record RequestView(String id, String name, String initials, String avatarBg,
                              String typeLabel, String typeBg, String typeFg,
                              String statusLabel, String statusBg, String statusFg,
                              String effLabel, String by, List<JobChangeService.DiffRow> diffs,
                              boolean hasReason, String reason,
                              boolean showActions, boolean canDecide,
                              boolean isScheduled, boolean isApplied) {
    }
}
