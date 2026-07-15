package com.meridian.hr.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A running review INSTANCE — one per participant per {@link ReviewCycle}. Carries the
 * self, manager, and calibrated score maps + narratives. The review's STATUS is computed
 * (awaiting_self → awaiting_manager → in_calibration → committed) from what's submitted.
 * Ported from the fixture's {@code performance-store.js} review model.
 */
public class Review {

    public String cycleId;
    public String empId;
    public String reviewerId;
    public final Assessment self = new Assessment();
    public final Assessment mgr = new Assessment();
    public final Calibration cal = new Calibration();

    public Review() {
    }

    public Review(String cycleId, String empId, String reviewerId) {
        this.cycleId = cycleId;
        this.empId = empId;
        this.reviewerId = reviewerId;
    }

    public static class Assessment {
        public final Map<String, Integer> scores = new LinkedHashMap<>();
        public String narrative = "";
        public Long submittedAt;   // null until submitted
    }

    public static class Calibration {
        public final Map<String, Integer> scores = new LinkedHashMap<>();
        public boolean started;      // true once seeded from the manager assessment
        public boolean committed;
        public Long committedAt;
    }
}
