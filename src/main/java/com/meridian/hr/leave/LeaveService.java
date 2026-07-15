package com.meridian.hr.leave;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.LeaveRequest;
import com.meridian.hr.domain.PolicyConfig;
import com.meridian.hr.domain.Role;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Leave lifecycle + policy engine, ported from the fixture's {@code leave-store} and
 * the New Leave Request validation. Operates on the calling device's workspace. The
 * demo clock is pinned so the seeded scenarios (notice, coverage, blackout) stay
 * meaningful regardless of the wall clock.
 */
@Service
public class LeaveService {

    /** Pinned "today" for notice/coverage checks (matches the fixture's reference date). */
    public static final LocalDate DEMO_TODAY = LocalDate.of(2026, 7, 2);
    private static final LocalDate COVER_START = LocalDate.of(2026, 8, 11);
    private static final LocalDate COVER_END = LocalDate.of(2026, 8, 15);

    private final SessionContext session;

    public LeaveService(SessionContext session) {
        this.session = session;
    }

    private Workspace ws() {
        return session.workspace();
    }

    private PolicyConfig policy() {
        return ws().policy;
    }

    // ---------------- reads ----------------

    public List<LeaveRequest> all() {
        return ws().leaveRequests;
    }

    public LeaveRequest get(String id) {
        for (LeaveRequest r : all()) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    public List<LeaveRequest> forEmployee(String empId) {
        List<LeaveRequest> out = new ArrayList<>();
        for (LeaveRequest r : all()) {
            if (r.empId.equals(empId)) out.add(r);
        }
        return out;
    }

    /** An approver's split queue: what they can act on, plus what's escalated past them. */
    public record ApproverQueue(List<LeaveRequest> actionable, List<LeaveRequest> escalated) {
    }

    public ApproverQueue forApprover(Actor actor) {
        List<LeaveRequest> actionable = new ArrayList<>();
        List<LeaveRequest> escalated = new ArrayList<>();
        if (actor == null) return new ApproverQueue(actionable, escalated);
        for (LeaveRequest r : all()) {
            if (actor.role() == Role.MANAGER && actor.userId().equals(r.managerId)) {
                if (!r.overCeiling && "pending".equals(r.status)) actionable.add(r);
                else if (r.overCeiling && "escalated".equals(r.status)) escalated.add(r);
            } else if (actor.role() == Role.HR && actor.userId().equals(r.approverId) && "escalated".equals(r.status)) {
                actionable.add(r);
            }
        }
        return new ApproverQueue(actionable, escalated);
    }

    public int pendingCountFor(Actor actor) {
        return forApprover(actor).actionable().size();
    }

    // ---------------- balances ----------------

    public record BalanceTile(String label, String value, String unit, String pct, String sub, String color) {
    }

    public List<BalanceTile> balances(String empId) {
        List<BalanceTile> tiles = new ArrayList<>();
        String[][] defs = {{"annual", "Annual"}, {"sick", "Sick"}, {"personal", "Personal"}};
        for (String[] d : defs) {
            int allow = policy().allowanceFor(d[0]);
            double used = usedDays(empId, d[0]);
            double remaining = Math.max(0, allow - used);
            int pct = allow > 0 ? (int) Math.min(100, Math.round(used / allow * 100)) : 0;
            tiles.add(new BalanceTile(d[1], trim(remaining), "days", pct + "%",
                    trim(used) + " of " + allow + " used", LeaveMeta.type(d[0]).dot()));
        }
        tiles.add(new BalanceTile("Carryover", "2", "days", "60%", "expires Mar 31", "#9b7fc4"));
        return tiles;
    }

    private double usedDays(String empId, String type) {
        double sum = 0;
        for (LeaveRequest r : forEmployee(empId)) {
            if (r.type.equals(type) && ("approved".equals(r.status) || "posted".equals(r.status))) sum += r.days;
        }
        return sum;
    }

    // ---------------- mutations ----------------

    public LeaveRequest addRequest(Employee me, Draft d) {
        Evaluation eval = evaluate(me, d);
        LeaveRequest r = new LeaveRequest();
        r.id = newId();
        r.empId = me.id;
        r.empName = me.fullName();
        r.empInitials = me.initials;
        r.empBg = me.avatarBg;
        r.empRole = me.title;
        r.type = d.type();
        r.startDate = d.startDate();
        r.endDate = d.endDate();
        r.days = eval.workingDays();
        r.cover = (d.cover() == null || d.cover().isBlank()) ? "—" : d.cover();
        r.reason = d.reason();
        r.managerId = "david.okonkwo";
        r.overCeiling = eval.overCeiling();
        r.approverId = eval.overCeiling() ? "priya.nair"
                : (me.id.equals("david.okonkwo") ? "priya.nair" : "david.okonkwo");
        r.status = eval.overCeiling() ? "escalated" : "pending";
        r.submitted = LeaveMeta.fmt(LocalDate.now().toString());
        r.createdAt = System.currentTimeMillis();
        if (eval.overCeiling()) r.ceilingNote = eval.ceilingReason();

        long now = System.currentTimeMillis();
        r.events.add(new LeaveRequest.Event("submitted", me.id, me.fullName(),
                d.reason() == null ? "" : d.reason(), now, LeaveMeta.stampLabel(now)));
        if (eval.overCeiling()) {
            r.events.add(new LeaveRequest.Event("escalated", "system", "System",
                    "Over the " + policy().ceilingDays + "-day manager ceiling — escalated to HR (P. Nair).",
                    now, LeaveMeta.stampLabel(now)));
        } else {
            r.events.add(new LeaveRequest.Event("routed", "system", "System",
                    "Within manager authority — routed to " + LeaveMeta.approver(r.approverId).name() + ".",
                    now, LeaveMeta.stampLabel(now)));
        }
        all().add(0, r);
        return r;
    }

    /** decision = approved | rejected | info. Appends a decision event with an optional note. */
    public void decide(String id, String decision, Actor by, String note) {
        LeaveRequest r = get(id);
        if (r == null || by == null) return;
        String kind = "info".equals(decision) ? "info_requested" : decision;
        r.status = decision;
        r.decidedBy = by.userId();
        r.decidedAt = System.currentTimeMillis();
        r.events.add(new LeaveRequest.Event(kind, by.userId(), LeaveMeta.approver(by.userId()).name(),
                note, r.decidedAt, LeaveMeta.stampLabel(r.decidedAt)));
    }

    /** Undo the latest decision: pop the last event, restore the open state. */
    public void revert(String id) {
        LeaveRequest r = get(id);
        if (r == null) return;
        if (!r.events.isEmpty()) r.events.remove(r.events.size() - 1);
        r.status = r.overCeiling ? "escalated" : "pending";
        r.decidedBy = null;
        r.decidedAt = 0;
    }

    /** Requester withdraws their own open request. */
    public void cancel(String id, Actor by, String byName) {
        LeaveRequest r = get(id);
        if (r == null || by == null) return;
        long now = System.currentTimeMillis();
        r.status = "cancelled";
        r.events.add(new LeaveRequest.Event("cancelled", by.userId(), byName == null ? by.userId() : byName,
                "Withdrawn by requester.", now, LeaveMeta.stampLabel(now)));
    }

    private String newId() {
        int base = 1053;
        int extra = 0;
        for (LeaveRequest r : all()) {
            if (r.id.matches("R-10[5-9]\\d")) extra++;
        }
        return "R-" + (base + extra);
    }

    // ---------------- policy engine (New Leave Request) ----------------

    public record Draft(String type, String startDate, String endDate, boolean halfDay,
                        boolean hasAttachment, String cover, String reason) {
    }

    public record Check(boolean ok, String title, String detail, String code) {
    }

    public record Evaluation(double workingDays, String workingDaysLabel, List<Check> checks, int errorCount,
                             boolean clean, boolean overCeiling, String ceilingReason, boolean needsAttachment,
                             String typeLabel, String balanceAfterLabel, boolean balanceNeg, String approverName,
                             String submitLabel, boolean submitEnabled, int annualRemaining) {
    }

    public Evaluation evaluate(Employee me, Draft d) {
        PolicyConfig p = policy();
        double wd = workingDays(d.startDate(), d.endDate(), d.halfDay());
        int annualAllow = p.allowanceFor("annual");
        double usedAnnual = usedDays(me.id, "annual");
        int annualRemaining = (int) Math.max(0, annualAllow - usedAnnual);

        List<Check> checks = new ArrayList<>();
        // 1 balance
        if ("annual".equals(d.type()) && wd > annualRemaining) {
            checks.add(err("Insufficient balance",
                    "Requested " + trim(wd) + " days; annual balance is " + annualRemaining + ".", "422"));
        } else {
            checks.add(ok("Sufficient balance"));
        }
        // 2 blackout
        PolicyConfig.Blackout bo = p.blackoutOverlap(d.startDate(), d.endDate());
        if (bo != null) {
            checks.add(err("Blackout period", bo.label + ": leave is restricted on these dates.", "422"));
        } else {
            checks.add(ok("No blackout conflict"));
        }
        // 3 coverage
        if (overlapsCoverage(d.startDate(), d.endDate())) {
            checks.add(err("Team-coverage conflict", "2 of 3 team members already off on these dates.", "422"));
        } else {
            checks.add(ok("Team coverage OK"));
        }
        // 4 notice
        if ("annual".equals(d.type()) && noticeDays(d.startDate()) < p.noticeDays) {
            checks.add(err("Notice-period violation", "Annual leave requires " + p.noticeDays
                    + " days' notice; " + Math.max(0, noticeDays(d.startDate())) + " given.", "422"));
        } else {
            checks.add(ok("Notice period met"));
        }
        // 5 medical certificate
        boolean needsAttachment = "sick".equals(d.type()) && wd > p.sickCertDays;
        if (needsAttachment && !d.hasAttachment()) {
            checks.add(err("Missing medical certificate",
                    "Sick leave over " + p.sickCertDays + " days requires a certificate.", "422"));
        } else if (needsAttachment) {
            checks.add(ok("Medical certificate attached"));
        }
        // 6 overlap with own existing leave
        LeaveRequest overlap = null;
        for (LeaveRequest r : forEmployee(me.id)) {
            if ("rejected".equals(r.status) || "cancelled".equals(r.status)) continue;
            if (rangesOverlap(d.startDate(), d.endDate(), r.startDate, r.endDate)) {
                overlap = r;
                break;
            }
        }
        if (overlap != null) {
            checks.add(err("Overlaps existing leave", "You already have " + LeaveMeta.type(overlap.type).shortLabel()
                    + " leave on " + LeaveMeta.formatRange(overlap.startDate, overlap.endDate)
                    + " (" + overlap.id + ").", "409"));
        } else {
            checks.add(ok("No overlap with your leave"));
        }

        int errorCount = (int) checks.stream().filter(c -> c.code() != null).count();
        boolean clean = errorCount == 0 && wd > 0;

        boolean overCeiling = wd > p.ceilingDays || "parental".equals(d.type()) || "unpaid".equals(d.type());
        String typeLabel = LeaveMeta.type(d.type()).shortLabel();
        String ceilingReason = wd > p.ceilingDays
                ? "Requests over " + p.ceilingDays + " days exceed a manager's authority."
                : "Extended " + typeLabel.toLowerCase() + " leave requires higher approval.";

        double balAfter = "annual".equals(d.type()) ? annualRemaining - wd : annualRemaining;
        boolean balanceNeg = "annual".equals(d.type()) && balAfter < 0;
        String balanceAfterLabel = "annual".equals(d.type()) ? trim(balAfter) + " days" : "— (non-annual)";

        String submitLabel = clean
                ? (overCeiling ? "Submit for HR approval" : "Submit for approval")
                : "Resolve " + errorCount + " issue" + (errorCount == 1 ? "" : "s") + " to submit";

        return new Evaluation(wd, trim(wd) + (wd == 1 ? " day" : " days"), checks, errorCount, clean,
                overCeiling, ceilingReason, needsAttachment, typeLabel + " leave",
                balanceAfterLabel, balanceNeg, overCeiling ? "P. Nair (HR)" : "D. Okonkwo",
                submitLabel, clean, annualRemaining);
    }

    private static Check ok(String title) {
        return new Check(true, title, null, null);
    }

    private static Check err(String title, String detail, String code) {
        return new Check(false, title, detail, code);
    }

    // business days inclusive, excluding weekends + configured holidays
    public double workingDays(String startIso, String endIso, boolean halfDay) {
        if (startIso == null || endIso == null) return 0;
        LocalDate s, e;
        try {
            s = LocalDate.parse(startIso);
            e = LocalDate.parse(endIso);
        } catch (RuntimeException ex) {
            return 0;
        }
        if (e.isBefore(s)) return 0;
        double count = 0;
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            if (!weekend && policy().holidayOn(d.toString()) == null) count++;
        }
        if (halfDay && count > 0) count -= 0.5;
        return count;
    }

