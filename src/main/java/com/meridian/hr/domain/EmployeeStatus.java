package com.meridian.hr.domain;

/** Employment status with the pill styling the directory/screens render. */
public enum EmployeeStatus {
    ACTIVE("active", "Active", "#e6f3ec", "#2f6f4f", "#3ecf8e"),
    ONBOARDING("onboarding", "Onboarding", "#e8eefb", "#3a5aa8", "#4a86d8"),
    LEAVE("leave", "On leave", "#faf3e6", "#9a6a1a", "#e0a13a"),
    INACTIVE("inactive", "Inactive", "#eef1f4", "#6b7480", "#b6bdc6");

    public final String key;
    public final String label;
    public final String pillBg;
    public final String pillFg;
    public final String dot;

    EmployeeStatus(String key, String label, String pillBg, String pillFg, String dot) {
        this.key = key;
        this.label = label;
        this.pillBg = pillBg;
        this.pillFg = pillFg;
        this.dot = dot;
    }

    public static EmployeeStatus fromKey(String key) {
        if (key == null) return ACTIVE;
        for (EmployeeStatus s : values()) {
            if (s.key.equalsIgnoreCase(key)) return s;
        }
        return ACTIVE;
    }
}
