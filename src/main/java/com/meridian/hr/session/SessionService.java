package com.meridian.hr.session;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Sign-in / sign-out against a workspace's population (the demo identity picker). */
@Service
public class SessionService {

    /** Resolve a picked user id OR a typed email to an employee, then sign in. Returns the user, or null. */
    public Employee signIn(Workspace ws, String userId, String email) {
        if (ws == null) return null;
        Employee target = null;
        if (userId != null && !userId.isBlank()) {
            target = ws.employee(userId.trim());
        }
        if (target == null && email != null && !email.isBlank()) {
            String key = email.trim().toLowerCase(Locale.ROOT);
            for (Employee e : ws.employees) {
                if (e.email != null && e.email.equalsIgnoreCase(key)) {
                    target = e;
                    break;
                }
            }
        }
        if (target == null) return null;
        ws.signedInUserId = target.id;
        return target;
    }

    public void signOut(Workspace ws) {
        if (ws != null) ws.signedInUserId = null;
    }
}
