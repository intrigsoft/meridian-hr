package com.meridian.hr.security;

/**
 * Thrown when the current actor lacks a required {@link Permission}. Carries the offending
 * permission so handlers can render a precise denial. Mapped to a 403 on the REST/MCP
 * surface; MVC pages generally prefer to gate with {@link AccessPolicy#can} and render a
 * "not available" panel instead of throwing.
 */
public class AccessDeniedException extends RuntimeException {

    private final transient Permission required;

    public AccessDeniedException(Permission required) {
        super("Missing permission: " + (required == null ? "?" : required.name()));
        this.required = required;
    }

    public Permission required() {
        return required;
    }
}
