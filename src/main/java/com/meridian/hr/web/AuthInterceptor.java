package com.meridian.hr.web;

import com.meridian.hr.session.SessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Redirects unauthenticated devices to /login. Public paths are excluded in {@link WebConfig}. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionContext session;

    public AuthInterceptor(SessionContext session) {
        this.session = session;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (session.isSignedIn()) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }
}
