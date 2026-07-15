package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The org taxonomy every employee record hangs on: departments, career levels and
 * compensation bands (HR-governed in Org Structure). Ported from {@code org-store}.
 * Headcount is read live from the people list, never stored here.
 */
public class OrgConfig {

    public static final List<String> TRACKS = List.of("IC", "Manager", "Executive");

    public List<Department> departments = new ArrayList<>();
    public List<String> levels = new ArrayList<>();
    public List<Band> bands = new ArrayList<>();

    public Department deptMeta(String id) {
        for (Department d : departments) {
            if (d.id.equals(id)) return d;
        }
        return new Department(id, "#8894a3", "#eef1f4", null);
    }

    public Band bandMeta(String id) {
        for (Band b : bands) {
            if (b.id.equals(id)) return b;
        }
        return null;
    }

    public static class Department {
        public String id;      // the department name is its id
        public String color;
        public String tint;
        public String lead;    // employee id of the department lead, or null

        public Department() {
        }

        public Department(String id, String color, String tint, String lead) {
            this.id = id;
            this.color = color;
            this.tint = tint;
            this.lead = lead;
        }
    }

    public static class Band {
        public String id;
        public String track;   // IC | Manager | Executive
        public String label;
        public int min;
        public int max;

        public Band() {
        }

        public Band(String id, String track, String label, int min, int max) {
            this.id = id;
            this.track = track;
            this.label = label;
            this.min = min;
            this.max = max;
        }
    }
}
