package com.meridian.hr.session;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.workspace.Workspace;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Per-request view of who's calling and what they can see: the resolved workspace,
 * the signed-in user, and the derived {@link Actor}. Controllers inject this instead
 * of poking at request attributes. Populated by {@link DeviceFilter}.
 */
@Component
@RequestScope
public class SessionContext {

    private final HttpServletRequest request;

    public SessionContext(HttpServletRequest request) {
        this.request = request;
    }

    public String deviceId() {
        return (String) request.getAttribute(DeviceFilter.ATTR_DEVICE_ID);
    }

    public Workspace workspace() {
        return (Workspace) request.getAttribute(DeviceFilter.ATTR_WORKSPACE);
    }

    public Employee currentUser() {
        Workspace ws = workspace();
        return ws == null ? null : ws.signedInUser();
    }

    public boolean isSignedIn() {
        return currentUser() != null;
    }

    /** The acting identity for this request (human for now). Null if not signed in. */
    public Actor actor() {
        Employee u = currentUser();
        return u == null ? null : new Actor(u.id, u.accessRole, false);
    }
}
