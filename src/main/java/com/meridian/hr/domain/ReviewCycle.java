package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A performance review SCHEMA: weighted competencies, a rating scale, a timeline, and
 * the participant set. Status draft → active → closed. Ported from the fixture's
 * {@code performance-store.js} cycle model. Public-field class for SpringEL.
 */
public class ReviewCycle {

    public String id;
    public String name;
    public String type;      // half | annual | quarter | probation
    public String status;    // draft | active | closed
    public String startDate;
    public String selfDue;
    public String mgrDue;
    public String calibrationDate;
    public int scaleMax = 5;
    public final List<CompWeight> competencies = new ArrayList<>();
    public final List<String> participants = new ArrayList<>();
    public long createdAt;
    public String createdBy;

    public ReviewCycle() {
    }

    public ReviewCycle comp(String id, int weight) {
        competencies.add(new CompWeight(id, weight));
        return this;
    }

    /** A competency reference with its weight (%) within a cycle. */
    public static class CompWeight {
        public String id;
        public int weight;

        public CompWeight() {
        }

        public CompWeight(String id, int weight) {
            this.id = id;
            this.weight = weight;
        }
    }
}
