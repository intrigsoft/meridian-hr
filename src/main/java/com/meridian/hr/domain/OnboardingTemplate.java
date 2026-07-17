package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A role-based onboarding SCHEMA: an ordered list of provisioning {@link Step}
 * definitions. A new hire's role picks the template; a {@link OnboardingCase} is an
 * instance created from it. Public-field class (SpringEL-friendly), fluent builders
 * for concise seeding. Ported from the fixture's {@code onboarding-store.js} templates.
 */
public class OnboardingTemplate {

    public String id;
    public String name;
    public String role;   // key into OnboardingMeta role families
    public String dept;
    public String description;
    public long updatedAt;

    /**
     * Frozen per-case snapshot flag. Template edits must only affect FUTURE cases, so
     * before the first edit of a template that running cases still point at, those cases
     * are repointed to an archived copy (copy-on-write). Archived templates resolve by id
     * (so old boards keep rendering) but are hidden from the Templates tab and role pickers.
     */
    public boolean archived;

    public final List<Step> steps = new ArrayList<>();

    public OnboardingTemplate() {
    }

    public OnboardingTemplate(String id, String name, String role, String dept, String description) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.dept = dept;
        this.description = description;
    }

    public OnboardingTemplate step(Step s) {
        steps.add(s);
        return this;
    }

    public int stepCount() {
        return steps.size();
    }

    /** Deep copy under a new id (used for the archived copy-on-write snapshots). */
    public OnboardingTemplate copy(String newId) {
        OnboardingTemplate t = new OnboardingTemplate(newId, name, role, dept, description);
        t.updatedAt = updatedAt;
        for (Step s : steps) {
            t.steps.add(s.copy());
        }
        return t;
    }

    /** One provisioning step definition inside a template. */
    public static class Step {
        public String id;
        public int order;
        public String title;
        public String system;       // key into OnboardingMeta.SYSTEMS
        public String owner;        // one of OnboardingMeta.OWNERS
        public String requiresDoc;  // document name, or null
        public String dependsOn;    // step id this waits on, or null
        public boolean autoAssign;  // Dioschub fires it automatically once unblocked
        public int dueOffset;       // days from the hire's start date

        public Step() {
        }

        public Step(int order, String id, String title, String system, String owner) {
            this.order = order;
            this.id = id;
            this.title = title;
            this.system = system;
            this.owner = owner;
        }

        public Step doc(String d) {
            this.requiresDoc = d;
            return this;
        }

        public Step depends(String d) {
            this.dependsOn = d;
            return this;
        }

        public Step auto() {
            this.autoAssign = true;
            return this;
        }

        public Step due(int off) {
            this.dueOffset = off;
            return this;
        }

        /** Field-for-field copy (same step id — ids are local to their template). */
        public Step copy() {
            Step s = new Step(order, id, title, system, owner);
            s.requiresDoc = requiresDoc;
            s.dependsOn = dependsOn;
            s.autoAssign = autoAssign;
            s.dueOffset = dueOffset;
            return s;
        }
    }
}
