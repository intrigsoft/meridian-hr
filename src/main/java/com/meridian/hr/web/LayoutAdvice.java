package com.meridian.hr.web;

import com.meridian.hr.diosc.DioscProperties;
import com.meridian.hr.domain.Employee;
import com.meridian.hr.leave.LeaveService;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feeds every authenticated page the AppShell context: the signed-in user, role
 * flags, the role-gated left nav (with per-item built flags), and the Dioschub
 * assistant config for the reserved right-rail mount. Runs for all @Controller
 * views; safely no-ops on the (layout-less) login page.
 */
@ControllerAdvice
public class LayoutAdvice {

    /**
     * Which nav destinations are wired. Extended as each phase lands — this is the
     * single switch the overnight loop flips per domain.
     */
    private static final Set<String> BUILT = Set.of("dashboard", "directory", "leave", "approvals", "time", "profile",
            "onboarding", "offboarding", "jobchanges", "performance", "recruitment",
            "analytics", "settings", "org", "roles", "audit");

    private final SessionContext session;
    private final DioscProperties diosc;
    private final LeaveService leave;
    private final AccessPolicy policy;

    public LayoutAdvice(SessionContext session, DioscProperties diosc, LeaveService leave, AccessPolicy policy) {
        this.session = session;
        this.diosc = diosc;
        this.leave = leave;
        this.policy = policy;
    }

    @ModelAttribute
    public void shell(Model model) {
        Employee user = session.currentUser();
        model.addAttribute("diosc", diosc);
        if (user == null) {
            return; // login page — no shell
        }
        boolean approver = user.accessRole.isApprover();
        boolean hr = user.accessRole == com.meridian.hr.domain.Role.HR;

        model.addAttribute("currentUser", user);
        model.addAttribute("isApprover", approver);
        model.addAttribute("isHr", hr);
        model.addAttribute("roleLabel", user.accessRole.label);
        // Notifications + approval badge derive from the Leave event log.
        Actor actor = session.actor();
        model.addAttribute("notifications", leave.notificationsFor(actor));
        model.addAttribute("unreadCount", leave.unreadCountFor(actor));
        int approvalCount = leave.pendingCountFor(actor);

        // Nav is derived from the RBAC catalog: an item appears only when the role holds the
        // permission that page requires — so nobody sees a link to a "not available" panel.
        List<NavItem> workspace = new ArrayList<>();
        workspace.add(item("dashboard", "Dashboard", "/dashboard", null)); // every signed-in user
        addIf(workspace, Permission.LEAVE_REQUEST, "leave", "My Leave", "/leave", null);
        addIf(workspace, Permission.TIME_TRACK, "time", "My Time", "/time", null);
        addIf(workspace, Permission.PROFILE_EDIT_OWN, "profile", "My Profile", "/profile", null);
        addIf(workspace, Permission.LEAVE_APPROVE, "approvals", "Approvals", "/approvals",
                approvalCount > 0 ? approvalCount : null);

        List<NavItem> peopleOps = new ArrayList<>();
        addIf(peopleOps, Permission.DIRECTORY_VIEW, "directory", "Directory", "/directory", null);
        addIf(peopleOps, Permission.ANALYTICS_VIEW, "analytics", "Analytics", "/analytics", null);
        addIf(peopleOps, Permission.ONBOARDING_VIEW, "onboarding", "Onboarding", "/onboarding", null);
        addIf(peopleOps, Permission.OFFBOARDING_VIEW, "offboarding", "Offboarding", "/offboarding", null);
        addIf(peopleOps, Permission.JOBCHANGE_VIEW, "jobchanges", "Job changes", "/job-changes", null);
        addIf(peopleOps, Permission.PERF_REVIEW_VIEW, "performance", "Performance", "/performance", null);
        addIf(peopleOps, Permission.RECRUIT_VIEW, "recruitment", "Recruitment", "/recruitment", null);

        model.addAttribute("navWorkspace", workspace);
        model.addAttribute("navPeopleOps", peopleOps);

        List<NavItem> admin = new ArrayList<>();
        addIf(admin, Permission.ADMIN_SETTINGS, "settings", "Settings", "/settings", null);
        addIf(admin, Permission.ADMIN_ORG, "org", "Org structure", "/org", null);
        addIf(admin, Permission.ADMIN_ROLES, "roles", "Roles & access", "/roles", null);
        addIf(admin, Permission.AUDIT_VIEW, "audit", "Audit log", "/audit", null);
        if (!admin.isEmpty()) {
            model.addAttribute("navAdmin", admin);
        }
    }

