package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * An exit case: a leaving employee, their exit type + last day, and a checklist of
 * exit tasks. Completing the case marks the person INACTIVE and writes an exit event
 * to their job history. Ported from the fixture's {@code offboarding-store.js}.
 */
public class OffboardingCase {

    public String id;
    public String empId;
    public String empName;
    public String dept;
    public String title;
    public String type;      // resignation | termination | end_contract | retirement
    public String lastDay;
    public String reason;
    public String initiatedBy;
    public long initiatedAt;
    public String status = "in_progress";   // in_progress | completed
    public long completedAt;
    public final List<Task> checklist = new ArrayList<>();

    public OffboardingCase() {
    }

    /** The standard 7-item exit checklist. */
    public static List<Task> defaultChecklist() {
        List<Task> l = new ArrayList<>();
        l.add(new Task("notice", "Resignation / notice acknowledged", "HR"));
        l.add(new Task("knowledge", "Knowledge transfer & handover plan", "Manager"));
        l.add(new Task("access", "Revoke system access & accounts", "IT"));
        l.add(new Task("assets", "Collect company assets (laptop, badge)", "IT"));
        l.add(new Task("payroll", "Final pay & benefits settlement", "Payroll"));
        l.add(new Task("exit_interview", "Exit interview", "HR"));
        l.add(new Task("records", "Archive employee records", "HR"));
        return l;
    }

    public Task task(String id) {
        for (Task t : checklist) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public static class Task {
        public String id;
        public String label;
        public String owner;
        public boolean done;

        public Task() {
        }

        public Task(String id, String label, String owner) {
            this.id = id;
            this.label = label;
            this.owner = owner;
        }
    }
}
