package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An application attached to a {@link Requisition}: profile, pipeline stage, interviewer
 * scorecards, an optional offer, and — once hired — a link to the created onboarding case.
 * Ported from the fixture's {@code recruitment-store.js} candidate model.
 */
public class Candidate {

    public String id;
    public String reqId;
    public String name;
    public String initials;
    public String bg;
    public String currentRole;
    public String exp;
    public String source;
    public String email;
    public String summary;
    public int fit;
    public String stage = "applied"; // applied|screen|interview|onsite|offer|hired|rejected
    public Long appliedAt;
    public String rejectionReason;
    public Long rejectedAt;
    /** stageId → interviewerId → scorecard. */
    public final Map<String, Map<String, Scorecard>> scorecards = new LinkedHashMap<>();
    public Offer offer;
    public String onboardingCaseId;
    public final List<Note> notes = new ArrayList<>();

    public Candidate() {
    }

    public static class Scorecard {
        public final Map<String, Integer> ratings = new LinkedHashMap<>();
        public String rec;       // strong_yes | yes | no | strong_no
        public String comment;
        public Long submittedAt;
    }

    public static class Offer {
        public int base;
        public int bonus;
        public double equity;
        public String level;
        public String startDate;
        public String status;    // pending_approval | approved | extended | accepted | declined
        public String approverId;
        public Long approvedAt;
        public Long extendedAt;
        public Long acceptedAt;
        public Long declinedAt;
    }

    public static class Note {
        public String authorId;
        public String text;
        public long at;

        public Note() {
        }

        public Note(String authorId, String text, long at) {
            this.authorId = authorId;
            this.text = text;
            this.at = at;
        }
    }
}
