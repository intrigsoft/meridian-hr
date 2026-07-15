package com.meridian.hr.leave;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Display metadata + timestamp/format helpers shared by all Leave pages. Ported
 * from the constants in the fixture's {@code leave-store}. Pure/static — no state.
 */
public final class LeaveMeta {

    private LeaveMeta() {
    }

    // ---------------- leave types ----------------

    public record TypeMeta(String id, String label, String shortLabel, String dot, String tint, String color) {
    }

    private static final Map<String, TypeMeta> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("annual", new TypeMeta("annual", "Annual leave", "Annual", "#3f7cc4", "#e9f0f9", "#2f6aa8"));
        TYPES.put("sick", new TypeMeta("sick", "Sick leave", "Sick", "#5aa17f", "#ecf5f0", "#3d8564"));
        TYPES.put("personal", new TypeMeta("personal", "Personal leave", "Personal", "#c99b4e", "#faf3e6", "#9a6a1a"));
        TYPES.put("parental", new TypeMeta("parental", "Parental leave", "Parental", "#9b7fc4", "#f0ecf8", "#6a4fa8"));
        TYPES.put("unpaid", new TypeMeta("unpaid", "Unpaid leave", "Unpaid", "#8894a3", "#eef1f4", "#5a6472"));
        TYPES.put("bereavement", new TypeMeta("bereavement", "Bereavement", "Bereavement", "#b56b8f", "#f7ecf1", "#8f4f6f"));
    }

    public static TypeMeta type(String id) {
        return TYPES.getOrDefault(id, new TypeMeta(id, id, id, "#8894a3", "#eef1f4", "#5a6472"));
    }

    public static java.util.List<TypeMeta> types() {
        return new java.util.ArrayList<>(TYPES.values());
    }

    // ---------------- statuses ----------------

    public record StatusMeta(String id, String label, String pillBg, String pillFg, String cat) {
    }

    private static final Map<String, StatusMeta> STATUS = new LinkedHashMap<>();

    static {
        STATUS.put("pending", new StatusMeta("pending", "Pending", "#fbf1de", "#9a6a1a", "pending"));
        STATUS.put("escalated", new StatusMeta("escalated", "Escalated", "#e8eefb", "#3a5aa8", "pending"));
        STATUS.put("info", new StatusMeta("info", "Info requested", "#eef3fb", "#3a5aa8", "pending"));
        STATUS.put("approved", new StatusMeta("approved", "Approved", "#e6f3ec", "#2f6f4f", "approved"));
        STATUS.put("posted", new StatusMeta("posted", "Posted", "#eef1f4", "#6b7480", "approved"));
        STATUS.put("rejected", new StatusMeta("rejected", "Rejected", "#fbe9e7", "#b23b2e", "rejected"));
        STATUS.put("cancelled", new StatusMeta("cancelled", "Withdrawn", "#eef1f4", "#6b7480", "cancelled"));
    }

    public static StatusMeta status(String id) {
        return STATUS.getOrDefault(id, STATUS.get("pending"));
    }

    // ---------------- event kinds ----------------

    public record EventMeta(String kind, String label, String color, String bg, String iconPath) {
    }

    private static final Map<String, EventMeta> EVENTS = new LinkedHashMap<>();

    static {
        EVENTS.put("submitted", new EventMeta("submitted", "Submitted", "#2f6aa8", "#e9f0f9", "M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"));
        EVENTS.put("routed", new EventMeta("routed", "Routed to approver", "#6b7480", "#eef1f4", "M5 12h14M13 6l6 6-6 6"));
        EVENTS.put("escalated", new EventMeta("escalated", "Escalated to HR", "#c68a2a", "#fbf1de", "M12 2l3 7h7l-5.5 4.5L18 21l-6-4-6 4 1.5-7.5L2 9h7z"));
        EVENTS.put("info_requested", new EventMeta("info_requested", "Info requested", "#3a5aa8", "#eef3fb", "M12 16v-4M12 8h.01"));
        EVENTS.put("approved", new EventMeta("approved", "Approved", "#2f6f4f", "#e6f3ec", "M20 6L9 17l-5-5"));
        EVENTS.put("rejected", new EventMeta("rejected", "Rejected", "#b23b2e", "#fbe9e7", "M18 6L6 18M6 6l12 12"));
        EVENTS.put("cancelled", new EventMeta("cancelled", "Withdrawn", "#6b7480", "#eef1f4", "M18 6L6 18M6 6l12 12"));
        EVENTS.put("posted", new EventMeta("posted", "Posted to calendar", "#6b7480", "#eef1f4", "M3 4h18v16H3zM3 10h18M8 2v4M16 2v4"));
    }

    public static EventMeta event(String kind) {
        return EVENTS.getOrDefault(kind, new EventMeta(kind, kind, "#6b7480", "#eef1f4", "M12 8v4l3 2"));
    }

    // ---------------- approver display ----------------

    public record ApproverMeta(String id, String name, String init, String bg) {
    }

    private static final Map<String, ApproverMeta> APPROVERS = new LinkedHashMap<>();

    static {
        APPROVERS.put("david.okonkwo", new ApproverMeta("david.okonkwo", "D. Okonkwo", "DO", "#c47f3f"));
        APPROVERS.put("priya.nair", new ApproverMeta("priya.nair", "P. Nair (HR)", "PN", "#4a9d7a"));
    }

    public static ApproverMeta approver(String id) {
        return APPROVERS.getOrDefault(id, new ApproverMeta(id, "—", "—", "#c7cdd6"));
    }

    // ---------------- date formatting ----------------

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.ENGLISH);

    /** "Sep 29" or "Sep 29 – Oct 3". */
    public static String formatRange(String startIso, String endIso) {
        String s = fmt(startIso);
        return startIso != null && startIso.equals(endIso) ? s : s + " – " + fmt(endIso);
    }

    public static String fmt(String iso) {
        if (iso == null) return "—";
        try {
            return LocalDate.parse(iso).format(SHORT);
        } catch (RuntimeException e) {
            return iso;
        }
    }

    public static String fmtLong(String iso) {
        if (iso == null) return "—";
        try {
            return LocalDate.parse(iso).format(LONG);
        } catch (RuntimeException e) {
            return iso;
        }
    }

    /** epoch millis for a date at a given hour (UTC), used to order the event log. */
    public static long epochAt(String isoDate, int hour) {
        return LocalDate.parse(isoDate).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /** Timeline label like "Jun 28 · 9:00 AM" for a date + hour. */
    public static String stampLabel(String isoDate, int hour) {
        return LocalDate.parse(isoDate).atTime(hour, 0).format(STAMP);
    }

    /** Timeline label for a given epoch-millis instant (decisions happen "now"). */
    public static String stampLabel(long epochMillis) {
        return LocalDateTime.ofEpochSecond(epochMillis / 1000, 0, ZoneOffset.UTC).format(STAMP);
    }
}
