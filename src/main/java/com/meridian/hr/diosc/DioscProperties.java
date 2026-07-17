package com.meridian.hr.diosc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the embedded Dioschub assistant (meridian.diosc.* in
 * application.yml / env). Empty until the AI-integration phase — while unset,
 * the assistant rail renders as an inert placeholder. When set, the layout's
 * assistant fragment drops in the real {@code <diosc-chat>} element + loader.
 */
@Component
@ConfigurationProperties(prefix = "meridian.diosc")
public class DioscProperties {

    /** Hub base URL, e.g. http://localhost:3333 */
    private String hubUrl = "";
    /** Public per-assistant embed key. */
    private String embedKey = "";
    /** Assistant UUID. */
    private String assistantId = "";
    /** Host BYOA bind route the kit POSTs to. */
    private String bindEndpoint = "/api/diosc/bind";
    /** Secret hub admin API key (scope auth:bind) used server-to-server. Never rendered. */
    private String bindKey = "";

    /** True once the assistant is configured enough to mount the real kit. */
    public boolean isConfigured() {
        return !hubUrl.isBlank() && !embedKey.isBlank() && !assistantId.isBlank();
    }

    public String getHubUrl() {
        return hubUrl;
    }

    public void setHubUrl(String hubUrl) {
        this.hubUrl = hubUrl;
    }

    public String getEmbedKey() {
        return embedKey;
    }

    public void setEmbedKey(String embedKey) {
        this.embedKey = embedKey;
    }

    public String getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(String assistantId) {
        this.assistantId = assistantId;
    }

    public String getBindEndpoint() {
        return bindEndpoint;
    }

    public void setBindEndpoint(String bindEndpoint) {
        this.bindEndpoint = bindEndpoint;
    }

    public String getBindKey() {
        return bindKey;
    }

    public void setBindKey(String bindKey) {
        this.bindKey = bindKey;
    }
}
