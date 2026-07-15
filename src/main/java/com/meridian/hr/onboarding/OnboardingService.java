package com.meridian.hr.onboarding;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.OnboardingCase;
import com.meridian.hr.domain.OnboardingTemplate;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Onboarding read + write logic, ported from the fixture's {@code onboarding-store.js}.
 * The core is {@link #resolvePlan}, which COMPUTES each step's status
 * (done → waiting → blocked → in_progress) from completion + doc uploads + dependency
 * order — so completing one step automatically releases whatever waited on it.
 * Operates on the calling device's {@link Workspace} via {@link SessionContext}.
 */
@Service
public class OnboardingService {

    private final SessionContext session;
    private final PeopleService people;

    public OnboardingService(SessionContext session, PeopleService people) {
        this.session = session;
        this.people = people;
    }

    private Workspace ws() {
        return session.workspace();
    }

    // ---------------- reads ----------------

    public List<OnboardingTemplate> templates() {
        return ws().onboardingTemplates;
    }

    public OnboardingTemplate template(String id) {
        if (id == null) return null;
        for (OnboardingTemplate t : templates()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public OnboardingTemplate templateForRole(String role) {
        List<OnboardingTemplate> list = templates();
        for (OnboardingTemplate t : list) {
            if (t.role.equals(role)) return t;
        }
        for (OnboardingTemplate t : list) {
            if ("general".equals(t.role)) return t;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    public List<OnboardingCase> cases() {
        return ws().onboardingCases;
    }

    public OnboardingCase getCase(String id) {
        if (id == null) return null;
        for (OnboardingCase c : cases()) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    // ---------------- status computation ----------------

    /** One resolved step: its definition, current progress state, and computed status. */
    public record Resolved(OnboardingTemplate.Step def, OnboardingCase.StepState state, String status) {
    }

    public List<Resolved> resolvePlan(OnboardingCase c) {
        OnboardingTemplate tpl = template(c.templateId);
        if (tpl == null) return List.of();
        List<OnboardingTemplate.Step> steps = new ArrayList<>(tpl.steps);
        steps.sort(Comparator.comparingInt(s -> s.order));
        List<Resolved> out = new ArrayList<>();
        for (OnboardingTemplate.Step def : steps) {
            OnboardingCase.StepState st = c.steps.get(def.id);
            String status;
            if (st != null && st.completed) {
                status = "done";
            } else if (def.dependsOn != null && !doneOf(c, def.dependsOn)) {
                status = "waiting";
            } else if (def.requiresDoc != null && (st == null || !st.docUploaded)) {
                status = "blocked";
            } else {
                status = "in_progress";
            }
            out.add(new Resolved(def, st != null ? st : new OnboardingCase.StepState(), status));
        }
        return out;
    }

    private boolean doneOf(OnboardingCase c, String stepId) {
        OnboardingCase.StepState s = c.steps.get(stepId);
        return s != null && s.completed;
    }

    public record Progress(int done, int total, int pct) {
    }

    public Progress progressOf(OnboardingCase c) {
        List<Resolved> plan = resolvePlan(c);
        if (plan.isEmpty()) return new Progress(0, 0, 0);
        int done = (int) plan.stream().filter(p -> "done".equals(p.status())).count();
        return new Progress(done, plan.size(), (int) Math.round(done * 100.0 / plan.size()));
    }

    /** Progress + blocked count + a health verdict (complete | blocked | on_track). */
    public record Summary(int done, int total, int pct, int blocked, String health) {
    }

    public Summary caseSummary(OnboardingCase c) {
        List<Resolved> plan = resolvePlan(c);
        Progress prog = progressOf(c);
        int blocked = (int) plan.stream().filter(p -> "blocked".equals(p.status())).count();
        String health;
        if (prog.pct() == 100) health = "complete";
        else if (blocked > 0) health = "blocked";
        else health = "on_track";
        return new Summary(prog.done(), prog.total(), prog.pct(), blocked, health);
    }

    public record ActiveSummary(int total, int active, int blocked) {
    }

    public ActiveSummary activeSummary() {
        int active = 0, blocked = 0;
        for (OnboardingCase c : cases()) {
            String health = caseSummary(c).health();
            if (!"complete".equals(health)) active++;
            if ("blocked".equals(health)) blocked++;
        }
        return new ActiveSummary(cases().size(), active, blocked);
    }

    // ---------------- mutations ----------------

    /** Mark a step done; returns how many dependent steps this released (waiting → not-waiting). */
    public int completeStep(String caseId, String stepId, String byName) {
        OnboardingCase c = getCase(caseId);
        if (c == null) return 0;
        List<Resolved> before = resolvePlan(c);
        OnboardingCase.StepState st = c.steps.computeIfAbsent(stepId, k -> new OnboardingCase.StepState());
        st.completed = true;
        st.completedAt = System.currentTimeMillis();
        st.completedBy = byName;
        List<Resolved> after = resolvePlan(c);
        return releasedBy(before, after);
    }

    public void uploadDoc(String caseId, String stepId) {
        OnboardingCase c = getCase(caseId);
        if (c == null) return;
        c.steps.computeIfAbsent(stepId, k -> new OnboardingCase.StepState()).docUploaded = true;
    }

    public void reopenStep(String caseId, String stepId) {
        OnboardingCase c = getCase(caseId);
        if (c == null) return;
        OnboardingCase.StepState st = c.steps.get(stepId);
        if (st != null) {
            st.completed = false;
            st.completedAt = null;
        }
    }

    private int releasedBy(List<Resolved> before, List<Resolved> after) {
        java.util.Set<String> wasWaiting = new java.util.HashSet<>();
        for (Resolved p : before) {
            if ("waiting".equals(p.status())) wasWaiting.add(p.def().id);
        }
        int n = 0;
        for (Resolved p : after) {
            if (wasWaiting.contains(p.def().id) && !"waiting".equals(p.status())) n++;
        }
        return n;
    }

    private static final String[] AVATARS =
            {"#b56b8f", "#6b7db5", "#6ba58f", "#c07f4f", "#7a6bb5", "#4a9d7a", "#c99b4e"};

    public OnboardingCase startOnboarding(String hireName, String role, String roleLabel,
                                          String startDate, String manager, String email) {
        OnboardingTemplate tpl = templateForRole(role);
        OnboardingMeta.RoleFamily rf = OnboardingMeta.role(role);
        String name = hireName == null ? "" : hireName.trim();
        OnboardingCase c = new OnboardingCase(
                "onb-" + Long.toString(System.currentTimeMillis(), 36) + cases().size(),
                name,
                initialsOf(name),
                AVATARS[cases().size() % AVATARS.length],
                role,
                blank(roleLabel) ? rf.label() : roleLabel.trim(),
                rf.dept(),
                blank(email) ? name.toLowerCase().replaceAll("[^a-z]+", ".") + "@meridian.co" : email.trim(),
                startDate,
                blank(manager) ? "—" : manager.trim(),
                tpl != null ? tpl.id : null,
                System.currentTimeMillis());
        cases().add(0, c); // newest first
        return c;
    }

    // ---------------- convert to directory ----------------

    /** Create the employee record for a completed onboarding and remember the link. */
    public String convert(String caseId) {
        OnboardingCase c = getCase(caseId);
        if (c == null || isConverted(caseId)) return convertedId(caseId);
        String[] parts = c.hireName.trim().split("\\s+", 2);
        String first = parts.length > 0 ? parts[0] : c.hireName;
        String last = parts.length > 1 ? parts[1] : "";
        Employee mgr = findByName(c.manager);
        PeopleService.NewEmployee f = new PeopleService.NewEmployee(
                first, last, c.roleLabel, c.dept, "Mid",
                mgr != null ? mgr.id : null, "Remote", "Remote", c.startDate, null);
        Employee e = people.add(f);
        ws().onboardingConverted.put(caseId, e.id);
        return e.id;
    }

    public boolean isConverted(String caseId) {
        return ws().onboardingConverted.containsKey(caseId);
    }

    public String convertedId(String caseId) {
        return ws().onboardingConverted.get(caseId);
    }

    private Employee findByName(String fullName) {
        if (blank(fullName)) return null;
        for (Employee e : ws().employees) {
            if (e.fullName().equalsIgnoreCase(fullName.trim())) return e;
        }
        return null;
    }

    // ---------------- helpers ----------------

    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length && sb.length() < 2; i++) {
            if (!words[i].isEmpty()) sb.append(Character.toUpperCase(words[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
