package com.meridian.hr.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-device workspace registry. A first-time visitor (no/unknown cookie) mints a
 * fresh {@link Seed}-built workspace, so anybody can start clean. Memory is bounded
 * two ways, mirroring the other samples' constants (IDLE_TTL 2h / MAX_DEVICES 2000):
 * lazily on every access, AND — because HR workspaces are heavier and the user asked
 * for a schedule — by a periodic {@link #sweep()} pass. No database; a restart wipes
 * everything back to seed.
 */
@Service
public class WorkspaceStore {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceStore.class);

    private final WorkspaceProperties props;
    private final Map<String, Entry> devices = new ConcurrentHashMap<>();

    public WorkspaceStore(WorkspaceProperties props) {
        this.props = props;
    }

    /** Resolve (or mint) the workspace for a cookie value. */
    public Lookup getOrCreate(String cookieId) {
        evictStale();
        if (cookieId != null) {
            Entry existing = devices.get(cookieId);
            if (existing != null) {
                existing.lastSeen = now();
                return new Lookup(cookieId, existing.workspace, false);
            }
        }
        String deviceId = UUID.randomUUID().toString();
        Entry entry = new Entry(Seed.build(), now());
        devices.put(deviceId, entry);
        enforceCap();
        return new Lookup(deviceId, entry.workspace, true);
    }

    /** Read-only peek used by the machine (BYOA) path later; null if unknown. */
    public Workspace peek(String deviceId) {
        Entry e = deviceId == null ? null : devices.get(deviceId);
        if (e == null) return null;
        e.lastSeen = now();
        return e.workspace;
    }

    /** Restore a device to a brand-new seeded workspace ("start fresh"). */
    public void resetDevice(String deviceId) {
        if (deviceId == null) return;
        devices.put(deviceId, new Entry(Seed.build(), now()));
    }

    public int size() {
        return devices.size();
    }

    // ----- bounding -----

    /** Scheduled belt-and-suspenders sweep (interval from meridian.workspace.sweep-interval). */
    @Scheduled(fixedDelayString = "${meridian.workspace.sweep-interval:300000}")
    public void sweep() {
        int before = devices.size();
        evictStale();
        enforceCap();
        int evicted = before - devices.size();
        if (evicted > 0) {
            log.info("workspace sweep: evicted {} stale/overflow devices ({} remain)", evicted, devices.size());
        }
    }

    private void evictStale() {
        long cutoff = now() - props.getIdleTtl().toMillis();
        devices.entrySet().removeIf(e -> e.getValue().lastSeen < cutoff);
    }

    private void enforceCap() {
        int max = props.getMaxDevices();
        while (devices.size() > max) {
            devices.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().lastSeen))
                    .map(Map.Entry::getKey)
                    .ifPresent(devices::remove);
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** Result of a cookie lookup: the resolved device id, its workspace, and whether it was just minted. */
    public record Lookup(String deviceId, Workspace workspace, boolean isNew) {
    }

    private static final class Entry {
        final Workspace workspace;
        volatile long lastSeen;

        Entry(Workspace workspace, long lastSeen) {
            this.workspace = workspace;
            this.lastSeen = lastSeen;
        }
    }
}
