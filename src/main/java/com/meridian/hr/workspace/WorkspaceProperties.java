package com.meridian.hr.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Bounds for the in-memory demo-workspace store (meridian.workspace.* in application.yml). */
@Component
@ConfigurationProperties(prefix = "meridian.workspace")
public class WorkspaceProperties {

    /** Evict a workspace after this much idle time. */
    private Duration idleTtl = Duration.ofHours(2);

    /** Hard cap on concurrent workspaces; least-recently-seen are evicted beyond it. */
    private int maxDevices = 2000;

    public Duration getIdleTtl() {
        return idleTtl;
    }

    public void setIdleTtl(Duration idleTtl) {
        this.idleTtl = idleTtl;
    }

    public int getMaxDevices() {
        return maxDevices;
    }

    public void setMaxDevices(int maxDevices) {
        this.maxDevices = maxDevices;
    }
}
