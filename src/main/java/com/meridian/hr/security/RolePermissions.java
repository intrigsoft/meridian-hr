package com.meridian.hr.security;

import com.meridian.hr.domain.Role;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.meridian.hr.security.Permission.*;

/**
 * The RBAC grant table: which {@link Permission}s each {@link Role} carries. This is the
 * single source of truth the whole product reads — change access here, not in scattered
 * {@code isHr()/isApprover()} checks.
 *
 * <p>The ladder is additive: MANAGER = EMPLOYEE + team/approval powers, HR = MANAGER +
 * company-wide admin. Grants are immutable {@link EnumSet}s.
 */
public final class RolePermissions {

    private RolePermissions() {
    }

    /** Self-service — what everyone with an account can do. */
    private static final Set<Permission> EMPLOYEE = EnumSet.of(
            DIRECTORY_VIEW,
            LEAVE_REQUEST,
            TIME_TRACK,
            PROFILE_EDIT_OWN,
            PERF_REVIEW_VIEW);

    /** Managers add team visibility and first-line approvals over their org. */
    private static final Set<Permission> MANAGER = union(EMPLOYEE, EnumSet.of(
            COMP_VIEW,               // scoped to downstream in AccessPolicy
            LEAVE_APPROVE,
            TIME_APPROVE,
            ONBOARDING_VIEW,
            ONBOARDING_MANAGE,
            OFFBOARDING_VIEW,
            OFFBOARDING_MANAGE,
            JOBCHANGE_VIEW,
            JOBCHANGE_REQUEST,
            PERF_REVIEW_MANAGE,
            RECRUIT_VIEW,
            RECRUIT_MANAGE,
            ANALYTICS_VIEW));

    /** HR carries everything, including company-wide records and all admin. */
    private static final Set<Permission> HR = union(MANAGER, EnumSet.of(
            DIRECTORY_MANAGE,
            PROFILE_APPROVE,
            ONBOARDING_ADMIN,
            JOBCHANGE_APPROVE,
            PERF_CYCLE_ADMIN,
            RECRUIT_ADMIN,
            ADMIN_SETTINGS,
            ADMIN_ORG,
            ADMIN_ROLES,
            AUDIT_VIEW));

    private static final Map<Role, Set<Permission>> GRANTS = new EnumMap<>(Role.class);

    static {
        GRANTS.put(Role.EMPLOYEE, Collections.unmodifiableSet(EMPLOYEE));
        GRANTS.put(Role.MANAGER, Collections.unmodifiableSet(MANAGER));
        GRANTS.put(Role.HR, Collections.unmodifiableSet(HR));
    }

    /** All permissions granted to {@code role} (never null; empty for a null role). */
    public static Set<Permission> forRole(Role role) {
        if (role == null) return Set.of();
        return GRANTS.getOrDefault(role, Set.of());
    }

    /** True if {@code role} carries {@code permission}. */
    public static boolean has(Role role, Permission permission) {
        return role != null && permission != null && forRole(role).contains(permission);
    }

    private static Set<Permission> union(Set<Permission> base, Set<Permission> extra) {
        EnumSet<Permission> out = EnumSet.copyOf(base);
        out.addAll(extra);
        return out;
    }
}
