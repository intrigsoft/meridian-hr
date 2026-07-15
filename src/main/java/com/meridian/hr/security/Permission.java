package com.meridian.hr.security;

/**
 * The capability catalog — the single vocabulary of "things you may do" in Meridian HR.
 *
 * <p>This is the RBAC coarse gate: a {@link Permission} answers "may this role perform
 * this kind of action at all", independent of any specific record. Resource-scoped rules
 * (my own leave, my report's timesheet, comp for my downstream, review workflow state)
 * layer <em>on top</em> of a permission and live with the data that decides them — see
 * {@link AccessPolicy} and the domain services.
 *
 * <p>Roles are mapped to permissions in {@link RolePermissions}. Nav visibility
 * ({@code LayoutAdvice}), controller guards, and the (future) REST/MCP surface all consult
 * {@link AccessPolicy} rather than re-deriving {@code isHr()/isApprover()} by hand.
 */
public enum Permission {

    // ---- Directory & compensation ----
    /** View the employee directory and 360 profiles. */
    DIRECTORY_VIEW("Directory", "View directory & profiles"),
    /** Add employees and edit records / status / exit (HR). */
    DIRECTORY_MANAGE("Directory", "Add / edit / offboard employees"),
    /** See compensation. HR sees everyone; managers only their downstream (scoped in AccessPolicy). */
    COMP_VIEW("Directory", "View compensation"),

    // ---- Leave ----
    /** Submit and cancel one's own leave requests. */
    LEAVE_REQUEST("Leave", "Request & cancel own leave"),
    /** Approve / reject leave (scoped to the actor's approval queue). */
    LEAVE_APPROVE("Leave", "Approve / reject leave"),

    // ---- Time ----
    /** Clock in/out and file one's own timesheet. */
    TIME_TRACK("Time", "Track own time"),
    /** Approve timesheets (HR: all; managers: direct reports — scoped). */
    TIME_APPROVE("Time", "Approve timesheets"),

    // ---- Profile self-service ----
    /** Edit one's own profile; sensitive fields still route to HR approval. */
    PROFILE_EDIT_OWN("Profile", "Edit own profile"),
    /** Approve sensitive profile-change requests (HR). */
    PROFILE_APPROVE("Profile", "Approve profile changes"),

    // ---- Onboarding ----
    /** View onboarding cases and plans. */
    ONBOARDING_VIEW("Onboarding", "View onboarding cases"),
    /** Complete / upload / reopen onboarding steps. */
    ONBOARDING_MANAGE("Onboarding", "Drive onboarding checklists"),
    /** Template designer, start a case, convert a hire into the directory (HR). */
    ONBOARDING_ADMIN("Onboarding", "Templates, start & convert"),

    // ---- Offboarding ----
    /** View exit cases. */
    OFFBOARDING_VIEW("Offboarding", "View exit cases"),
    /** Start an exit and drive the checklist. */
    OFFBOARDING_MANAGE("Offboarding", "Start & drive exits"),

    // ---- Job changes ----
    /** View job-change requests. */
    JOBCHANGE_VIEW("Job changes", "View job changes"),
    /** Raise a job-change request. */
    JOBCHANGE_REQUEST("Job changes", "Raise job-change requests"),
    /** Approve / reject / schedule job changes (HR). */
    JOBCHANGE_APPROVE("Job changes", "Approve & schedule changes"),

    // ---- Performance ----
    /** View and file one's own review. */
    PERF_REVIEW_VIEW("Performance", "View & file own review"),
    /** Write manager reviews and calibrate (reviewer/approver, scoped by review state). */
    PERF_REVIEW_MANAGE("Performance", "Write manager reviews"),
    /** Create / launch / close cycles, reopen committed reviews, export (HR). */
    PERF_CYCLE_ADMIN("Performance", "Run cycles & calibrate"),

    // ---- Recruitment ----
    /** View requisitions and pipelines. */
    RECRUIT_VIEW("Recruitment", "View requisitions & pipeline"),
    /** Drive the pipeline: candidates, moves, offers, req approval (approver). */
    RECRUIT_MANAGE("Recruitment", "Manage candidates & offers"),
    /** Open requisitions and approve offers (HR). */
    RECRUIT_ADMIN("Recruitment", "Open reqs & approve offers"),

    // ---- Analytics & admin ----
    /** View people analytics. */
    ANALYTICS_VIEW("Analytics", "View people analytics"),
    /** Admin settings (leave types, policy). */
    ADMIN_SETTINGS("Admin", "Settings"),
    /** Org structure admin. */
    ADMIN_ORG("Admin", "Org structure"),
    /** Roles & access admin. */
    ADMIN_ROLES("Admin", "Roles & access"),
    /** Read the audit log. */
    AUDIT_VIEW("Admin", "Audit log");

    private final String group;
    private final String label;

    Permission(String group, String label) {
        this.group = group;
        this.label = label;
    }

    /** UI grouping for the roles matrix (e.g. "Leave", "Recruitment", "Admin"). */
    public String group() {
        return group;
    }

    /** Short human-readable action label. */
    public String label() {
        return label;
    }
}
