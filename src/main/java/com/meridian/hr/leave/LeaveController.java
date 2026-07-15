package com.meridian.hr.leave;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.LeaveRequest;
import com.meridian.hr.domain.PolicyConfig;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The Leave domain: My Leave (balances + request history), New Leave Request (the
 * policy engine + HITL routing), Approvals (manager/HR queue), and the shared
 * request drawer with its approve / reject / more-info / withdraw / undo actions.
 */
@Controller
public class LeaveController {

    private final LeaveService leave;
    private final SessionContext session;
    private final AccessPolicy policy;

    public LeaveController(LeaveService leave, SessionContext session, AccessPolicy policy) {
        this.leave = leave;
        this.session = session;
        this.policy = policy;
    }

    // ============ My Leave ============

    @GetMapping("/leave")
    public String myLeave(@RequestParam(required = false, defaultValue = "all") String tab,
                          @RequestParam(required = false, defaultValue = "all") String type,
                          @RequestParam(required = false) String req,
                          @RequestParam(required = false, defaultValue = "view") String mode,
                          Model model) {
        Employee me = session.currentUser();
        leave.markRead(me.id);

        List<Row> rows = new ArrayList<>();
        int cAll = 0, cPending = 0, cApproved = 0, cRejected = 0;
        for (LeaveRequest r : leave.forEmployee(me.id)) {
            String cat = LeaveMeta.status(r.status).cat();
            cAll++;
            if ("pending".equals(cat)) cPending++;
            else if ("approved".equals(cat)) cApproved++;
            else if ("rejected".equals(cat)) cRejected++;
            if (!"all".equals(tab) && !tab.equals(cat)) continue;
            if (!"all".equals(type) && !type.equals(r.type)) continue;
            rows.add(row(r));
        }

        model.addAttribute("balances", leave.balances(me.id));
        model.addAttribute("rows", rows);
        model.addAttribute("tab", tab);
        model.addAttribute("typeFilter", type);
        model.addAttribute("counts", new Counts(cAll, cPending, cApproved, cRejected));
        model.addAttribute("leaveTypes", LeaveMeta.types());
        model.addAttribute("active", "leave");
        model.addAttribute("noteTitle", "My leave");
        drawer(req, mode, "leave", model);
        return "leave/my-leave";
    }

    // ============ New Leave Request ============

    @GetMapping("/leave/new")
    public String newLeave(@RequestParam(required = false, defaultValue = "annual") String type,
                           @RequestParam(required = false) String startDate,
                           @RequestParam(required = false) String endDate,
                           @RequestParam(required = false, defaultValue = "false") boolean halfDay,
                           @RequestParam(required = false, defaultValue = "false") boolean hasAttachment,
                           @RequestParam(required = false) String cover,
                           @RequestParam(required = false) String reason,
                           Model model) {
        Employee me = session.currentUser();
        String start = startDate == null || startDate.isBlank() ? "2026-10-19" : startDate;
        String end = endDate == null || endDate.isBlank() ? "2026-10-23" : endDate;
        LeaveService.Draft draft = new LeaveService.Draft(type, start, end, halfDay, hasAttachment, cover, reason);
        LeaveService.Evaluation eval = leave.evaluate(me, draft);

        model.addAttribute("draft", draft);
        model.addAttribute("eval", eval);
        model.addAttribute("leaveTypes", LeaveMeta.types());
        model.addAttribute("coverOptions", List.of("M. Reid", "E. Vasquez", "T. Bradley"));
        model.addAttribute("scenarios", scenarios());
        model.addAttribute("holidaysInRange", holidaysInRange(start, end));
        model.addAttribute("active", "leave");
        model.addAttribute("noteTitle", "New leave request");
        return "leave/new-leave";
    }

