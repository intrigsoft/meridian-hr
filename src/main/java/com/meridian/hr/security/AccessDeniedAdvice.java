package com.meridian.hr.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Turns an {@link AccessDeniedException} into a 403 JSON body for the REST/MCP surface.
 * Applies only to {@code @ResponseBody} handlers, so MVC view controllers — which gate with
 * {@link AccessPolicy#can} and render a panel rather than throw — are unaffected. This is the
 * denial contract the (forthcoming) {@code /api} controllers rely on when they call
 * {@link AccessPolicy#require}.
 */
@RestControllerAdvice
public class AccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> onDenied(AccessDeniedException ex) {
        Permission p = ex.required();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", Map.of(
                        "code", "forbidden",
                        "message", "You do not have permission to perform this action.",
                        "requiredPermission", p == null ? "" : p.name())));
    }
}
