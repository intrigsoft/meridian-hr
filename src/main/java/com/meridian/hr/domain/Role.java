package com.meridian.hr.domain;

/**
 * The single access level a person carries. Drives nav scope, comp visibility,
 * approval authority and admin access across the whole product — mirrors the
 * fixture's {@code accessRole} / session {@code role}.
 */
public enum Role {
    EMPLOYEE("employee", "Employee"),
    MANAGER("manager", "Manager"),
    HR("hr", "HR executive");

    public final String key;   // wire value used in the fixture (employee|manager|hr)
    public final String label;

    Role(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static Role fromKey(String key) {
        if (key == null) return EMPLOYEE;
        for (Role r : values()) {
            if (r.key.equalsIgnoreCase(key)) return r;
        }
        return EMPLOYEE;
    }

    public boolean isApprover() {
        return this == MANAGER || this == HR;
    }
}
