package com.meridian.hr.admin;

import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-workspace trail of admin configuration changes (Settings / Org structure /
 * Roles &amp; access). The unified Audit Log synthesizes domain events straight from
 * each domain's own records; admin edits have no domain record of their own, so
 * this component keeps the equivalent event list — keyed weakly by workspace so a
 * device's trail lives and dies with its demo world.
 */
@Component
public class AdminAudit {

    /** One admin change: when, who, what ("Policy updated — work week"), and the diff detail. */
    public record Event(long at, String actor, String action, String detail) {
    }

    private final Map<Workspace, List<Event>> trails = Collections.synchronizedMap(new WeakHashMap<>());

    public void log(Workspace ws, String actorName, String action, String detail) {
        if (ws == null) return;
        trails.computeIfAbsent(ws, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new Event(System.currentTimeMillis(), actorName == null ? "HR" : actorName,
                        action, detail == null ? "" : detail));
    }

    public List<Event> trail(Workspace ws) {
        List<Event> list = trails.get(ws);
        return list == null ? List.of() : new ArrayList<>(list);
    }
}
