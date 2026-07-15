package com.meridian.hr.onboarding;

import com.meridian.hr.domain.OnboardingCase;
import com.meridian.hr.domain.OnboardingTemplate;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * People Ops → Onboarding. Two views under one nav item:
 *  - active list (search + status filter) and per-hire status board (the resolved
 *    provisioning plan, with complete/upload/reopen + convert-to-directory actions);
 *  - a read-only Templates tab listing the role schemas (HR only).
 * Start + convert are HR-only; step actions are open to managers and HR.
 * Ports the fixture's {@code Onboarding.dc.html} / {@code New Onboarding.dc.html}.
 */
@Controller
public class OnboardingController {

    private final OnboardingService onboarding;
    private final SessionContext session;
    private final AccessPolicy policy;

    public OnboardingController(OnboardingService onboarding, SessionContext session, AccessPolicy policy) {
        this.onboarding = onboarding;
        this.session = session;
        this.policy = policy;
    }

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    // ===================== list + templates =====================

    @GetMapping("/onboarding")
    public String index(@RequestParam(defaultValue = "active") String tab,
                        @RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "all") String status,
                        Model model) {
        Actor actor = session.actor();
        boolean allowed = policy.can(Permission.ONBOARDING_VIEW);
        model.addAttribute("active", "onboarding");
        model.addAttribute("allowed", allowed);
        model.addAttribute("restricted", !allowed);
        if (!allowed) {
            return "onboarding/onboarding";
        }

        boolean hr = policy.can(Permission.ONBOARDING_ADMIN);
        boolean canManage = policy.can(Permission.ONBOARDING_MANAGE);
        boolean templatesTab = "templates".equals(tab) && hr;

        model.addAttribute("canManage", canManage);
        model.addAttribute("isHr", hr);
        model.addAttribute("showStartBtn", hr && !templatesTab);
        model.addAttribute("tab", templatesTab ? "templates" : "active");
        model.addAttribute("tplCount", onboarding.templates().size());

        OnboardingService.ActiveSummary asum = onboarding.activeSummary();
        model.addAttribute("noteTitle", "Open onboardings");
        model.addAttribute("noteSub", asum.active() + " active · " + asum.blocked() + " blocked");

        if (templatesTab) {
            buildTemplatesView(model);
        } else {
            buildListView(model, q, status);
        }
        return "onboarding/onboarding";
    }

    private void buildListView(Model model, String q, String status) {
        List<OnboardingCase> cases = onboarding.cases();
        int cAll = 0, cOnTrack = 0, cBlocked = 0, cComplete = 0;
        List<Object[]> withSummary = new ArrayList<>();
        for (OnboardingCase c : cases) {
            OnboardingService.Summary s = onboarding.caseSummary(c);
            withSummary.add(new Object[]{c, s});
            cAll++;
            switch (s.health()) {
                case "on_track" -> cOnTrack++;
                case "blocked" -> cBlocked++;
                case "complete" -> cComplete++;
                default -> {
                }
            }
        }

        String needle = q.trim().toLowerCase();
        List<Row> rows = new ArrayList<>();
        for (Object[] pair : withSummary) {
            OnboardingCase c = (OnboardingCase) pair[0];
            OnboardingService.Summary s = (OnboardingService.Summary) pair[1];
            if (!"all".equals(status) && !status.equals(s.health())) continue;
            if (!needle.isEmpty()
                    && !(c.hireName + " " + c.roleLabel + " " + c.dept).toLowerCase().contains(needle)) continue;
            OnboardingTemplate tpl = onboarding.template(c.templateId);
            String health = s.health();
            String barColor = "complete".equals(health) ? "#4a9d7a" : "blocked".equals(health) ? "#c68a2a" : "#3f7cc4";
            String pillBg, pillFg, label;
            if ("complete".equals(health)) {
                pillBg = "#e6f3ec";
                pillFg = "#2f6f4f";
                label = "Complete";
            } else if ("blocked".equals(health)) {
                pillBg = "#fbf1de";
                pillFg = "#9a6a1a";
                label = s.blocked() + " blocked";
            } else {
                pillBg = "#e8eefb";
                pillFg = "#3a5aa8";
                label = "On track";
            }
            rows.add(new Row(c.id, c.hireName, c.roleLabel, c.initials, c.avatarBg,
                    fmtStart(c.startDate), tpl != null ? tpl.name : "—", s.pct(), barColor, pillBg, pillFg, label));
        }

        List<StatusFilter> filters = List.of(
                statusFilter("all", "All", cAll, status),
                statusFilter("on_track", "On track", cOnTrack, status),
                statusFilter("blocked", "Blocked", cBlocked, status),
                statusFilter("complete", "Complete", cComplete, status));

        model.addAttribute("rows", rows);
        model.addAttribute("statusFilters", filters);
        model.addAttribute("q", q);
        model.addAttribute("statusValue", status);
        model.addAttribute("emptyQueue", rows.isEmpty());
        model.addAttribute("emptyTitle", cAll > 0 ? "No matches" : "No active onboardings");
        model.addAttribute("emptySub", cAll > 0 ? "Try a different search or status filter."
                : "Start an onboarding to provision a new hire.");
    }

    private StatusFilter statusFilter(String id, String label, int count, String selected) {
        boolean on = id.equals(selected);
        return new StatusFilter(id, label, count,
                on ? "#eaf1fa" : "#fff", on ? "#bcd0ea" : "#dde2e8", on ? "#17457f" : "#5a6472",
                on ? "#d3e3f6" : "#eef1f4", on ? "#17457f" : "#8894a3");
    }

    private void buildTemplatesView(Model model, String... selArg) {
        List<OnboardingTemplate> tpls = onboarding.templates();
        String sel = selArg.length > 0 ? selArg[0] : (tpls.isEmpty() ? null : tpls.get(0).id);
        List<TplRow> tplRows = new ArrayList<>();
        for (OnboardingTemplate t : tpls) {
            OnboardingMeta.RoleFamily rf = OnboardingMeta.role(t.role);
            tplRows.add(new TplRow(t.id, t.name, rf.label(), t.stepCount(), t.id.equals(sel)));
        }
        model.addAttribute("templates", tplRows);

        OnboardingTemplate draft = onboarding.template(sel);
        model.addAttribute("hasDraft", draft != null);
        if (draft != null) {
            model.addAttribute("dName", draft.name);
            model.addAttribute("dDesc", draft.description == null ? "" : draft.description);
            model.addAttribute("dRoleLabel", OnboardingMeta.role(draft.role).label());
            List<TplStepView> steps = new ArrayList<>();
            List<OnboardingTemplate.Step> ordered = new ArrayList<>(draft.steps);
            ordered.sort((a, b) -> Integer.compare(a.order, b.order));
            for (int i = 0; i < ordered.size(); i++) {
                OnboardingTemplate.Step s = ordered.get(i);
                OnboardingMeta.System sys = OnboardingMeta.system(s.system);
                String dep = s.dependsOn == null ? null : titleOf(ordered, s.dependsOn);
                steps.add(new TplStepView(i + 1, s.title, sys.label(), sys.color(), sys.bg(), s.owner,
                        dueLabel(s.dueOffset), dep, s.autoAssign, s.requiresDoc != null, s.requiresDoc));
            }
            model.addAttribute("tplSteps", steps);
        }
    }

    private static String titleOf(List<OnboardingTemplate.Step> steps, String id) {
        for (OnboardingTemplate.Step s : steps) {
            if (s.id.equals(id)) return s.title;
        }
        return "a prior step";
    }

    @GetMapping("/onboarding/templates/{tplId}")
    public String templatesTab(@PathVariable String tplId, Model model) {
        boolean hr = policy.can(Permission.ONBOARDING_ADMIN);
        if (!hr) return "redirect:/onboarding";
        model.addAttribute("active", "onboarding");
        model.addAttribute("canManage", true);
        model.addAttribute("isHr", true);
        model.addAttribute("showStartBtn", false);
        model.addAttribute("tab", "templates");
        model.addAttribute("tplCount", onboarding.templates().size());
        OnboardingService.ActiveSummary asum = onboarding.activeSummary();
        model.addAttribute("noteTitle", "Open onboardings");
        model.addAttribute("noteSub", asum.active() + " active · " + asum.blocked() + " blocked");
        buildTemplatesView(model, tplId);
        return "onboarding/onboarding";
    }

    // ===================== status board =====================

    @GetMapping("/onboarding/case/{caseId}")
    public String status(@PathVariable String caseId, Model model) {
        boolean hr = policy.can(Permission.ONBOARDING_ADMIN);
        boolean canManage = policy.can(Permission.ONBOARDING_MANAGE);

        model.addAttribute("active", "onboarding");
        model.addAttribute("canManage", canManage);
        model.addAttribute("isHr", hr);
        model.addAttribute("showStartBtn", false);
        model.addAttribute("tab", "active");
        model.addAttribute("mode", "status");
        model.addAttribute("tplCount", onboarding.templates().size());
        OnboardingService.ActiveSummary asum = onboarding.activeSummary();
        model.addAttribute("noteTitle", "Open onboardings");
        model.addAttribute("noteSub", asum.active() + " active · " + asum.blocked() + " blocked");

        OnboardingCase c = onboarding.getCase(caseId);
        model.addAttribute("hasActive", c != null);
        if (c == null) {
            return "onboarding/status";
        }

        OnboardingService.Summary sum = onboarding.caseSummary(c);
        OnboardingTemplate tpl = onboarding.template(c.templateId);
        model.addAttribute("caseId", c.id);
        model.addAttribute("activeName", c.hireName);
        model.addAttribute("activeMeta", c.roleLabel + " · starts " + fmtStart(c.startDate)
                + " · " + (tpl != null ? tpl.name : "no schema") + " · manager " + c.manager);
        model.addAttribute("activePct", sum.pct());
        model.addAttribute("activeDone", sum.done());
        model.addAttribute("activeTotal", sum.total());

        String health = sum.health();
        model.addAttribute("healthLabel", "complete".equals(health) ? "Complete"
                : "blocked".equals(health) ? sum.blocked() + " blocked" : "On track");
        model.addAttribute("healthBg", "complete".equals(health) ? "#e6f3ec"
                : "blocked".equals(health) ? "#fbf1de" : "#e8eefb");
        model.addAttribute("healthFg", "complete".equals(health) ? "#2f6f4f"
                : "blocked".equals(health) ? "#9a6a1a" : "#3a5aa8");

        boolean complete = sum.pct() == 100;
        boolean converted = onboarding.isConverted(c.id);
        model.addAttribute("showConvert", complete && !converted && hr);
        model.addAttribute("isConverted", converted);
        model.addAttribute("convertedId", onboarding.convertedId(c.id));

        model.addAttribute("steps", buildSteps(c));
        return "onboarding/status";
    }

    private List<StepView> buildSteps(OnboardingCase c) {
        List<OnboardingService.Resolved> plan = onboarding.resolvePlan(c);
        List<StepView> out = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            OnboardingService.Resolved p = plan.get(i);
            OnboardingTemplate.Step def = p.def();
            OnboardingCase.StepState st = p.state();
            OnboardingMeta.System sys = OnboardingMeta.system(def.system);
            Style style = styleFor(p.status());

            String detail;
            switch (p.status()) {
                case "done" -> detail = "Completed " + rel(st.completedAt) + " · "
                        + (st.completedBy != null ? st.completedBy : "Dioschub");
                case "waiting" -> detail = "Waiting on: " + depTitle(plan, def.dependsOn);
                case "blocked" -> detail = "Due " + dueStr(c.startDate, def.dueOffset) + " · owner " + def.owner;
                default -> detail = (def.autoAssign
                        ? "Dioschub executes this automatically in " + sys.label()
                        : "Ready · owner " + def.owner) + " · due " + dueStr(c.startDate, def.dueOffset);
            }

            out.add(new StepView(def.id, def.title, sys.label(), sys.color(), sys.bg(), def.owner,
                    style.label, style.stColor, style.dotBg, style.dotBorder, style.glyph, style.glyphColor,
                    i < plan.size() - 1, detail,
                    "blocked".equals(p.status()), "in_progress".equals(p.status()),
                    "done".equals(p.status()), "waiting".equals(p.status()),
                    def.requiresDoc != null ? def.requiresDoc : "document",
                    def.autoAssign ? "Complete (simulate run)" : "Mark done"));
        }
        return out;
    }

    private static String depTitle(List<OnboardingService.Resolved> plan, String depId) {
        for (OnboardingService.Resolved p : plan) {
            if (p.def().id.equals(depId)) return p.def().title;
        }
        return "a prior step";
    }

    // ===================== new onboarding =====================

    @GetMapping("/onboarding/new")
    public String newForm(@RequestParam(defaultValue = "design") String role,
                          @RequestParam(required = false) String name,
                          @RequestParam(required = false) String title,
                          @RequestParam(required = false) String start,
                          @RequestParam(required = false) String manager,
                          @RequestParam(required = false) String email,
                          Model model) {
        boolean hr = policy.can(Permission.ONBOARDING_ADMIN);
        model.addAttribute("active", "onboarding");

        if (!hr) {
            model.addAttribute("noAccess", true);
            model.addAttribute("hasAccess", false);
            return "onboarding/new-onboarding";
        }
        model.addAttribute("noAccess", false);
        model.addAttribute("hasAccess", true);

        String startDate = start == null || start.isBlank() ? "2026-07-20" : start;
        model.addAttribute("fName", name == null ? "" : name);
        model.addAttribute("fTitle", title == null ? "" : title);
        model.addAttribute("fStart", startDate);
        model.addAttribute("fManager", manager == null ? "" : manager);
        model.addAttribute("fEmail", email == null ? "" : email);
        model.addAttribute("selRole", role);
        model.addAttribute("emailPlaceholder", derivedEmail(name));
        model.addAttribute("operator", session.currentUser() != null ? session.currentUser().email : "");

        List<RoleOption> roleOptions = new ArrayList<>();
        for (OnboardingMeta.RoleFamily rf : OnboardingMeta.roles()) {
            OnboardingTemplate tpl = onboarding.templateForRole(rf.id());
            roleOptions.add(new RoleOption(rf.id(), rf.label(),
                    tpl != null ? tpl.name : "—", tpl != null ? tpl.stepCount() : 0, rf.id().equals(role)));
        }
        model.addAttribute("roleOptions", roleOptions);

        OnboardingTemplate tpl = onboarding.templateForRole(role);
        List<OnboardingTemplate.Step> ordered = tpl == null ? List.of() : new ArrayList<>(tpl.steps);
        ordered = new ArrayList<>(ordered);
        ordered.sort((a, b) -> Integer.compare(a.order, b.order));
        List<PreviewRow> preview = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            OnboardingTemplate.Step s = ordered.get(i);
            OnboardingMeta.System sys = OnboardingMeta.system(s.system);
            Integer depOrder = s.dependsOn == null ? null : orderOf(ordered, s.dependsOn);
            preview.add(new PreviewRow(i + 1, s.title, sys.label(), sys.color(), sys.bg(),
                    s.autoAssign, s.requiresDoc != null, s.requiresDoc, s.dependsOn != null, depOrder));
        }
        model.addAttribute("preview", preview);
        model.addAttribute("previewSub", (tpl != null ? tpl.name : "No schema") + " · " + ordered.size() + " steps");
        model.addAttribute("startCount", ordered.size());
        model.addAttribute("needName", name == null || name.isBlank());
        return "onboarding/new-onboarding";
    }

    @PostMapping("/onboarding/new")
    public String start(@RequestParam(defaultValue = "design") String role,
                        @RequestParam(required = false) String name,
                        @RequestParam(required = false) String title,
                        @RequestParam(required = false) String start,
                        @RequestParam(required = false) String manager,
                        @RequestParam(required = false) String email,
                        RedirectAttributes ra) {
        if (!policy.can(Permission.ONBOARDING_ADMIN)) return "redirect:/onboarding";
        if (name == null || name.isBlank()) {
            return "redirect:/onboarding/new?role=" + role;
        }
        OnboardingCase c = onboarding.startOnboarding(name.trim(), role, title, start, manager, email);
        ra.addFlashAttribute("toast", "Onboarding started for " + c.hireName + " — Dioschub is provisioning.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/onboarding/case/" + c.id;
    }

    // ===================== step actions =====================

    @PostMapping("/onboarding/case/{caseId}/step/{stepId}/complete")
    public String complete(@PathVariable String caseId, @PathVariable String stepId,
                           @RequestParam String title, RedirectAttributes ra) {
        if (!policy.can(Permission.ONBOARDING_MANAGE)) return "redirect:/onboarding/case/" + caseId;
        int released = onboarding.completeStep(caseId, stepId, actorName());
        ra.addFlashAttribute("toast", released > 0
                ? "\"" + title + "\" done — Dioschub released " + released + " dependent step"
                    + (released > 1 ? "s" : "") + "."
                : "\"" + title + "\" marked done.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/onboarding/case/" + caseId;
    }

    @PostMapping("/onboarding/case/{caseId}/step/{stepId}/upload")
    public String upload(@PathVariable String caseId, @PathVariable String stepId,
                         @RequestParam String doc, RedirectAttributes ra) {
        if (!policy.can(Permission.ONBOARDING_MANAGE)) return "redirect:/onboarding/case/" + caseId;
        onboarding.uploadDoc(caseId, stepId);
        ra.addFlashAttribute("toast", doc + " verified — Dioschub released the held write.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/onboarding/case/" + caseId;
    }

    @PostMapping("/onboarding/case/{caseId}/step/{stepId}/reopen")
    public String reopen(@PathVariable String caseId, @PathVariable String stepId,
                         @RequestParam String title, RedirectAttributes ra) {
        if (!policy.can(Permission.ONBOARDING_MANAGE)) return "redirect:/onboarding/case/" + caseId;
        onboarding.reopenStep(caseId, stepId);
        ra.addFlashAttribute("toast", "\"" + title + "\" reopened.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return "redirect:/onboarding/case/" + caseId;
    }

    @PostMapping("/onboarding/case/{caseId}/convert")
    public String convert(@PathVariable String caseId, RedirectAttributes ra) {
        if (!policy.can(Permission.ONBOARDING_ADMIN)) return "redirect:/onboarding/case/" + caseId;
        String empId = onboarding.convert(caseId);
        OnboardingCase c = onboarding.getCase(caseId);
        ra.addFlashAttribute("toast", empId != null && c != null
                ? c.hireName + " added to the employee directory."
                : "Could not convert this onboarding.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/onboarding/case/" + caseId;
    }

    // ===================== helpers =====================

    private String actorName() {
        return policy.can(Permission.ONBOARDING_ADMIN) ? "P. Nair (HR)" : "You";
    }

    private static String derivedEmail(String name) {
        if (name == null || name.isBlank()) return "name@meridian.co";
        String slug = name.toLowerCase().replaceAll("[^a-z\\s]", "").trim().replaceAll("\\s+", ".");
        return slug.isEmpty() ? "name@meridian.co" : slug + "@meridian.co";
    }

    private static String fmtStart(String iso) {
        try {
            return LocalDate.parse(iso).format(LONG_DATE);
        } catch (RuntimeException e) {
            return iso;
        }
    }

    private static String dueStr(String startIso, int offset) {
        try {
            return LocalDate.parse(startIso).plusDays(offset).format(SHORT_DATE);
        } catch (RuntimeException e) {
            return "—";
        }
    }

    private static String dueLabel(int offset) {
        if (offset == 0) return "on start day";
        return offset < 0 ? Math.abs(offset) + "d before start" : offset + "d after start";
    }

    private static Integer orderOf(List<OnboardingTemplate.Step> steps, String id) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id.equals(id)) return i + 1;
        }
        return null;
    }

    private static String rel(Long ts) {
        if (ts == null) return "";
        long days = Math.round((System.currentTimeMillis() - ts) / 86400000.0);
        if (days <= 0) return "today";
        if (days == 1) return "yesterday";
        return days + " days ago";
    }

    private record Style(String label, String stColor, String dotBg, String dotBorder, String glyph, String glyphColor) {
    }

    private static Style styleFor(String status) {
        return switch (status) {
            case "done" -> new Style("Done", "#2f6f4f", "#4a9d7a", "#4a9d7a", "✓", "#fff");
            case "in_progress" -> new Style("In progress", "#2f6aa8", "#eaf1fa", "#3f7cc4", "•", "#3f7cc4");
            case "blocked" -> new Style("Blocked", "#9a6a1a", "#fbf1de", "#c68a2a", "!", "#9a6a1a");
            default -> new Style("Waiting", "#9aa3ad", "#f1f4f7", "#cdd4dc", "", "#cdd4dc");
        };
    }

    // ===================== view records =====================

    public record Row(String id, String name, String role, String initials, String avatarBg,
                      String startFmt, String schema, int pct, String barColor,
                      String pillBg, String pillFg, String statusLabel) {
    }

    public record StatusFilter(String id, String label, int count,
                               String bg, String border, String fg, String countBg, String countFg) {
    }

    public record StepView(String stepId, String title, String sysLabel, String sysColor, String sysBg, String owner,
                           String statusLabel, String stColor, String dotBg, String dotBorder, String glyph, String glyphColor,
                           boolean showRail, String detail,
                           boolean isBlocked, boolean isProgress, boolean isDone, boolean isWaiting,
                           String blockDoc, String progressBtn) {
    }

    public record TplRow(String id, String name, String roleLabel, int stepCount, boolean on) {
    }

    public record TplStepView(int order, String title, String sysLabel, String sysColor, String sysBg, String owner,
                              String dueLabel, String dependsOn, boolean auto, boolean hasDoc, String docName) {
    }

    public record RoleOption(String id, String label, String tplName, int stepCount, boolean on) {
    }

    public record PreviewRow(int order, String title, String sysLabel, String sysColor, String sysBg,
                             boolean auto, boolean hasDoc, String docName, boolean hasDep, Integer depOrder) {
    }
}
