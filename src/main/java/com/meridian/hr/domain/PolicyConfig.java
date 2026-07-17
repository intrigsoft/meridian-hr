package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central policy/config: the work week, leave allowances + approval thresholds,
 * blackout windows and public holidays. HR edits these in Settings; the leave and
 * time flows read from here so changes take effect across the app. Ported from
 * {@code config-store}.
 */
public class PolicyConfig {

    // work week
    public int targetHours = 40;
    public List<String> workingDays = new ArrayList<>(List.of("mon", "tue", "wed", "thu", "fri"));

    // leave policy
    public int noticeDays = 7;      // minimum notice for annual leave
    public int ceilingDays = 10;    // manager approval authority ceiling; above -> HR
    public int sickCertDays = 2;    // sick leave beyond this needs a certificate
    public List<LeaveType> leaveTypes = new ArrayList<>();
    public List<Blackout> blackouts = new ArrayList<>();

    // holidays
    public List<Holiday> holidays = new ArrayList<>();

    /**
     * Workspace-owned copy of the performance competency catalog HR edits in Settings.
     * Lazily seeded from the static defaults on first admin access so the seed stays
     * in one place; review cycles keep referencing competencies by id.
     */
    public List<Competency> competencies = new ArrayList<>();

    /**
     * Per-change-type overrides of which employee fields that job-change type edits
     * (Settings → Job-change types). Empty until HR toggles a chip; absent types fall
     * back to the static {@code JobChangeMeta} defaults.
     */
    public Map<String, List<String>> changeTypeFields = new LinkedHashMap<>();

    /** The fields change type {@code typeId} edits: the HR override, or {@code defaults}. */
    public List<String> changeTypeFields(String typeId, List<String> defaults) {
        List<String> override = changeTypeFields.get(typeId);
        return override != null ? override : defaults;
    }

    public Competency competency(String id) {
        for (Competency c : competencies) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public LeaveType leaveType(String id) {
        for (LeaveType t : leaveTypes) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public int allowanceFor(String id) {
        LeaveType t = leaveType(id);
        return t != null ? t.allowance : 0;
    }

    /** Returns the blackout overlapping [start,end], or null. */
    public Blackout blackoutOverlap(String startIso, String endIso) {
        if (startIso == null || endIso == null) return null;
        for (Blackout b : blackouts) {
            if (startIso.compareTo(b.end) <= 0 && endIso.compareTo(b.start) >= 0) return b;
        }
        return null;
    }

    public List<Holiday> holidaysSorted() {
        List<Holiday> copy = new ArrayList<>(holidays);
        copy.sort(Comparator.comparing(h -> h.date));
        return copy;
    }

    public Holiday holidayOn(String iso) {
        for (Holiday h : holidays) {
            if (h.date.equals(iso)) return h;
        }
        return null;
    }

    public static class LeaveType {
        public String id;
        public String label;
        public int allowance;   // days/year
        public String color;

        public LeaveType() {
        }

        public LeaveType(String id, String label, int allowance, String color) {
            this.id = id;
            this.label = label;
            this.allowance = allowance;
            this.color = color;
        }
    }

    public static class Blackout {
        public String id;
        public String label;
        public String start;   // ISO
        public String end;     // ISO
        public String scope;

        public Blackout() {
        }

        public Blackout(String id, String label, String start, String end, String scope) {
            this.id = id;
            this.label = label;
            this.start = start;
            this.end = end;
            this.scope = scope;
        }
    }

    /** A competency in the workspace catalog (id is stable; name/blurb are HR-editable). */
    public static class Competency {
        public String id;
        public String name;
        public String blurb;

        public Competency() {
        }

        public Competency(String id, String name, String blurb) {
            this.id = id;
            this.name = name;
            this.blurb = blurb == null ? "" : blurb;
        }
    }

    public static class Holiday {
        public String id;
        public String date;   // ISO
        public String name;

        public Holiday() {
        }

        public Holiday(String id, String date, String name) {
            this.id = id;
            this.date = date;
            this.name = name;
        }
    }
}
