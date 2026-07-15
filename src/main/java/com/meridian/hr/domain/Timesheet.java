package com.meridian.hr.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One employee's timesheet for a Monday-started ISO week: hours per weekday plus a
 * submission/approval status. Ported from the fixture's {@code time-store}. Public
 * fields for SpringEL; {@code days} is keyed mon..sun.
 */
public class Timesheet {

    public static final String[] DAY_IDS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    public static final String[] DAY_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public String empId;
    public String weekStart;                 // ISO yyyy-MM-dd (Monday)
    public Map<String, Double> days = blank();
    public String status = "open";           // open | submitted | approved
    public long submittedAt;
    public String approvedBy;
    public long approvedAt;

    public Timesheet() {
    }

    public Timesheet(String empId, String weekStart) {
        this.empId = empId;
        this.weekStart = weekStart;
    }

    public double total() {
        double t = 0;
        for (String d : DAY_IDS) {
            Double v = days.get(d);
            if (v != null) t += v;
        }
        return t;
    }

    public String totalLabel() {
        double t = total();
        return t == Math.floor(t) ? String.valueOf((long) t) : String.valueOf(t);
    }

    public static Map<String, Double> blank() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String d : DAY_IDS) m.put(d, 0.0);
        return m;
    }
}
