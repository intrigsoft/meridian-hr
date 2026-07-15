package com.meridian.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Meridian HR — a Dioschub sample app.
 *
 * A conventional server-rendered enterprise HR portal (Spring Boot + Thymeleaf).
 * Every visitor gets an isolated, in-memory demo workspace keyed by a device cookie,
 * seeded fresh so anybody can start clean. Memory is bounded by on-access TTL/LRU
 * eviction plus a scheduled sweep — no database.
 *
 * The Dioschub assistant + MCP seam is layered on in a later phase; for now the
 * right rail is an inert placeholder.
 */
@SpringBootApplication
@EnableScheduling
public class MeridianHrApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeridianHrApplication.class, args);
    }
}
