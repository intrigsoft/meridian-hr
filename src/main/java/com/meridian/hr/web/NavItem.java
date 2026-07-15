package com.meridian.hr.web;

/**
 * One left-nav entry. {@code built} flips true as each domain's phase lands, so the
 * shell renders unbuilt destinations as disabled rather than dead 404 links.
 */
public record NavItem(String key, String label, String href, String icon, boolean built, Integer badge) {
}
