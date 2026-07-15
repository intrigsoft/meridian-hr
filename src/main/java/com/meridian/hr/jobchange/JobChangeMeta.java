package com.meridian.hr.jobchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static job-change metadata: the change-type catalog (which fields each type touches),
 * human field labels, and status pill styling. Mirrors the constants in the fixture's
 * {@code jobchange-store.js}. (The Settings-editable type fields are deferred; static here.)
 */
public final class JobChangeMeta {

    private JobChangeMeta() {
    }

    public record ChangeType(String id, String label, String color, String bg, List<String> fields) {
    }

    private static final List<ChangeType> TYPES = List.of(
            new ChangeType("promotion", "Promotion", "#7a5aa8", "#f0ecf8", List.of("title", "level", "salary", "band")),
            new ChangeType("transfer", "Transfer", "#3a5aa8", "#e8eefb", List.of("dept", "managerId", "title", "workMode", "location")),
            new ChangeType("comp", "Compensation", "#9a6a1a", "#f7f1e0", List.of("salary", "band")),
            new ChangeType("reclass", "Reclassification", "#2f6f4f", "#e6f3ec", List.of("employmentType", "title", "level")));

    public static List<ChangeType> types() {
        return TYPES;
    }

    public static ChangeType type(String id) {
        for (ChangeType t : TYPES) {
            if (t.id().equals(id)) return t;
        }
        return TYPES.get(0);
    }

    private static final Map<String, String> FIELD_LABELS = new LinkedHashMap<>();

    static {
        FIELD_LABELS.put("title", "Job title");
        FIELD_LABELS.put("level", "Level");
        FIELD_LABELS.put("dept", "Department");
        FIELD_LABELS.put("managerId", "Manager");
        FIELD_LABELS.put("salary", "Base salary");
        FIELD_LABELS.put("band", "Band");
        FIELD_LABELS.put("workMode", "Work mode");
        FIELD_LABELS.put("location", "Location");
        FIELD_LABELS.put("employmentType", "Employment type");
    }

    public static String fieldLabel(String f) {
        return FIELD_LABELS.getOrDefault(f, f);
    }

    /** Fields whose editor is a dropdown (rest are free text). */
    public static boolean isSelect(String field) {
        return switch (field) {
            case "level", "dept", "band", "workMode", "employmentType", "managerId" -> true;
            default -> false;
        };
    }

    public record StatusMeta(String label, String pillBg, String pillFg) {
    }

    public static StatusMeta status(String s) {
        return switch (s) {
            case "scheduled" -> new StatusMeta("Scheduled", "#e8eefb", "#3a5aa8");
            case "applied" -> new StatusMeta("Applied", "#e6f3ec", "#2f6f4f");
            case "rejected" -> new StatusMeta("Rejected", "#fbe9e7", "#b23b2e");
            default -> new StatusMeta("Pending approval", "#faf3e6", "#9a6a1a");
        };
    }
}