    @PostMapping("/leave/new")
    public String submitLeave(@RequestParam String type,
                              @RequestParam String startDate, @RequestParam String endDate,
                              @RequestParam(required = false, defaultValue = "false") boolean halfDay,
                              @RequestParam(required = false, defaultValue = "false") boolean hasAttachment,
                              @RequestParam(required = false) String cover,
                              @RequestParam(required = false) String reason,
                              RedirectAttributes ra) {
        Employee me = session.currentUser();
        LeaveService.Draft draft = new LeaveService.Draft(type, startDate, endDate, halfDay, hasAttachment, cover, reason);
        LeaveService.Evaluation eval = leave.evaluate(me, draft);
        if (!eval.clean()) {
            ra.addFlashAttribute("toast", "Resolve the failing policy checks before submitting.");
            ra.addFlashAttribute("toastDot", "#e0a13a");
            return "redirect:/leave/new";
        }
        LeaveRequest r = leave.addRequest(me, draft);
        ra.addFlashAttribute("toast", eval.overCeiling()
                ? "Saved & escalated to P. Nair (HR) — over the manager ceiling."
                : "Saved & routed to D. Okonkwo — within manager authority.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/leave?req=" + r.id;
    }

    // ============ Approvals ============

    @GetMapping("/approvals")
    public String approvals(@RequestParam(required = false) String req,
                            @RequestParam(required = false, defaultValue = "view") String mode,
                            Model model) {
        Actor actor = session.actor();
        boolean isApprover = policy.can(Permission.LEAVE_APPROVE);
        model.addAttribute("isApprover", isApprover);
        model.addAttribute("active", "approvals");
        model.addAttribute("noteTitle", "Leave approvals");

        if (isApprover) {
            LeaveService.ApproverQueue q = leave.forApprover(actor);
            List<LeaveRequest> combined = new ArrayList<>(q.actionable());
            combined.addAll(q.escalated());
            List<QueueRow> queue = new ArrayList<>();
            int actionableCount = 0, ceilingCount = 0;
            for (LeaveRequest r : combined) {
                boolean decided = isDecided(r.status);
                boolean managerReadonly = r.overCeiling && actor.role() == com.meridian.hr.domain.Role.MANAGER;
                boolean actionable = !decided && !managerReadonly;
                if (actionable) actionableCount++;
                if (r.overCeiling) ceilingCount++;
                queue.add(queueRow(r, actionable, managerReadonly));
            }
            model.addAttribute("queue", queue);
            model.addAttribute("emptyQueue", queue.isEmpty());
            model.addAttribute("pendingCount", actionableCount);
            model.addAttribute("ceilingCount", ceilingCount);
        }
        drawer(req, mode, "approvals", model);
        return "leave/approvals";
    }

    // ============ decision actions ============

    @PostMapping("/leave/{id}/approve")
    public String approve(@PathVariable String id, @RequestParam(defaultValue = "approvals") String back, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (canDecide(actor, leave.get(id))) {
            leave.decide(id, "approved", actor, "");
            ra.addFlashAttribute("toast", "Approved " + nameOf(id) + "'s leave");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
            ra.addFlashAttribute("undoId", id);
            ra.addFlashAttribute("undoBack", back);
        }
        return "redirect:/" + back;
    }

    @PostMapping("/leave/{id}/reject")
    public String reject(@PathVariable String id, @RequestParam String note,
                         @RequestParam(defaultValue = "approvals") String back, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (canDecide(actor, leave.get(id)) && note != null && !note.isBlank()) {
            leave.decide(id, "rejected", actor, note.trim());
            ra.addFlashAttribute("toast", "Rejected " + nameOf(id) + "'s leave");
            ra.addFlashAttribute("toastDot", "#e0483a");
            ra.addFlashAttribute("undoId", id);
            ra.addFlashAttribute("undoBack", back);
        }
        return "redirect:/" + back;
    }

    @PostMapping("/leave/{id}/info")
    public String info(@PathVariable String id, @RequestParam String note,
                       @RequestParam(defaultValue = "approvals") String back, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (canDecide(actor, leave.get(id)) && note != null && !note.isBlank()) {
            leave.decide(id, "info", actor, note.trim());
            ra.addFlashAttribute("toast", "Info requested from " + nameOf(id));
            ra.addFlashAttribute("toastDot", "#5b8cff");
        }
        return "redirect:/" + back;
    }

    @PostMapping("/leave/{id}/undo")
    public String undo(@PathVariable String id, @RequestParam(defaultValue = "approvals") String back, RedirectAttributes ra) {
        LeaveRequest r = leave.get(id);
        if (policy.can(Permission.LEAVE_APPROVE) && r != null) {
            leave.revert(id);
            ra.addFlashAttribute("toast", "Decision reverted");
            ra.addFlashAttribute("toastDot", "#8894a3");
        }
        return "redirect:/" + back;
    }

    @PostMapping("/leave/{id}/withdraw")
    public String withdraw(@PathVariable String id, @RequestParam(defaultValue = "leave") String back, RedirectAttributes ra) {
        Actor actor = session.actor();
        Employee me = session.currentUser();
        LeaveRequest r = leave.get(id);
        if (r != null && me != null && me.id.equals(r.empId) && isOpen(r.status)) {
            leave.cancel(id, actor, me.fullName());
            ra.addFlashAttribute("toast", "Request withdrawn");
            ra.addFlashAttribute("toastDot", "#8894a3");
        }
        return "redirect:/" + back;
    }

    // ============ drawer builder ============

    private void drawer(String req, String mode, String back, Model model) {
        if (req == null) {
            model.addAttribute("drawerOpen", false);
            return;
        }
        LeaveRequest r = leave.get(req);
        if (r == null) {
            model.addAttribute("drawerOpen", false);
            return;
        }
        Actor viewer = session.actor();
        boolean isOwner = viewer != null && viewer.userId().equals(r.empId);
        boolean isMyQueue = viewer != null && (
                (viewer.role() == com.meridian.hr.domain.Role.MANAGER && viewer.userId().equals(r.approverId) && !r.overCeiling)
                        || (viewer.role() == com.meridian.hr.domain.Role.HR && viewer.userId().equals(r.approverId)));
        boolean canDecide = isMyQueue && ("pending".equals(r.status) || "escalated".equals(r.status));
        boolean canWithdraw = isOwner && isOpen(r.status);

        LeaveMeta.StatusMeta sm = LeaveMeta.status(r.status);
        List<Fact> facts = List.of(
                new Fact("Type", LeaveMeta.type(r.type).label(), "#28323f"),
                new Fact("Dates", LeaveMeta.fmtLong(r.startDate) + (r.startDate.equals(r.endDate) ? "" : " – " + LeaveMeta.fmtLong(r.endDate)), "#28323f"),
                new Fact("Working days", r.daysLabel() + (r.days == 1 ? " day" : " days"), "#28323f"),
                new Fact("Coverage", r.cover == null ? "—" : r.cover, "#28323f"),
                new Fact("Approver", LeaveMeta.approver(r.approverId).name(), "#28323f"),
                new Fact("Submitted", r.submitted, "#8894a3"));

        // callout
        boolean hasCallout = false;
        String calloutText = "", calloutBg = "#f7f4ec", calloutBorder = "#ece2c6", calloutIcon = "#9a6a1a";
        if (r.overCeiling && r.ceilingNote != null) {
            hasCallout = true;
            calloutText = r.ceilingNote + " Final approval sits with HR (P. Nair).";
        } else if (r.flag != null) {
            hasCallout = true;
            calloutText = "Flagged: " + r.flag + ". Follow up before approving.";
            calloutBg = "#fbe9e7";
            calloutBorder = "#f0cfc9";
            calloutIcon = "#b23b2e";
        }

        List<TimelineRow> timeline = new ArrayList<>();
        for (int i = 0; i < r.events.size(); i++) {
            LeaveRequest.Event e = r.events.get(i);
            LeaveMeta.EventMeta m = LeaveMeta.event(e.kind);
            timeline.add(new TimelineRow(m.label(), m.color(), m.bg(), m.iconPath(),
                    e.atLabel, e.actorName, e.note != null && !e.note.isEmpty(), e.note, i < r.events.size() - 1));
        }

        model.addAttribute("drawerOpen", true);
        model.addAttribute("dr", new DrawerView(r.id, r.empBg, r.empInitials, r.empName, r.empRole,
                sm.label(), sm.pillBg(), sm.pillFg(), facts,
                r.reason != null && !r.reason.isBlank(), r.reason,
                hasCallout, calloutText, calloutBg, calloutBorder, calloutIcon, timeline,
                canDecide || canWithdraw, canDecide, canWithdraw, mode, back));
    }

    // ============ helpers ============

    private boolean canDecide(Actor actor, LeaveRequest r) {
        if (actor == null || r == null || !policy.can(Permission.LEAVE_APPROVE)) return false;
        boolean isMyQueue = (actor.role() == com.meridian.hr.domain.Role.MANAGER && actor.userId().equals(r.approverId) && !r.overCeiling)
                || (actor.role() == com.meridian.hr.domain.Role.HR && actor.userId().equals(r.approverId));
        return isMyQueue && ("pending".equals(r.status) || "escalated".equals(r.status));
    }

    private static boolean isDecided(String status) {
        return "approved".equals(status) || "rejected".equals(status) || "info".equals(status) || "posted".equals(status);
    }

    private static boolean isOpen(String status) {
        return "pending".equals(status) || "escalated".equals(status) || "info".equals(status);
    }

    private String nameOf(String id) {
        LeaveRequest r = leave.get(id);
        return r == null ? "the request" : r.empName;
    }

    private Row row(LeaveRequest r) {
        LeaveMeta.TypeMeta tm = LeaveMeta.type(r.type);
        LeaveMeta.StatusMeta sm = LeaveMeta.status(r.status);
        LeaveMeta.ApproverMeta ap = LeaveMeta.approver(r.approverId);
        return new Row(r.id, r.type, tm.label(), tm.dot(),
                LeaveMeta.formatRange(r.startDate, r.endDate), r.daysLabel(), r.submitted,
                ap.name(), ap.init(), ap.bg(), sm.label(), sm.pillBg(), sm.pillFg(), sm.cat());
    }

    private QueueRow queueRow(LeaveRequest r, boolean actionable, boolean managerReadonly) {
        LeaveMeta.TypeMeta tm = LeaveMeta.type(r.type);
        String resLabel = null, resBg = null, resFg = null;
        if (isDecided(r.status)) {
            LeaveMeta.StatusMeta sm = LeaveMeta.status(r.status);
            resLabel = sm.label();
            resBg = sm.pillBg();
            resFg = sm.pillFg();
        } else if (managerReadonly) {
            resLabel = "Escalated → HR";
            resBg = "#e8eefb";
            resFg = "#3a5aa8";
        }
        return new QueueRow(r.id, r.empName, r.empRole, r.empInitials, r.empBg,
                tm.label(), LeaveMeta.formatRange(r.startDate, r.endDate), r.daysLabel(), r.cover,
                r.overCeiling, r.ceilingNote, r.flag, actionable, !actionable, resLabel, resBg, resFg);
    }

    private List<Scenario> scenarios() {
        return List.of(
                new Scenario("Valid request", "annual", "2026-09-29", "2026-10-03"),
                new Scenario("Insufficient balance", "annual", "2026-08-03", "2026-08-21"),
                new Scenario("Blackout period", "annual", "2026-12-22", "2026-12-24"),
                new Scenario("Coverage conflict", "annual", "2026-08-11", "2026-08-14"),
                new Scenario("Short notice", "annual", "2026-07-04", "2026-07-08"),
                new Scenario("Missing certificate", "sick", "2026-07-20", "2026-07-23"));
    }

    private List<PolicyConfig.Holiday> holidaysInRange(String start, String end) {
        List<PolicyConfig.Holiday> out = new ArrayList<>();
        try {
            LocalDate s = LocalDate.parse(start), e = LocalDate.parse(end);
            for (PolicyConfig.Holiday h : session.workspace().policy.holidaysSorted()) {
                LocalDate hd = LocalDate.parse(h.date);
                if (!hd.isBefore(s) && !hd.isAfter(e)) out.add(h);
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    // ============ view records ============

    public record Row(String id, String typeKey, String type, String dot, String dates, String days,
                      String submitted, String approver, String apprInit, String apprBg,
                      String status, String pillBg, String pillFg, String cat) {
    }

    public record Counts(int all, int pending, int approved, int rejected) {
    }

    public record QueueRow(String id, String name, String role, String initials, String bg,
                           String type, String dates, String days, String cover,
                           boolean overCeiling, String ceilingNote, String flag,
                           boolean actionable, boolean resolved, String resLabel, String resBg, String resFg) {
    }

    public record Scenario(String label, String type, String startDate, String endDate) {
    }

    public record Fact(String k, String v, String color) {
    }

    public record TimelineRow(String label, String color, String bg, String iconPath, String atLabel,
                              String actorName, boolean hasNote, String note, boolean line) {
    }

    public record DrawerView(String reqId, String empBg, String empInitials, String empName, String empRole,
                             String statusLabel, String statusBg, String statusFg, List<Fact> facts,
                             boolean hasReason, String reason,
                             boolean hasCallout, String calloutText, String calloutBg, String calloutBorder, String calloutIcon,
                             List<TimelineRow> timeline, boolean showFooter, boolean canDecide, boolean canWithdraw,
                             String mode, String back) {
    }
}
