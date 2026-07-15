package com.meridian.hr.offboarding;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.OffboardingCase;
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

import java.util.ArrayList;
import java.util.List;

/**
 * People Ops → Offboarding. HR and managers run exit checklists; completing a case
 * marks the employee inactive in the Directory. HR sees every case; a manager sees
 * only their reports'. Ports the fixture's {@code Offboarding.dc.html}.
 */
@Controller
public class OffboardingController {

    private final OffboardingService offboarding;
    private final PeopleService people;
    private final SessionContext session;

    public OffboardingController(OffboardingService offboarding, PeopleService people, SessionContext session) {
        this.offboarding = offboarding;
        this.people = people;
        this.session = session;
    }

    @GetMapping("/offboarding")
    public String index(@RequestParam(required = false) String add,
                        @RequestParam(required = false) String emp,
                        Model model) {
        Actor actor = session.actor();
        boolean allowed = actor != null && actor.isApprover();

        model.addAttribute("active", "offboarding");
        model.addAttribute("allowed", allowed);
        model.addAttribute("restricted", !allowed);

        OffboardingService.Summary sum = offboarding.summary();
        model.addAttribute("noteTitle", sum.active() + " active exit" + (sum.active() == 1 ? "" : "s"));
        model.addAttribute("noteSub", sum.completed() + " completed");

        if (!allowed) {
            return "offboarding/offboarding";
        }

        model.addAttribute("stats", List.of(
                new Stat("Active exits", sum.active()),
                new Stat("Completed", sum.completed()),
                new Stat("Total cases", sum.total())));

        List<CaseView> views = new ArrayList<>();
        for (OffboardingCase c : offboarding.forActor(actor)) {
            views.add(toView(c));
        }
        model.addAttribute("cases", views);
        model.addAttribute("noCases", views.isEmpty());

        boolean addOpen = "true".equals(add) || (emp != null && !emp.isBlank());
        model.addAttribute("addOpen", addOpen);
        List<EmpOption> opts = new ArrayList<>();
        for (Employee e : offboarding.candidateEmployees(actor)) {
            opts.add(new EmpOption(e.id, e.fullName() + " · " + e.title));
        }
        model.addAttribute("empOptions", opts);
        model.addAttribute("typeOptions", offboarding.exitTypes());
        model.addAttribute("formEmp", emp == null ? "" : emp);
        return "offboarding/offboarding";
    }

    @PostMapping("/offboarding/new")
    public String start(@RequestParam String empId,
                        @RequestParam(defaultValue = "resignation") String type,
                        @RequestParam(required = false) String lastDay,
                        @RequestParam(required = false) String reason,
                        RedirectAttributes ra) {
        Actor actor = session.actor();
        if (actor == null || !actor.isApprover()) return "redirect:/offboarding";
        if (empId == null || empId.isBlank() || lastDay == null || lastDay.isBlank()) {
            ra.addFlashAttribute("toast", "Pick an employee and a last working day.");
            ra.addFlashAttribute("toastDot", "#c98a2a");
            return "redirect:/offboarding?add=true";
        }
        offboarding.start(empId, type, lastDay, reason, actor.userId());
        ra.addFlashAttribute("toast", "Offboarding started — checklist created.");
        ra.addFlashAttribute("toastDot", "#e0a13a");
        return "redirect:/offboarding";
    }

    @PostMapping("/offboarding/{caseId}/task/{taskId}")
    public String toggle(@PathVariable String caseId, @PathVariable String taskId) {
        Actor actor = session.actor();
        if (actor != null && actor.isApprover()) offboarding.toggleTask(caseId, taskId);
        return "redirect:/offboarding";
    }

    @PostMapping("/offboarding/{caseId}/complete")
    public String complete(@PathVariable String caseId, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (actor == null || !actor.isApprover()) return "redirect:/offboarding";
        OffboardingCase c = offboarding.getCase(caseId);
        boolean ok = offboarding.complete(caseId, actorName(actor));
        if (ok && c != null) {
            ra.addFlashAttribute("toast", c.empName + " offboarded — marked inactive.");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
        }
        return "redirect:/offboarding";
    }

    @PostMapping("/offboarding/{caseId}/cancel")
    public String cancel(@PathVariable String caseId, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (actor == null || !actor.isApprover()) return "redirect:/offboarding";
        offboarding.cancel(caseId);
        ra.addFlashAttribute("toast", "Offboarding cancelled.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return "redirect:/offboarding";
    }

    // ---- view building ----

    private CaseView toView(OffboardingCase c) {
        Employee e = people.get(c.empId);
        OffboardingService.ExitType t = offboarding.exitType(c.type);
        OffboardingService.Progress prog = offboarding.progressOf(c);
        boolean active = "in_progress".equals(c.status);
        boolean allDone = prog.done() == prog.total() && prog.total() > 0;
        boolean doneCase = "completed".equals(c.status);

        String statusLabel = doneCase ? "Completed" : "In progress";
        String statusBg = doneCase ? "#e6f3ec" : "#faf3e6";
        String statusFg = doneCase ? "#2f6f4f" : "#9a6a1a";

        List<TaskView> tasks = new ArrayList<>();
        for (OffboardingCase.Task task : c.checklist) {
            tasks.add(new TaskView(task.id, task.label, task.owner, task.done,
                    task.done ? "#cfe6d8" : "#e4e8ed", task.done ? "#f2faf5" : "#fff",
                    task.done ? "#2f6f4f" : "#c7cdd6", task.done ? "#2f6f4f" : "#fff",
                    task.done ? "#2f6f4f" : "#3a4453"));
        }

        return new CaseView(c.id, c.empName,
                e != null ? e.initials : "?", e != null ? e.avatarBg : "#c7cdd6",
                c.title, c.dept, PeopleService.formatDate(c.lastDay),
                t.label(), t.bg(), t.color(), statusLabel, statusBg, statusFg,
                prog.pct(), prog.done(), prog.total(), prog.pct() == 100 ? "#2f6f4f" : "#17457f",
                c.reason != null && !c.reason.isBlank(), c.reason, tasks,
                active, doneCase, allDone,
                allDone ? "#2f6f4f" : "#b8c2ce", allDone ? "Complete exit" : "Finish tasks first");
    }

    private static String actorName(Actor actor) {
        return actor.isHr() ? "P. Nair (HR)" : "Manager";
    }

    // ---- view records ----

    public record Stat(String label, int value) {
    }

    public record EmpOption(String id, String label) {
    }

    public record TaskView(String id, String label, String owner, boolean done,
                           String border, String bg, String boxBorder, String boxBg, String textColor) {
    }

    public record CaseView(String id, String name, String initials, String avatarBg,
                           String title, String dept, String lastDayLabel,
                           String typeLabel, String typeBg, String typeFg,
                           String statusLabel, String statusBg, String statusFg,
                           int pct, int done, int total, String pctColor,
                           boolean hasReason, String reason, List<TaskView> tasks,
                           boolean active, boolean doneCase, boolean completeEnabled,
                           String completeBg, String completeLabel) {
    }
}
