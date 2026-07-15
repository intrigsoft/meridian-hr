package com.meridian.hr.time;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.PolicyConfig;
import com.meridian.hr.domain.Timesheet;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** My Time: the weekly timesheet grid, live clock, recent weeks, and (for approvers) a pending-approval strip. */
@Controller
public class TimeController {

    private final TimeService time;
    private final PeopleService people;
    private final SessionContext session;
    private final AccessPolicy policy;

    public TimeController(TimeService time, PeopleService people, SessionContext session, AccessPolicy policy) {
        this.time = time;
        this.people = people;
        this.session = session;
        this.policy = policy;
    }

    @GetMapping("/time")
    public String myTime(@RequestParam(required = false) String week, Model model) {
        Employee me = session.currentUser();
        Actor actor = session.actor();
        String ws = (week == null || week.isBlank()) ? time.thisWeek() : week;

        Timesheet sheet = time.getWeek(me.id, ws);
        List<String> dates = time.weekDates(ws);
        Map<String, String> leaveMap = time.leaveDaysFor(me.id, dates);
        boolean editable = !"approved".equals(sheet.status);

        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String iso = dates.get(i);
            boolean weekend = i >= 5;
            double h = sheet.days.getOrDefault(Timesheet.DAY_IDS[i], 0.0);
            PolicyConfig.Holiday hol = time.holidayOn(iso);
            String leaveLbl = leaveMap.get(iso);
            Cell c = new Cell();
            c.day = Timesheet.DAY_LABELS[i];
            c.dayId = Timesheet.DAY_IDS[i];
            c.dateLabel = time.dateLabel(iso);
            c.dayColor = weekend ? "#b6bdc6" : "#5a6472";
            c.bg = "#fff";
            c.border = "#eef1f4";
            if (hol != null) {
                c.tagLabel = hol.name;
                c.tagColor = "#9a6a1a";
                c.bg = "#f7f1e0";
                c.border = "#ece2c6";
            } else if (leaveLbl != null) {
                c.tagLabel = leaveLbl + " leave";
                c.tagColor = "#3a5aa8";
                c.bg = "#eef4fb";
                c.border = "#dbe6f3";
            } else if (weekend && h <= 0) {
                c.tagLabel = "—";
                c.tagColor = "#c7cdd6";
                c.bg = "#fafbfc";
            } else if (editable) {
                c.editable = true;
                c.hours = h > 0 ? trim(h) : "";
            } else {
                c.tagLabel = trim(h) + "h";
                c.tagColor = "#28323f";
            }
            cells.add(c);
        }

        double total = sheet.total();
        int target = time.targetHours();

        model.addAttribute("weekStart", ws);
        model.addAttribute("weekLabel", time.weekLabel(ws));
        model.addAttribute("prevWeek", time.shiftWeek(ws, -1));
        model.addAttribute("nextWeek", time.shiftWeek(ws, 1));
        model.addAttribute("cells", cells);
        model.addAttribute("status", sheet.status);
        model.addAttribute("statusMeta", statusMeta(sheet.status));
        model.addAttribute("weekTotal", trim(total));
        model.addAttribute("target", target);
        model.addAttribute("totalColor", total >= target ? "#2f6f4f" : "#1a2331");
        model.addAttribute("canSubmit", !"approved".equals(sheet.status));
        model.addAttribute("submitLabel", "submitted".equals(sheet.status) ? "Re-submit" : "Submit week");
        model.addAttribute("isApproved", "approved".equals(sheet.status));
        Employee appr = sheet.approvedBy == null ? null : people.get(sheet.approvedBy);
        model.addAttribute("approverName", appr == null ? "—" : appr.fullName());

        // clock
        Long since = time.clockedSince(me.id);
        model.addAttribute("clocked", since != null);
        model.addAttribute("clockElapsed", since == null ? "0:00" : time.elapsedLabel(since));
        model.addAttribute("clockSince", since == null ? "Not clocked in" : "Since " + time.sinceLabel(since));

