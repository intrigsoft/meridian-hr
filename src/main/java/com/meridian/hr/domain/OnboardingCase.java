package com.meridian.hr.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A running onboarding INSTANCE created from an {@link OnboardingTemplate}. Carries
 * the hire's details plus per-step progress ({@link StepState}); step STATUS is
 * computed (not stored) from completion + document uploads + dependencies, so
 * completing a step automatically releases whatever was waiting on it. Ported from
 * the fixture's {@code cases} model.
 */
public class OnboardingCase {

    public String id;
    public String hireName;
    public String initials;
    public String avatarBg;
    public String role;
    public String roleLabel;
    public String dept;
    public String email;
    public String startDate;
    public String manager;
    public String templateId;
    public long createdAt;

    /** stepId → progress. Absent key = nothing done yet on that step. */
    public final Map<String, StepState> steps = new LinkedHashMap<>();

    public OnboardingCase() {
    }

    public OnboardingCase(String id, String hireName, String initials, String avatarBg, String role,
                          String roleLabel, String dept, String email, String startDate, String manager,
                          String templateId, long createdAt) {
        this.id = id;
        this.hireName = hireName;
        this.initials = initials;
        this.avatarBg = avatarBg;
        this.role = role;
        this.roleLabel = roleLabel;
        this.dept = dept;
        this.email = email;
        this.startDate = startDate;
        this.manager = manager;
        this.templateId = templateId;
        this.createdAt = createdAt;
    }

    /** Seed helper: mark a step complete (chainable). */
    public OnboardingCase done(String stepId, String by, long completedAt, boolean docUploaded) {
        StepState s = new StepState();
        s.completed = true;
        s.completedBy = by;
        s.completedAt = completedAt;
        s.docUploaded = docUploaded;
        steps.put(stepId, s);
        return this;
    }

    public static class StepState {
        public boolean completed;
        public Long completedAt;
        public String completedBy;
        public boolean docUploaded;

        public StepState() {
        }
    }
}
