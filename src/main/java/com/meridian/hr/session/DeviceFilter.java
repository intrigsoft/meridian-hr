package com.meridian.hr.session;

import com.meridian.hr.workspace.WorkspaceStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * The device-cookie chokepoint: runs before every request, resolves (or mints) the
 * caller's isolated demo workspace and stashes it on the request. This is where a
 * first-time visitor "starts fresh". Later this same filter grows a machine branch
 * for the BYOA bearer path (the Dioschub assistant acting as the human).
 */
@Component
@Order(1)
public class DeviceFilter extends OncePerRequestFilter {

    public static final String COOKIE = "meridian_device";
    public static final String ATTR_DEVICE_ID = "meridian.deviceId";
    public static final String ATTR_WORKSPACE = "meridian.workspace";

    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(7);

    private final WorkspaceStore store;

    public DeviceFilter(WorkspaceStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        WorkspaceStore.Lookup lookup = store.getOrCreate(readCookie(request));
        if (lookup.isNew()) {
            ResponseCookie cookie = ResponseCookie.from(COOKIE, lookup.deviceId())
                    .path("/")
                    .httpOnly(true)
                    .sameSite("Lax")
                    .secure(!isLocal(request.getServerName()))
                    .maxAge(COOKIE_MAX_AGE)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        request.setAttribute(ATTR_DEVICE_ID, lookup.deviceId());
        request.setAttribute(ATTR_WORKSPACE, lookup.workspace());

        chain.doFilter(request, response);
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static boolean isLocal(String host) {
        return host == null
                || host.contains("localhost")
                || host.startsWith("127.")
                || host.equals("0.0.0.0")
                || host.equals("[::1]");
    }
}
