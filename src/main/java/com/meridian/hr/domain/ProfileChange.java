package com.meridian.hr.domain;

/**
 * A pending change to a sensitive profile field (legal name, bank, tax) that routes
 * through HR approval before it takes effect. Ported from the fixture's people-store
 * change-request log. Public fields for SpringEL.
 */
public class ProfileChange {

    public String id;
    public String empId;
    public String path;        // e.g. "legalName", "bank.accountLast4"
    public String label;       // human label, e.g. "Legal name"
    public String oldValue;
    public String newValue;
    public String requestedBy;
    public long requestedAt;
    public String status = "pending";   // pending | approved | rejected
    public String decidedBy;
    public long decidedAt;

    public ProfileChange() {
    }

    public ProfileChange(String id, String empId, String path, String label,
                         String oldValue, String newValue, String requestedBy) {
        this.id = id;
        this.empId = empId;
        this.path = path;
        this.label = label;
        this.oldValue = oldValue == null ? "" : oldValue;
        this.newValue = newValue == null ? "" : newValue;
        this.requestedBy = requestedBy;
        this.requestedAt = System.currentTimeMillis();
    }
}
