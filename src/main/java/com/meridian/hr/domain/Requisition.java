package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A job opening (requisition): role details, a scorecard (attribute ids), an interview
 * plan, and an approval + lifecycle status (draft → pending_approval → open → filled/closed).
 * Ported from the fixture's {@code recruitment-store.js} requisition model.
 */
public class Requisition {

    public String id;
    public String title;
    public String dept;
    public String level;
    public String location;
    public int headcount = 1;
    public String status = "draft"; // draft | pending_approval | open | filled | closed
    public String ownerId;          // hiring manager
    public String recruiterId;
    public final List<String> scorecard = new ArrayList<>();   // attribute ids
    public final List<Round> interviewPlan = new ArrayList<>();
    public String approvalStatus = "none"; // none | pending | approved
    public String approverId;
    public Long approvalAt;
    public long createdAt;
    public Long openedAt;
    public Long closedAt;

    public Requisition() {
    }

    public Requisition round(String stageId, String... interviewers) {
        Round r = new Round();
        r.stageId = stageId;
        for (String i : interviewers) r.interviewerIds.add(i);
        interviewPlan.add(r);
        return this;
    }

    public static class Round {
        public String stageId;
        public final List<String> interviewerIds = new ArrayList<>();
    }
}
