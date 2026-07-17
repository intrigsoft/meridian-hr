package com.meridian.hr.performance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static performance metadata: the competency library HR designs cycles from, cycle-type
 * labels, review-status pill styling (with a 1–4 step for the stage bar), and score-band /
 * heat-cell helpers. Mirrors the constants in the fixture's {@code performance-store.js}.
 */
public final class PerformanceMeta {

    private PerformanceMeta() {
    }

    public record Competency(String id, String name, String blurb) {
    }

    private static final Map<String, Competency> LIB = new LinkedHashMap<>();

    static {
        put("exec", "Technical execution", "Ships high-quality work reliably.");
        put("craft", "Craft & quality", "Attention to detail and standards.");
        put("collab", "Collaboration", "Works across teams effectively.");
        put("owner", "Ownership", "Drives outcomes end-to-end.");
        put("comm", "Communication", "Clear written and verbal updates.");
        put("lead", "Leadership", "Elevates and mentors others.");
        put("customer", "Customer focus", "Anchors decisions in user value.");
        put("innov", "Innovation", "Brings new ideas that move metrics.");
        put("reliab", "Reliability", "Consistent, dependable delivery.");
        put("strategy", "Strategic thinking", "Connects work to the bigger picture.");
    }

    private static void put(String id, String name, String blurb) {
        LIB.put(id, new Competency(id, name, blurb));
    }

    public static Competency competency(String id) {
        Competency c = LIB.get(id);
        return c != null ? c : new Competency(id, id, "");
    }

    public static List<Competency> library() {
        return new java.util.ArrayList<>(LIB.values());
    }

    /**
     * The workspace competency catalog HR edits in Settings, lazily seeded from the static
     * library. Performance reads resolve through this (not {@link #library()}) so Settings
     * renames/additions flow straight into the cycle designer and review pages; the static
     * library stays the seed + fallback for ids that predate the catalog.
     */
    public static List<com.meridian.hr.domain.PolicyConfig.Competency> catalog(
            com.meridian.hr.domain.PolicyConfig policy) {
        List<com.meridian.hr.domain.PolicyConfig.Competency> list = policy.competencies;
        if (list.isEmpty()) {
            for (Competency c : LIB.values()) {
                list.add(new com.meridian.hr.domain.PolicyConfig.Competency(c.id(), c.name(), c.blurb()));
            }
        }
        return list;
    }

    /** Catalog entry for {@code id}, falling back to the static library so old ids render. */
    public static Competency catalogEntry(com.meridian.hr.domain.PolicyConfig policy, String id) {
        com.meridian.hr.domain.PolicyConfig.Competency c = policy.competency(id);
        return c != null ? new Competency(c.id, c.name, c.blurb) : competency(id);
    }

    private static final Map<String, String> CYCLE_TYPES = new LinkedHashMap<>();

    static {
        CYCLE_TYPES.put("half", "Half-yearly review");
        CYCLE_TYPES.put("annual", "Annual review");
        CYCLE_TYPES.put("quarter", "Quarterly check-in");
        CYCLE_TYPES.put("probation", "Probation review");
    }

    public static String cycleTypeLabel(String id) {
        return CYCLE_TYPES.getOrDefault(id, id);
    }

    /** id → label for every cycle type, in designer display order. */
    public static Map<String, String> cycleTypes() {
        return java.util.Collections.unmodifiableMap(CYCLE_TYPES);
    }

    /** Review status styling; step drives the 4-segment stage bar. */
    public record StatusMeta(String key, String label, String shortLabel, String bg, String fg, String bar, int step) {
    }

    public static StatusMeta status(String key) {
        return switch (key) {
            case "awaiting_manager" -> new StatusMeta(key, "Awaiting manager", "Manager", "#f7f1e0", "#9a6a1a", "#c68a2a", 2);
            case "in_calibration" -> new StatusMeta(key, "In calibration", "Calibrate", "#e9f0f9", "#2f6aa8", "#3f7cc4", 3);
            case "committed" -> new StatusMeta(key, "Committed", "Done", "#e6f3ec", "#2f6f4f", "#4a9d7a", 4);
            default -> new StatusMeta("awaiting_self", "Awaiting self", "Self", "#eef1f4", "#6b7480", "#c7cdd6", 1);
        };
    }

    public record Band(String label, String color, String bg) {
    }

    public static Band scoreBand(Double v) {
        if (v == null) return new Band("—", "#9aa3ad", "#f1f4f7");
        if (v >= 4.5) return new Band("Outstanding", "#2f6f4f", "#e6f3ec");
        if (v >= 3.5) return new Band("Exceeds", "#2f6aa8", "#e9f0f9");
        if (v >= 2.5) return new Band("Meets", "#9a6a1a", "#f7f1e0");
        return new Band("Below", "#b23b2e", "#fbeae8");
    }

    /** Heat-cell background for a raw 1–5 score. */
    public static String cellColor(int v) {
        return v >= 5 ? "#3d8564" : v >= 4 ? "#3f7cc4" : v >= 3 ? "#c68a2a" : v >= 2 ? "#c0563f" : "#a84334";
    }
}