    private boolean overlapsCoverage(String startIso, String endIso) {
        if (startIso == null || endIso == null) return false;
        try {
            LocalDate s = LocalDate.parse(startIso), e = LocalDate.parse(endIso);
            return !s.isAfter(COVER_END) && !e.isBefore(COVER_START);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private int noticeDays(String startIso) {
        if (startIso == null) return 999;
        try {
            return (int) ChronoUnit.DAYS.between(DEMO_TODAY, LocalDate.parse(startIso));
        } catch (RuntimeException ex) {
            return 999;
        }
    }

    private static boolean rangesOverlap(String s1, String e1, String s2, String e2) {
        return s1 != null && e1 != null && s2 != null && e2 != null
                && s1.compareTo(e2) <= 0 && e1.compareTo(s2) >= 0;
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ---------------- notifications (derived from the event log) ----------------

    public record Notification(String id, String reqId, String title, String sub, String atLabel, boolean unread) {
    }

    private record Timed(Notification n, long at) {
    }

    public List<Notification> notificationsFor(Actor actor) {
        List<Timed> out = new ArrayList<>();
        if (actor == null) return List.of();
        long seen = ws().leaveReadAt.getOrDefault(actor.userId(), 0L);
        for (LeaveRequest r : all()) {
            LeaveMeta.TypeMeta tm = LeaveMeta.type(r.type);
            String shortLc = tm.shortLabel().toLowerCase();
            List<LeaveRequest.Event> evs = r.events;
            for (int i = 0; i < evs.size(); i++) {
                LeaveRequest.Event e = evs.get(i);
                String title = null, sub = null;
                if (r.empId.equals(actor.userId())) {
                    switch (e.kind) {
                        case "approved" -> {
                            title = "Your " + shortLc + " leave was approved";
                            sub = e.actorName + (e.note.isEmpty() ? "" : " · " + e.note);
                        }
                        case "rejected" -> {
                            title = "Your " + shortLc + " leave was declined";
                            sub = e.actorName + (e.note.isEmpty() ? "" : " · " + e.note);
                        }
                        case "info_requested" -> {
                            title = e.actorName + " requested more info";
                            sub = e.note.isEmpty() ? tm.shortLabel() + " · " + LeaveMeta.formatRange(r.startDate, r.endDate) : e.note;
                        }
                        case "escalated" -> {
                            title = "Your request was escalated to HR";
                            sub = tm.shortLabel() + " · " + LeaveMeta.formatRange(r.startDate, r.endDate);
                        }
                        case "posted" -> {
                            title = "Leave posted to calendar";
                            sub = tm.shortLabel() + " · " + LeaveMeta.formatRange(r.startDate, r.endDate);
                        }
                        default -> {
                        }
                    }
                } else if (actor.role() == Role.MANAGER || actor.role() == Role.HR) {
                    boolean mineManager = actor.role() == Role.MANAGER && actor.userId().equals(r.managerId)
                            && !r.overCeiling && "routed".equals(e.kind) && "pending".equals(r.status);
                    boolean mineHr = actor.role() == Role.HR && actor.userId().equals(r.approverId)
                            && "escalated".equals(e.kind) && "escalated".equals(r.status);
                    if (mineManager || mineHr) {
                        title = r.empName + " requested " + r.daysLabel() + "d " + shortLc;
                        sub = LeaveMeta.formatRange(r.startDate, r.endDate) + (mineHr ? " · needs HR approval" : " · awaiting you");
                    }
                }
                if (title != null) {
                    out.add(new Timed(new Notification(r.id + "-" + i, r.id, title, sub, e.atLabel, e.at > seen), e.at));
                }
            }
        }
        out.sort(java.util.Comparator.comparingLong(Timed::at).reversed());
        List<Notification> result = new ArrayList<>();
        for (Timed t : out) {
            if (result.size() >= 12) break;
            result.add(t.n());
        }
        return result;
    }

    public int unreadCountFor(Actor actor) {
        int c = 0;
        for (Notification n : notificationsFor(actor)) {
            if (n.unread()) c++;
        }
        return c;
    }

    public void markRead(String userId) {
        if (userId != null) ws().leaveReadAt.put(userId, System.currentTimeMillis());
    }
}
