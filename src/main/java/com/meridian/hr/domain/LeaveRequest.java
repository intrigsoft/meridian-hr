package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A leave request and its immutable event timeline. Ported from the fixture's
 * {@code leave-store}. Carries a production-shaped lifecycle: every request has an
 * append-only event log; decisions capture a note; employees can withdraw; approvers
 * can undo; per-user notifications are derived from the events. Public fields for
 * direct SpringEL access; {@code days} is a double so half-days count as 0.5.
 */
public class LeaveRequest {

    public String id;
    public String empId;
    public String empName;
    public String empInitials;
    public String empBg;
    public String empRole;

    public String type;            // annual | sick | personal | parental | unpaid | bereavement
    public String startDate;       // ISO yyyy-MM-dd
    public String endDate;         // ISO yyyy-MM-dd
    public double days;
    public String cover;           // coverage / delegate label

    public String reason;
    public String managerId;
    public String approverId;      // who must decide (manager, or HR when over ceiling)
    public boolean overCeiling;
    public String status;          // pending | escalated | info | approved | posted | rejected | cancelled
    public String submitted;       // short label e.g. "Jun 28"
    public String flag;            // e.g. "certificate pending", or null
    public String ceilingNote;     // why it exceeded manager authority, or null

    public long createdAt;
    public String decidedBy;
    public long decidedAt;

    public List<Event> events = new ArrayList<>();

    public LeaveRequest() {
    }

    /** Renders the day count without a trailing ".0" for whole days. */
    public String daysLabel() {
        return (days == Math.floor(days)) ? String.valueOf((long) days) : String.valueOf(days);
    }

    /** An entry in the request's immutable activity timeline. */
    public static class Event {
        public String kind;        // submitted | routed | escalated | info_requested | approved | rejected | cancelled | posted
        public String actor;       // user id or "system"
        public String actorName;
        public String note;
        public long at;            // epoch millis
        public String atLabel;     // e.g. "Jun 28 · 9:00 AM"

        public Event() {
        }

        public Event(String kind, String actor, String actorName, String note, long at, String atLabel) {
            this.kind = kind;
            this.actor = actor;
            this.actorName = actorName;
            this.note = note == null ? "" : note;
            this.at = at;
            this.atLabel = atLabel;
        }
    }
}
