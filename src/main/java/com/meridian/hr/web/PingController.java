package com.meridian.hr.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Temporary boot-check endpoint. Replaced by the real login/redirect flow in Phase 1. */
@RestController
public class PingController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("app", "meridian-hr", "status", "ok");
    }
}
