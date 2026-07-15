package com.meridian.hr.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A promotion / transfer / comp change request against an employee, carrying an
 * EFFECTIVE DATE. HR approves; on approval it either applies immediately (date today
 * or past) or is SCHEDULED and applied when the date arrives. Ported from the
 * fixture's {@code jobchange-store.js}. {@code fromSnapshot} captures the pre-change
 * values so the diff stays stable even after the change applies.
 */
public class JobChange {

    public String id;
    public String empId;
    public String empName;
    public String type;             // promotion | transfer | comp | reclass
    public String effectiveDate;    // ISO yyyy-MM-dd
    public final Map<String, String> changes = new LinkedHashMap<>();       // field → new raw value
    public final Map<String, String> fromSnapshot = new LinkedHashMap<>();  // field → old raw value
    public String reason;
    public String requestedBy;
    public String requestedByName;
    public String status = "pending"; // pending | scheduled | applied | rejected
    public long createdAt;
    public String decidedBy;
    public String decidedByName;
    public long decidedAt;
    public long appliedAt;

    public JobChange() {
    }

    /** Seed helper: record a field change with its before/after raw values. */
    public JobChange change(String field, String from, String to) {
        fromSnapshot.put(field, from);
        changes.put(field, to);
        return this;
    }
}
