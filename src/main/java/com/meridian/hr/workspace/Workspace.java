package com.meridian.hr.workspace;

import com.meridian.hr.domain.Candidate;
import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.LeaveRequest;
import com.meridian.hr.domain.JobChange;
import com.meridian.hr.domain.OffboardingCase;
import com.meridian.hr.domain.OnboardingCase;
import com.meridian.hr.domain.OnboardingTemplate;
import com.meridian.hr.domain.OrgConfig;
import com.meridian.hr.domain.PolicyConfig;
import com.meridian.hr.domain.ProfileChange;
import com.meridian.hr.domain.Requisition;
import com.meridian.hr.domain.Review;
import com.meridian.hr.domain.ReviewCycle;
import com.meridian.hr.domain.Timesheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One visitor's isolated, in-memory demo world. Everything a device can see or
 * change lives here — the equivalent of the fixture's per-user localStorage, but
 * server-side and per-device. A fresh Workspace is built by {@link Seed}; edits
 * mutate this object in place. Later domains (leave, time, onboarding, ...) add
 * their own collections here as those phases land.
 */
public class Workspace {

    /** The user this device is signed in as (id), or null if on the login screen. */
    public String signedInUserId;

    public final List<Employee> employees = new ArrayList<>();
    public OrgConfig org = new OrgConfig();
    public PolicyConfig policy = new PolicyConfig();

    /** Leave requests (newest first). Notifications derive from their event logs. */
    public final List<LeaveRequest> leaveRequests = new ArrayList<>();

    /** Per-user "notifications last read at" (epoch millis), for the unread badge. */
    public final Map<String, Long> leaveReadAt = new HashMap<>();

    /** Timesheets (one per employee+week) and live "clocked-in since" stamps per user. */
    public final List<Timesheet> timesheets = new ArrayList<>();
    public final Map<String, Long> clockIns = new HashMap<>();

    /** Sensitive-field change requests awaiting HR approval (self-service profile edits). */
    public final List<ProfileChange> profileChanges = new ArrayList<>();

    /** Onboarding role schemas and running cases; converted maps caseId → new employee id. */
    public final List<OnboardingTemplate> onboardingTemplates = new ArrayList<>();
    public final List<OnboardingCase> onboardingCases = new ArrayList<>();
    public final Map<String, String> onboardingConverted = new HashMap<>();

    /** Exit cases (offboarding checklists); completing one flips the employee to INACTIVE. */
    public final List<OffboardingCase> offboardingCases = new ArrayList<>();

    /** Job-change requests (promotion/transfer/comp); approval applies now or on the effective date. */
    public final List<JobChange> jobChanges = new ArrayList<>();

    /** Performance review cycles (schemas) and review instances (one per participant per cycle). */
    public final List<ReviewCycle> reviewCycles = new ArrayList<>();
    public final List<Review> reviews = new ArrayList<>();

    /** Recruitment: job requisitions and their candidate pipelines. */
    public final List<Requisition> requisitions = new ArrayList<>();
    public final List<Candidate> candidates = new ArrayList<>();

    public Employee employee(String id) {
        if (id == null) return null;
        for (Employee e : employees) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    public Employee signedInUser() {
        return employee(signedInUserId);
    }

    public boolean isSignedIn() {
        return signedInUser() != null;
    }
}
