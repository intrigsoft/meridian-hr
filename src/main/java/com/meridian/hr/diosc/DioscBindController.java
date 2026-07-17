package com.meridian.hr.diosc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.hr.domain.Employee;
import com.meridian.hr.session.DeviceFilter;
import com.meridian.hr.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The host side of Dioschub's BYOA "bind, don't carry" flow.
 *
 * The embedded kit never holds credentials. When the hub asks a fresh WS
 * connection to authenticate, the kit POSTs {@code {wsId}} here (same-origin,
 * cookies included). We resolve the caller from the {@code meridian_device}
 * cookie — the exact same session Meridian's own pages trust — and push the
 * identity plus an opaque auth artifact server-to-server to the hub:
 *
 * <pre>
 *   Authorization: Bearer session:&lt;meridian_device&gt;
 * </pre>
 *
 * The hub stores that artifact keyed by the WS connection and forwards it,
 * verbatim, as the Authorization header on every MCP tool call — so the
 * MCP adapter acts as this user through Meridian's front door, and the LLM
 * never sees the cookie. Not signed in → anonymous bind (identity null).
 */
@RestController
@RequestMapping("/api/diosc")
public class DioscBindController {

    private static final Logger log = LoggerFactory.getLogger(DioscBindController.class);
    // HTTP/1.1 explicitly: the JDK client's default h2c upgrade dance makes the
    // hub's Node HTTP server drop the connection ("header parser received no bytes").
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final DioscProperties props;
    private final SessionContext session;
    private final ObjectMapper mapper;

    public DioscBindController(DioscProperties props, SessionContext session, ObjectMapper mapper) {
        this.props = props;
        this.session = session;
        this.mapper = mapper;
    }

    @PostMapping("/bind")
    public ResponseEntity<Map<String, Object>> bind(@RequestBody(required = false) Map<String, Object> body)
            throws Exception {
        String wsId = body != null && body.get("wsId") instanceof String s && !s.isBlank() ? s : null;
        if (wsId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wsId is required"));
        }
        if (!props.isConfigured() || props.getBindKey().isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "assistant not configured"));
        }

        Employee u = session.currentUser();
        String device = session.deviceId();

        // Identity + artifact only when there is a signed-in Meridian session to speak for.
        Map<String, Object> identity = null;
        Map<String, String> headers = new HashMap<>();
        if (u != null && device != null) {
            identity = new LinkedHashMap<>();
            identity.put("userId", u.id);
            identity.put("username", (u.first + " " + u.last).trim());
            identity.put("role", Map.of("id", u.accessRole.key, "name", u.accessRole.key));
            headers.put("Authorization", "Bearer session:" + device);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wsId", wsId);
        payload.put("identity", identity);
        payload.put("authArtifacts", Map.of("headers", headers));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getHubUrl() + "/api/auth/bind"))
                .timeout(Duration.ofSeconds(10))
                .header("content-type", "application/json")
                .header("x-api-key", props.getBindKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            // Never log the payload — it carries the device cookie.
            log.error("dioschub /auth/bind failed ({}) for ws={} user={}", res.statusCode(), wsId,
                    u == null ? "anonymous" : u.id);
            return ResponseEntity.status(502).body(Map.of("error", "bind failed"));
        }

        log.info("bound ws={} as {} ({})", wsId, u == null ? "anonymous" : u.id,
                u == null ? "-" : u.accessRole.key);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Compile-time nudge: the artifact is the same cookie {@link DeviceFilter#COOKIE} mints. */
    @SuppressWarnings("unused")
    private static final String ARTIFACT_SOURCE = DeviceFilter.COOKIE;
}
