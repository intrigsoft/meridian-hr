package com.meridian.hr.security;

import com.meridian.hr.domain.Role;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Set;

/**
 * The one place anything asks "may the current caller do this". Wraps the request's
 * {@link Actor} and answers coarse {@link Permission} questions from {@link RolePermissions}.
 *
 * <p>Controllers, {@code LayoutAdvice}, and the future REST/MCP layer inject this instead of
 * poking at {@code actor.isHr()/isApprover()}. Use {@link #can} to gate rendering (show a
 * button / a nav link / a "not available" panel) and {@link #require} to hard-stop an action
 * ({@link AccessDeniedException} → 403 on the API). Resource-scoped rules (ownership, the
 * approval queue, review workflow state) still live in the domain services; this covers the
 * role-level gate they build on.
 */
@Component
@RequestScope
public class AccessPolicy {

    private final SessionContext session;

    public AccessPolicy(SessionContext session) {
        this.session = session;
    }

    /** The signed-in role for this request, or {@code null} if not signed in. */
    public Role role() {
        Actor actor = session.actor();
        return actor == null ? null : actor.role();
    }

    /** Every permission the current caller carries (empty if not signed in). */
    public Set<Permission> permissions() {
        return RolePermissions.forRole(role());
    }

    /** True if the current caller carries {@code permission}. */
    public boolean can(Permission permission) {
        return RolePermissions.has(role(), permission);
    }

    /** True if the caller carries at least one of {@code permissions}. */
    public boolean canAny(Permission... permissions) {
        for (Permission p : permissions) {
            if (can(p)) return true;
        }
        return false;
    }

    /** True if the caller carries all of {@code permissions}. */
    public boolean canAll(Permission... permissions) {
        for (Permission p : permissions) {
            if (!can(p)) return false;
        }
        return true;
    }

    /** Hard-stop: throw {@link AccessDeniedException} unless the caller carries {@code permission}. */
    public void require(Permission permission) {
        if (!can(permission)) {
            throw new AccessDeniedException(permission);
        }
    }
}