        // recent weeks (exclude viewed)
        List<HistoryRow> hist = new ArrayList<>();
        for (Timesheet t : time.weeksFor(me.id)) {
            if (t.weekStart.equals(ws)) continue;
            StatusMeta sm = statusMeta(t.status);
            hist.add(new HistoryRow(t.weekStart, time.weekLabel(t.weekStart), t.totalLabel(),
                    sm.label(), sm.bg(), sm.fg()));
            if (hist.size() >= 6) break;
        }
        model.addAttribute("history", hist);
        model.addAttribute("holidays", time.upcomingHolidays(5));

        // approvals strip (manager: reports; HR: everyone)
        List<PendingRow> pending = new ArrayList<>();
        if (policy.can(Permission.TIME_APPROVE)) {
            boolean isHr = actor.isHr();
            List<String> reports = new ArrayList<>();
            for (Employee r : people.directReports(me.id)) reports.add(r.id);
            for (Timesheet t : time.pendingApprovalsFor(me.id, isHr, reports)) {
                Employee e = people.get(t.empId);
                pending.add(new PendingRow(t.empId, t.weekStart,
                        e == null ? t.empId : e.fullName(), e == null ? "?" : e.initials,
                        e == null ? "#c7cdd6" : e.avatarBg, time.weekLabel(t.weekStart), t.totalLabel()));
            }
        }
        model.addAttribute("pending", pending);
        model.addAttribute("active", "time");
        model.addAttribute("noteTitle", trim(total) + "h this week");
        return "time/my-time";
    }

    @PostMapping("/time/save")
    public String save(@RequestParam String week, @RequestParam Map<String, String> all, RedirectAttributes ra) {
        Employee me = session.currentUser();
        time.saveHours(me.id, week, parseDays(all));
        ra.addFlashAttribute("toast", "Hours saved.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/time?week=" + week;
    }

    @PostMapping("/time/submit")
    public String submit(@RequestParam String week, @RequestParam Map<String, String> all, RedirectAttributes ra) {
        Employee me = session.currentUser();
        time.submitWeek(me.id, week, parseDays(all));
        ra.addFlashAttribute("toast", "Timesheet submitted for approval.");
        ra.addFlashAttribute("toastDot", "#e0a13a");
        return "redirect:/time?week=" + week;
    }

    @PostMapping("/time/approve")
    public String approve(@RequestParam String empId, @RequestParam String week,
                          @RequestParam(required = false) String back, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (policy.can(Permission.TIME_APPROVE)) {
            time.approveWeek(empId, week, actor.userId());
            Employee e = people.get(empId);
            ra.addFlashAttribute("toast", (e == null ? empId : e.first) + "'s timesheet approved.");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
        }
        return "redirect:/time" + (back == null || back.isBlank() ? "" : "?week=" + back);
    }

    @PostMapping("/time/clock")
    public String clock(@RequestParam(required = false) String week, RedirectAttributes ra) {
        Employee me = session.currentUser();
        boolean wasClocked = time.clockedSince(me.id) != null;
        time.toggleClock(me.id, week);
        ra.addFlashAttribute("toast", wasClocked ? "Clocked out — hours added to today." : "Clocked in. Timer running.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/time" + (week == null || week.isBlank() ? "" : "?week=" + week);
    }

    // ---------------- helpers ----------------

    private Map<String, Double> parseDays(Map<String, String> all) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String d : Timesheet.DAY_IDS) {
            String v = all.get(d);
            if (v != null && !v.isBlank()) {
                try {
                    out.put(d, Double.parseDouble(v.replaceAll("[^0-9.]", "")));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static StatusMeta statusMeta(String status) {
        return switch (status) {
            case "submitted" -> new StatusMeta("Submitted", "#faf3e6", "#9a6a1a");
            case "approved" -> new StatusMeta("Approved", "#e6f3ec", "#2f6f4f");
            default -> new StatusMeta("Open", "#eef1f4", "#6b7480");
        };
    }

    // ---------------- view records ----------------

    public static class Cell {
        public String day;
        public String dayId;
        public String dateLabel;
        public String dayColor;
        public String bg;
        public String border;
        public boolean editable;
        public String hours = "";
        public String tagLabel = "";
        public String tagColor = "#9aa3ad";
    }

    public record StatusMeta(String label, String bg, String fg) {
    }

    public record HistoryRow(String weekStart, String label, String total, String statusLabel, String statusBg, String statusFg) {
    }

    public record PendingRow(String empId, String weekStart, String name, String initials, String avatarBg, String week, String total) {
    }
}
