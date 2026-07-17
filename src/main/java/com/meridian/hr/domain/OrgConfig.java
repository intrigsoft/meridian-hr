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

    /** Color/tint pairs cycled for new departments (mirrors the fixture's DEPT_SWATCHES). */
    public static final List<String[]> DEPT_SWATCHES = List.of(
            new String[]{"#3f7cc4", "#e9f0f9"}, new String[]{"#9a6ab5", "#f0ecf8"},
            new String[]{"#4a9d7a", "#e9f4ef"}, new String[]{"#c68a2a", "#faf3e6"},
            new String[]{"#b56b8f", "#f7ecf1"}, new String[]{"#5a8fb5", "#e8f1f7"},
            new String[]{"#c0563f", "#fbece8"}, new String[]{"#6b7db5", "#eceff8"});

    public List<Department> departments = new ArrayList<>();
    public List<String> levels = new ArrayList<>();
    public List<Band> bands = new ArrayList<>();

    // ---------------- mutations (Org Structure admin page) ----------------

    /** Adds a department (no-op on duplicate name); swatch cycles like the fixture. */
    public Department addDepartment(String name) {
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty()) return null;
        for (Department d : departments) {
            if (d.id.equalsIgnoreCase(nm)) return null;
        }
        String[] sw = DEPT_SWATCHES.get(departments.size() % DEPT_SWATCHES.size());
        Department d = new Department(nm, sw[0], sw[1], null);
        departments.add(d);
        return d;
    }

    public boolean removeDepartment(String id) {
        return departments.removeIf(d -> d.id.equals(id));
    }

    public boolean addLevel(String name) {
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty() || levels.contains(nm)) return false;
        levels.add(nm);
        return true;
    }

    public boolean removeLevel(String name) {
        return levels.remove(name);
    }

    /** Swap a level with its neighbor; dir = -1 (up) or +1 (down). */
    public boolean moveLevel(String name, int dir) {
        int i = levels.indexOf(name);
        int j = i + (dir < 0 ? -1 : 1);
        if (i < 0 || j < 0 || j >= levels.size()) return false;
        String tmp = levels.get(i);
        levels.set(i, levels.get(j));
        levels.set(j, tmp);
        return true;
    }

    /** Adds a band on {@code track} with the next free id (IC6, M5, E2, ...). */
    public Band addBand(String track) {
        String t = TRACKS.contains(track) ? track : "IC";
        String prefix = "Manager".equals(t) ? "M" : "Executive".equals(t) ? "E" : "IC";
        int n = 1;
        String id;
        while (true) {
            id = prefix + n++;
            if (bandMeta(id) == null) break;
        }
        Band b = new Band(id, t, "New band", 80000, 110000);
        bands.add(b);
        return b;
    }

    public boolean removeBand(String id) {
        return bands.removeIf(b -> b.id.equals(id));
    }

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
