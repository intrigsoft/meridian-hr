package com.meridian.hr.recruitment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static recruitment catalog: pipeline stages, recommendation scale, the scorecard attribute
 * library, sources, rejection reasons, requisition-status pills, and the dept→onboarding-role
 * map. Mirrors the constants in the fixture's {@code recruitment-store.js}.
 */
public final class RecruitmentMeta {

    private RecruitmentMeta() {
    }

    public record Stage(String id, String label, String shortLabel, String color, String bg) {
    }

    private static final List<Stage> STAGES = List.of(
            new Stage("applied", "Applied", "Applied", "#6b7480", "#eef1f4"),
            new Stage("screen", "Recruiter screen", "Screen", "#2f6aa8", "#e9f0f9"),
            new Stage("interview", "HM interview", "Interview", "#5b3ea8", "#f0ecfd"),
            new Stage("onsite", "Onsite panel", "Onsite", "#7a5aa8", "#f2ecfa"),
            new Stage("offer", "Offer", "Offer", "#9a6a1a", "#f7f1e0"),
            new Stage("hired", "Hired", "Hired", "#2f6f4f", "#e6f3ec"));

    public static List<Stage> stages() {
        return STAGES;
    }

    public static Stage stage(String id) {
        if ("rejected".equals(id)) return new Stage("rejected", "Rejected", "Rejected", "#b23b2e", "#fbeae8");
        for (Stage s : STAGES) {
            if (s.id().equals(id)) return s;
        }
        return STAGES.get(0);
    }

    public static int stageIndex(String id) {
        for (int i = 0; i < STAGES.size(); i++) {
            if (STAGES.get(i).id().equals(id)) return i;
        }
        return 0;
    }

    public static final List<String> SCORED_STAGES = List.of("interview", "onsite");

    public record Attr(String id, String name) {
    }

    private static final Map<String, String> LIB = new LinkedHashMap<>();

    static {
        LIB.put("coding", "Coding");
        LIB.put("system", "System design");
        LIB.put("problem", "Problem solving");
        LIB.put("comm", "Communication");
        LIB.put("collab", "Collaboration");
        LIB.put("product", "Product sense");
        LIB.put("craft", "Craft & taste");
        LIB.put("ownership", "Ownership");
        LIB.put("sales", "Sales acumen");
        LIB.put("domain", "Domain expertise");
        LIB.put("culture", "Values / culture add");
    }

    public static String attrName(String id) {
        return LIB.getOrDefault(id, id);
    }

    public record Rec(String id, String label, int val, String color, String bg) {
    }

    private static final Map<String, Rec> RECS = new LinkedHashMap<>();

    static {
        RECS.put("strong_yes", new Rec("strong_yes", "Strong hire", 2, "#2f6f4f", "#e6f3ec"));
        RECS.put("yes", new Rec("yes", "Hire", 1, "#2f6aa8", "#e9f0f9"));
        RECS.put("no", new Rec("no", "No hire", -1, "#9a6a1a", "#f7f1e0"));
        RECS.put("strong_no", new Rec("strong_no", "Strong no", -2, "#b23b2e", "#fbeae8"));
    }

    public static Rec rec(String id) {
        return RECS.getOrDefault(id, RECS.get("no"));
    }

    public static List<Rec> recOrder() {
        return List.of(RECS.get("strong_yes"), RECS.get("yes"), RECS.get("no"), RECS.get("strong_no"));
    }

    public static final List<String> SOURCES =
            List.of("Referral", "LinkedIn", "Job board", "CV corpus", "Inbound", "Agency");

    public static final List<String> REJECTION_REASONS = List.of(
            "Skills mismatch", "Seniority mismatch", "Failed technical screen",
            "Communication concerns", "Compensation misalignment", "Withdrew",
            "Position filled", "Values / culture");

    public record StatusMeta(String label, String bg, String fg) {
    }

    public static StatusMeta reqStatus(String s) {
        return switch (s) {
            case "open" -> new StatusMeta("Open", "#e8eefb", "#3a5aa8");
            case "filled" -> new StatusMeta("Filled", "#e6f3ec", "#2f6f4f");
            case "closed" -> new StatusMeta("Closed", "#eef1f4", "#6b7480");
            case "pending_approval" -> new StatusMeta("Pending approval", "#f7f1e0", "#9a6a1a");
            default -> new StatusMeta("Draft", "#eef1f4", "#6b7480");
        };
    }

    public static final Map<String, String> DEPT_TO_ROLE = Map.of(
            "Engineering", "eng", "Design", "design", "Revenue", "sales");
}
