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

    /** Live (non-archived) templates — what the Templates tab and role pickers see. */
    public List<OnboardingTemplate> templates() {
        List<OnboardingTemplate> out = new ArrayList<>();
        for (OnboardingTemplate t : ws().onboardingTemplates) {
            if (!t.archived) out.add(t);
        }
        return out;
    }

    /** Resolve by id across live AND archived templates, so old case boards keep rendering. */
    public OnboardingTemplate template(String id) {
        if (id == null) return null;
        for (OnboardingTemplate t : ws().onboardingTemplates) {
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

    // ---------------- template editing (HR admin) ----------------
    //
    // Template edits affect FUTURE cases only. Cases resolve their plan LIVE from the
    // template (see resolvePlan), so before the FIRST mutation of a template that any
    // existing case still points at, those cases are repointed to a frozen archived
    // snapshot copy (copy-on-write). Subsequent edits find no cases on the live template
    // and mutate it in place; cases started after an edit reference the live template
    // again and get snapshotted by the next edit. Seeded templates/cases need no special
    // handling — the same lazy snapshot covers them.

    /** New template with the design's starter step; returned so the caller can open its editor. */
    public OnboardingTemplate createTemplate(String name, String role) {
        OnboardingMeta.RoleFamily rf = OnboardingMeta.role(role);
        OnboardingTemplate t = new OnboardingTemplate(newId("tpl-"),
                blank(name) ? "New template" : name.trim(), rf.id(), rf.dept(), "");
        t.step(new OnboardingTemplate.Step(1, "identity", "Create identity & directory account",
                "azure_ad", "IT").auto().due(-2));
        t.updatedAt = System.currentTimeMillis();
        ws().onboardingTemplates.add(t);
        return t;
    }

    /**
     * Delete a live template. Returns the number of ACTIVE (non-complete) cases still
     * using it — {@code > 0} means the delete was blocked and nothing changed. If only
     * completed cases reference it, it is archived instead of removed so their boards
     * keep rendering; unreferenced templates are removed outright.
     */
    public int deleteTemplate(String id) {
        OnboardingTemplate t = editable(id);
        if (t == null) return 0;
        boolean referenced = false;
        int active = 0;
        for (OnboardingCase c : cases()) {
            // Match the whole template FAMILY: cases repointed to an archived
            // "<id>-v<ts>" snapshot by copy-on-write still count as using this
            // template — otherwise one edit quietly disarms the delete guard.
            if (t.id.equals(c.templateId) || (c.templateId != null && c.templateId.startsWith(t.id + "-v"))) {
                referenced = true;
                if (!"complete".equals(caseSummary(c).health())) active++;
            }
        }
        if (active > 0) return active;
        if (referenced) {
            t.archived = true;
        } else {
            ws().onboardingTemplates.remove(t);
        }
        return 0;
    }

    /** Edit name / role / description; dept follows the role family. */
    public void updateTemplateMeta(String id, String name, String role, String description) {
        OnboardingTemplate t = editable(id);
        if (t == null) return;
        snapshotForExistingCases(t);
        if (!blank(name)) t.name = name.trim();
        if (!blank(role)) {
            OnboardingMeta.RoleFamily rf = OnboardingMeta.role(role);
            t.role = rf.id();
            t.dept = rf.dept();
        }
        t.description = description == null ? "" : description.trim();
        touch(t);
    }

    /** Append the design's {@code newStepDef} defaults; returns the new step (null if no template). */
    public OnboardingTemplate.Step addStep(String tplId) {
        OnboardingTemplate t = editable(tplId);
        if (t == null) return null;
        snapshotForExistingCases(t);
        OnboardingTemplate.Step s = new OnboardingTemplate.Step(t.steps.size() + 1,
                newStepId(t), "New step", "manual", "People Ops");
        t.steps.add(s);
        renumber(t);
        touch(t);
        return s;
    }

    /** Save one step's fields. Null {@code dueOffset} keeps the current value; doc name only applies while the doc toggle is on. */
    public void updateStep(String tplId, String stepId, String title, String system, String owner,
                           Integer dueOffset, String dependsOn, String docName) {
        OnboardingTemplate t = editable(tplId);
        OnboardingTemplate.Step s = stepOf(t, stepId);
        if (s == null) return;
        snapshotForExistingCases(t);
        if (!blank(title)) s.title = title.trim();
        if (!blank(system) && OnboardingMeta.system(system).id().equals(system)) s.system = system;
        if (!blank(owner) && OnboardingMeta.OWNERS.contains(owner)) s.owner = owner;
        if (dueOffset != null) s.dueOffset = dueOffset;
        s.dependsOn = blank(dependsOn) || dependsOn.equals(stepId) || stepOf(t, dependsOn) == null
                ? null : dependsOn;
        if (s.requiresDoc != null && !blank(docName)) s.requiresDoc = docName.trim();
        touch(t);
    }

    /** Flip "Dioschub executes automatically" for one step. */
    public void toggleAuto(String tplId, String stepId) {
        OnboardingTemplate t = editable(tplId);
        OnboardingTemplate.Step s = stepOf(t, stepId);
        if (s == null) return;
        snapshotForExistingCases(t);
        s.autoAssign = !s.autoAssign;
        touch(t);
    }

    /** Flip "requires document" for one step (on = the design's default doc name). */
    public void toggleDoc(String tplId, String stepId) {
        OnboardingTemplate t = editable(tplId);
        OnboardingTemplate.Step s = stepOf(t, stepId);
        if (s == null) return;
        snapshotForExistingCases(t);
        s.requiresDoc = s.requiresDoc == null ? "Signed document" : null;
        touch(t);
    }

    /** Swap a step with its neighbour ({@code dir} = -1 up / +1 down) and renumber. */
    public void moveStep(String tplId, String stepId, int dir) {
        OnboardingTemplate t = editable(tplId);
        if (t == null) return;
        ordered(t);
        int i = indexOf(t, stepId);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= t.steps.size()) return;
        snapshotForExistingCases(t);
        // Swap the ORDER VALUES, not the list slots — renumber() re-sorts by order
        // first, so a positional swap alone gets silently sorted straight back.
        OnboardingTemplate.Step a = t.steps.get(i);
        OnboardingTemplate.Step b = t.steps.get(j);
        int tmp = a.order;
        a.order = b.order;
        b.order = tmp;
        renumber(t);
        touch(t);
    }

    /** Remove a step; anything that depended on it now runs immediately (design behaviour). */
    public void deleteStep(String tplId, String stepId) {
        OnboardingTemplate t = editable(tplId);
        OnboardingTemplate.Step s = stepOf(t, stepId);
        if (s == null) return;
        snapshotForExistingCases(t);
        t.steps.remove(s);
        for (OnboardingTemplate.Step other : t.steps) {
            if (stepId.equals(other.dependsOn)) other.dependsOn = null;
        }
        renumber(t);
        touch(t);
    }

    /** The live template behind {@code id} — archived snapshots are never editable. */
    private OnboardingTemplate editable(String id) {
        OnboardingTemplate t = template(id);
        return t == null || t.archived ? null : t;
    }

    /** Copy-on-write: repoint every case still on {@code t} to a frozen archived snapshot. */
    private void snapshotForExistingCases(OnboardingTemplate t) {
        List<OnboardingCase> using = new ArrayList<>();
        for (OnboardingCase c : cases()) {
            if (t.id.equals(c.templateId)) using.add(c);
        }
        if (using.isEmpty()) return;
        OnboardingTemplate snap = t.copy(newId(t.id + "-v"));
        snap.archived = true;
        ws().onboardingTemplates.add(snap);
        for (OnboardingCase c : using) {
            c.templateId = snap.id;
        }
    }

    private OnboardingTemplate.Step stepOf(OnboardingTemplate t, String stepId) {
        if (t == null || stepId == null) return null;
        for (OnboardingTemplate.Step s : t.steps) {
            if (s.id.equals(stepId)) return s;
        }
        return null;
    }

    private int indexOf(OnboardingTemplate t, String stepId) {
        for (int i = 0; i < t.steps.size(); i++) {
            if (t.steps.get(i).id.equals(stepId)) return i;
        }
        return -1;
    }

    /** Keep the list sorted by order, then rewrite order = position (1-based). */
    private void renumber(OnboardingTemplate t) {
        ordered(t);
        for (int i = 0; i < t.steps.size(); i++) {
            t.steps.get(i).order = i + 1;
        }
    }

    private void ordered(OnboardingTemplate t) {
        t.steps.sort(Comparator.comparingInt(s -> s.order));
    }

    private void touch(OnboardingTemplate t) {
        t.updatedAt = System.currentTimeMillis();
    }

    private String newId(String prefix) {
        String id = prefix + Long.toString(System.currentTimeMillis(), 36)
                + Long.toString((long) (Math.random() * 1296), 36);
        return template(id) == null ? id : newId(prefix);
    }

    private String newStepId(OnboardingTemplate t) {
        String id;
        do {
            id = "st" + Long.toString((long) (Math.random() * 60466176L), 36);
        } while (stepOf(t, id) != null);
        return id;
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