    /** Append a nav item only when the current role holds {@code perm}. */
    private void addIf(List<NavItem> into, Permission perm, String key, String label, String href, Integer badge) {
        if (policy.can(perm)) {
            into.add(item(key, label, href, badge));
        }
    }

    private static NavItem item(String key, String label, String href, Integer badge) {
        return new NavItem(key, label, href, ICONS.getOrDefault(key, ""), BUILT.contains(key), badge);
    }

    /** Inner SVG markup for each nav icon (24x24, stroke=currentColor), ported from the design shell. */
    private static final Map<String, String> ICONS = Map.ofEntries(
        Map.entry("dashboard", "<rect x='3' y='3' width='7' height='9' rx='1'/><rect x='14' y='3' width='7' height='5' rx='1'/><rect x='14' y='12' width='7' height='9' rx='1'/><rect x='3' y='16' width='7' height='5' rx='1'/>"),
        Map.entry("leave", "<rect x='3' y='4' width='18' height='17' rx='2'/><path d='M8 2v4M16 2v4M3 10h18'/>"),
        Map.entry("time", "<circle cx='12' cy='12' r='9'/><path d='M12 7v5l3 2'/>"),
        Map.entry("profile", "<circle cx='12' cy='8' r='4'/><path d='M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2'/>"),
        Map.entry("approvals", "<path d='M9 11l3 3L22 4'/><path d='M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11'/>"),
        Map.entry("directory", "<path d='M17 21v-2a4 4 0 0 0-3-3.87'/><path d='M7 21v-2a4 4 0 0 1 3-3.87'/><circle cx='12' cy='7' r='4'/><circle cx='5' cy='9' r='2'/><circle cx='19' cy='9' r='2'/>"),
        Map.entry("analytics", "<path d='M3 3v18h18'/><path d='M7 15l4-4 3 3 5-6'/>"),
        Map.entry("onboarding", "<path d='M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'/><circle cx='9' cy='7' r='4'/><path d='M22 11h-6M19 8v6'/>"),
        Map.entry("offboarding", "<path d='M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'/><path d='M16 17l5-5-5-5M21 12H9'/>"),
        Map.entry("jobchanges", "<path d='M7 17l5-5 5 5M7 7l5 5 5-5'/>"),
        Map.entry("performance", "<circle cx='12' cy='12' r='9'/><circle cx='12' cy='12' r='4.5'/><circle cx='12' cy='12' r='0.5' fill='currentColor'/>"),
        Map.entry("recruitment", "<path d='M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2'/><circle cx='9' cy='7' r='4'/><path d='M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75'/>"),
        Map.entry("settings", "<circle cx='12' cy='12' r='3'/><path d='M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z'/>"),
        Map.entry("org", "<rect x='9' y='3' width='6' height='6' rx='1'/><rect x='3' y='15' width='6' height='6' rx='1'/><rect x='15' y='15' width='6' height='6' rx='1'/><path d='M12 9v3M6 15v-1.5a1.5 1.5 0 0 1 1.5-1.5h9a1.5 1.5 0 0 1 1.5 1.5V15'/>"),
        Map.entry("roles", "<path d='M12 2l7 4v6c0 4-3 7-7 8-4-1-7-4-7-8V6z'/><path d='M9 12l2 2 4-4'/>"),
        Map.entry("audit", "<path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/><path d='M14 2v6h6M9 13h6M9 17h4'/>")
    );
}
