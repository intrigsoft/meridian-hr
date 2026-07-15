package com.meridian.hr.session;

import com.meridian.hr.domain.Role;

/**
 * Who is acting on the current request. For human traffic this is the signed-in
 * user resolved from the device cookie. When the Dioschub assistant acts (a later
 * phase), {@code isAgent} is true but the identity + role are still the human's —
 * only audit attribution differs. This is the BYOA seam.
 */
public record Actor(String userId, Role role, boolean isAgent) {

    public boolean isApprover() {
        return role != null && role.isApprover();
    }

    public boolean isHr() {
        return role == Role.HR;
    }
}
