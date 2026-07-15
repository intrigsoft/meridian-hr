package com.meridian.hr.onboarding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static onboarding metadata (pure, no state): the downstream-systems catalog Dioschub
 * writes to, the accountable owners, and the role families a schema can target. Mirrors
 * the exported constants in the fixture's {@code onboarding-store.js}.
 */
public final class OnboardingMeta {

    private OnboardingMeta() {
    }

    public record System(String id, String label, String color, String bg) {
    }

    public record RoleFamily(String id, String label, String dept) {
    }

    private static final Map<String, System> SYSTEMS = new LinkedHashMap<>();

    static {
        put("azure_ad", "Azure AD", "#2f6aa8", "#e9f0f9");
        put("google_ws", "Google WS", "#3d8564", "#ecf5f0");
        put("slack", "Slack", "#7a5aa8", "#f0ecf8");
        put("servicenow", "ServiceNow", "#7a5aa8", "#f0ecf8");
        put("workday", "Workday Pay", "#9a6a1a", "#f7f1e4");
        put("genetec", "Genetec", "#b23b2e", "#fbeae8");
        put("docebo", "Docebo LMS", "#5a6472", "#eef1f4");
        put("onepass", "1Password", "#2f6aa8", "#e9f0f9");
        put("manual", "Manual", "#6b7480", "#eef1f4");
    }

    private static void put(String id, String label, String color, String bg) {
        SYSTEMS.put(id, new System(id, label, color, bg));
    }

    public static System system(String key) {
        System s = key == null ? null : SYSTEMS.get(key);
        return s != null ? s : SYSTEMS.get("manual");
    }

    public static List<System> systems() {
        return new ArrayList<>(SYSTEMS.values());
    }

    public static final List<String> OWNERS =
            List.of("People Ops", "IT", "Facilities", "Payroll", "Hiring Manager", "Security");

    private static final List<RoleFamily> ROLES = List.of(
            new RoleFamily("design", "Design / Product", "Design"),
            new RoleFamily("eng", "Engineering", "Engineering"),
            new RoleFamily("sales", "Sales / GTM", "Revenue"),
            new RoleFamily("general", "General / Other", "Operations"));

    public static List<RoleFamily> roles() {
        return ROLES;
    }

    public static RoleFamily role(String id) {
        for (RoleFamily r : ROLES) {
            if (r.id().equals(id)) return r;
        }
        return ROLES.get(3); // general fallback
    }
}
