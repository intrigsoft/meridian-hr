package com.meridian.hr.time;

import com.meridian.hr.domain.LeaveRequest;
import com.meridian.hr.domain.PolicyConfig;
import com.meridian.hr.domain.Timesheet;
import com.meridian.hr.leave.LeaveService;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Timesheet + clock logic, ported from the fixture's {@code time-store}. Weeks start
 * Monday. Holidays and approved/posted leave auto-fill the grid (read from policy +
 * {@link LeaveService}). Operates on the calling device's workspace.
 */
@Service
public class TimeService {

    private static final DateTimeFormatter MON_D = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MON_D_Y = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final DateTimeFormatter MON = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final SessionContext session;
    private final LeaveService leave;

    public TimeService(SessionContext session, LeaveService leave) {
        this.session = session;
        this.leave = leave;
    }

    private Workspace ws() {
        return session.workspace();
    }

    // ---------------- week math ----------------

    public String mondayOf(LocalDate d) {
        return d.minusDays((d.getDayOfWeek().getValue() + 6) % 7).toString();
    }

    public String thisWeek() {
        return mondayOf(LocalDate.now());
    }

    public String shiftWeek(String weekStart, int weeks) {
        return LocalDate.parse(weekStart).plusWeeks(weeks).toString();
    }

    /** The 7 ISO dates of a week, Monday..Sunday. */
    public List<String> weekDates(String weekStart) {
        List<String> out = new ArrayList<>();
        LocalDate mon = LocalDate.parse(weekStart);
        for (int i = 0; i < 7; i++) out.add(mon.plusDays(i).toString());
        return out;
    }

    public String weekLabel(String weekStart) {
        LocalDate mon = LocalDate.parse(weekStart);
        LocalDate sun = mon.plusDays(6);
        return mon.format(MON_D) + " – " + sun.format(MON_D_Y);
    }

    // ---------------- timesheet reads ----------------

    public Timesheet getWeek(String empId, String weekStart) {
        for (Timesheet t : ws().timesheets) {
            if (t.empId.equals(empId) && t.weekStart.equals(weekStart)) return t;
        }
        return new Timesheet(empId, weekStart);
    }

    public List<Timesheet> weeksFor(String empId) {
        List<Timesheet> out = new ArrayList<>();
        for (Timesheet t : ws().timesheets) {
            if (t.empId.equals(empId)) out.add(t);
        }
        out.sort((a, b) -> b.weekStart.compareTo(a.weekStart));
        return out;
    }

    // ---------------- timesheet mutations ----------------

    private Timesheet ensure(String empId, String weekStart) {
        for (Timesheet t : ws().timesheets) {
            if (t.empId.equals(empId) && t.weekStart.equals(weekStart)) return t;
        }
        Timesheet t = new Timesheet(empId, weekStart);
        ws().timesheets.add(t);
        return t;
    }

    /** Save all seven day hours for a week (no-op if already approved). */
    public void saveHours(String empId, String weekStart, Map<String, Double> hours) {
        Timesheet t = ensure(empId, weekStart);
        if ("approved".equals(t.status)) return;
        for (String d : Timesheet.DAY_IDS) {
            Double h = hours.get(d);
            if (h != null) t.days.put(d, Math.max(0, Math.min(24, h)));
        }
    }

    public void submitWeek(String empId, String weekStart, Map<String, Double> hours) {
        Timesheet t = ensure(empId, weekStart);
        if ("approved".equals(t.status)) return;
        if (hours != null) saveHours(empId, weekStart, hours);
        t.status = "submitted";
        t.submittedAt = System.currentTimeMillis();
    }

    public void approveWeek(String empId, String weekStart, String byId) {
        Timesheet t = getWeek(empId, weekStart);
        if (t.weekStart == null || !ws().timesheets.contains(t)) return;
        t.status = "approved";
        t.approvedBy = byId;
        t.approvedAt = System.currentTimeMillis();
    }

    /** Submitted timesheets an approver may act on (HR: everyone; manager: their reports). */
    public List<Timesheet> pendingApprovalsFor(String userId, boolean isHr, List<String> reportIds) {
        List<Timesheet> out = new ArrayList<>();
        for (Timesheet t : ws().timesheets) {
            if (!"submitted".equals(t.status) || t.empId.equals(userId)) continue;
            if (isHr || reportIds.contains(t.empId)) out.add(t);
        }
        return out;
    }

    // ---------------- clock ----------------

    public Long clockedSince(String empId) {
        return ws().clockIns.get(empId);
    }

    public void toggleClock(String empId, String currentWeek) {
        Map<String, Long> clocks = ws().clockIns;
        if (clocks.containsKey(empId)) {
            long since = clocks.remove(empId);
            double hrs = Math.round((System.currentTimeMillis() - since) / 3_600_000.0 * 4) / 4.0;
            // add elapsed to today's cell if today falls in an editable week
            String today = LocalDate.now().toString();
            String wk = mondayOf(LocalDate.now());
            List<String> dates = weekDates(wk);
            int idx = dates.indexOf(today);
            if (idx >= 0) {
                Timesheet t = ensure(empId, wk);
                if (!"approved".equals(t.status)) {
                    String day = Timesheet.DAY_IDS[idx];
                    t.days.put(day, t.days.getOrDefault(day, 0.0) + hrs);
                }
            }
        } else {
            clocks.put(empId, System.currentTimeMillis());
        }
    }

    /** Elapsed clocked-in time as "H:MM" for display (not live-ticking). */
    public String elapsedLabel(long since) {
        long secs = (System.currentTimeMillis() - since) / 1000;
        long h = secs / 3600, m = (secs % 3600) / 60;
        return h + ":" + String.format("%02d", m);
    }

    public String sinceLabel(long since) {
        return java.time.LocalTime.ofSecondOfDay((since / 1000) % 86400)
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
    }

    // ---------------- holidays + leave overlays ----------------

    public PolicyConfig.Holiday holidayOn(String iso) {
        return ws().policy.holidayOn(iso);
    }

    public record UpcomingHoliday(String name, String mon, String day, String weekday) {
    }

    public List<UpcomingHoliday> upcomingHolidays(int n) {
        String from = LocalDate.now().toString();
        List<UpcomingHoliday> out = new ArrayList<>();
        for (PolicyConfig.Holiday h : ws().policy.holidaysSorted()) {
            if (h.date.compareTo(from) < 0) continue;
            LocalDate d = LocalDate.parse(h.date);
            out.add(new UpcomingHoliday(h.name, d.format(MON), String.valueOf(d.getDayOfMonth()), d.format(WEEKDAY)));
            if (out.size() >= n) break;
        }
        return out;
    }

    /** Map of ISO date -> short leave label for approved/posted leave in the given dates. */
    public Map<String, String> leaveDaysFor(String empId, List<String> dates) {
        Map<String, String> map = new LinkedHashMap<>();
        for (LeaveRequest r : leave.forEmployee(empId)) {
            if (!"approved".equals(r.status) && !"posted".equals(r.status)) continue;
            for (String iso : dates) {
                if (iso.compareTo(r.startDate) >= 0 && iso.compareTo(r.endDate) <= 0) {
                    map.put(iso, com.meridian.hr.leave.LeaveMeta.type(r.type).shortLabel());
                }
            }
        }
        return map;
    }

    public String dateLabel(String iso) {
        return LocalDate.parse(iso).format(MON_D);
    }

    public int targetHours() {
        return ws().policy.targetHours;
    }
}
