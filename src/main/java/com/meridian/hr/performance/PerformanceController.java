package com.meridian.hr.performance;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.Review;
import com.meridian.hr.domain.ReviewCycle;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.session.Actor;
import com.meridian.hr.session.SessionContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * People Ops → Performance. HR runs cycles (Cycles / Reviews / Reports tabs); managers see
 * their team's reviews; employees see their own. The per-employee Review page drives the
 * self → manager → calibration flow. The Cycle Designer is fully editable for DRAFT cycles
 * (auto-save per change via small PRG forms) and locked once active/closed. Ports the
 * fixture's {@code Performance.dc.html} + {@code Cycle Designer.dc.html} + {@code Review.dc.html}.
 */
@Controller
public class PerformanceController {

    private final PerformanceService perf;
    private final PeopleService people;
    private final SessionContext session;
    private final AccessPolicy policy;

    public PerformanceController(PerformanceService perf, PeopleService people, SessionContext session,
                                 AccessPolicy policy) {
        this.perf = perf;
        this.people = people;
        this.session = session;
        this.policy = policy;
    }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    // ===================== main tabs =====================

    @GetMapping("/performance")
    public String index(@RequestParam(required = false) String tab,
                        @RequestParam(required = false) String cycle,
                        @RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "all") String status,
                        Model model) {
        Actor actor = session.actor();
        boolean isHr = policy.can(Permission.PERF_CYCLE_ADMIN);
        boolean isManager = actor != null && actor.role() == com.meridian.hr.domain.Role.MANAGER;

        String view = tab != null ? tab : (isHr ? "cycles" : "reviews");
        if (!isHr && ("cycles".equals(view) || "reports".equals(view))) view = "reviews";

        List<ReviewCycle> selectable = perf.selectableCycles();
        String selId = cycle;
        if (perf.getCycle(selId) == null || "draft".equals(safeStatus(selId))) {
            ReviewCycle a = perf.activeCycle();
            selId = a != null && !"draft".equals(a.status) ? a.id : (selectable.isEmpty() ? null : selectable.get(0).id);
        }
        ReviewCycle selCycle = perf.getCycle(selId);

        model.addAttribute("active", "performance");
        model.addAttribute("isHr", isHr);
        model.addAttribute("tab", view);
        model.addAttribute("selectedCycle", selId);

        // tabs
        List<TabView> tabs = new ArrayList<>();
        if (isHr) {
            tabs.add(new TabView("cycles", "Cycles", true, perf.allCycles().size(), "cycles".equals(view)));
            tabs.add(new TabView("reviews", "Reviews", false, 0, "reviews".equals(view)));
            tabs.add(new TabView("reports", "Reports", false, 0, "reports".equals(view)));
        } else {
            tabs.add(new TabView("reviews", isManager ? "My team" : "My review", false, 0, true));
        }
        model.addAttribute("tabs", tabs);

        List<CycleOption> opts = new ArrayList<>();
        for (ReviewCycle c : selectable) {
            opts.add(new CycleOption(c.id, c.name + ("active".equals(c.status) ? " · active" : " · closed"), c.id.equals(selId)));
        }
        model.addAttribute("cycleOptions", opts);
        model.addAttribute("showCycleSelect", ("reviews".equals(view) || "reports".equals(view)) && !opts.isEmpty());

        String heading = switch (view) {
            case "cycles" -> "Review cycles";
            case "reports" -> "Reports · " + (selCycle != null ? selCycle.name : "");
            default -> isManager ? "My team's reviews" : isHr ? "Reviews · " + (selCycle != null ? selCycle.name : "") : "My review";
        };
        model.addAttribute("heading", heading);

        if ("cycles".equals(view)) {
            buildCycles(model);
        } else if ("reports".equals(view)) {
            buildReports(model, selId);
        } else {
            buildReviews(model, actor, isHr, isManager, selId, q, status);
        }
        return "performance/performance";
    }

    private String safeStatus(String cycleId) {
        ReviewCycle c = perf.getCycle(cycleId);
        return c == null ? "" : c.status;
    }

    private void buildCycles(Model model) {
        List<CycleCard> cards = new ArrayList<>();
        for (ReviewCycle c : perf.allCycles()) {
            PerformanceService.Completion comp = perf.completionFor(c.id);
            String bg, fg, label;
            switch (c.status) {
                case "active" -> { bg = "#e8eefb"; fg = "#3a5aa8"; label = "Active"; }
                case "closed" -> { bg = "#eef1f4"; fg = "#6b7480"; label = "Closed"; }
                default -> { bg = "#f7f1e0"; fg = "#9a6a1a"; label = "Draft"; }
            }
            boolean draft = "draft".equals(c.status);
            cards.add(new CycleCard(c.id, c.name, PerformanceMeta.cycleTypeLabel(c.type),
                    fmt(c.startDate) + " – " + fmt(c.calibrationDate), c.participants.size(),
                    bg, fg, label, draft, !draft, !draft, comp.pct(), comp.done(),
                    "closed".equals(c.status) ? "#4a9d7a" : "#3f7cc4",
                    draft && perf.launchable(c), "active".equals(c.status), draft));
        }
        model.addAttribute("cycleCards", cards);
    }

    private void buildReviews(Model model, Actor actor, boolean isHr, boolean isManager,
                              String selId, String q, String status) {
        ReviewCycle cy = perf.getCycle(selId);
        List<PerformanceService.PerEmp> raw = cy == null ? List.of() : perf.perEmployee(selId);
        List<PerformanceService.PerEmp> scoped = new ArrayList<>();
        for (PerformanceService.PerEmp r : raw) {
            if (isHr) scoped.add(r);
            else if (isManager) {
                Review rv = perf.getReview(selId, r.empId());
                if (rv != null && actor.userId().equals(rv.reviewerId)) scoped.add(r);
            } else if (r.empId().equals(actor.userId())) scoped.add(r);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("all", scoped.size());
        counts.put("awaiting_self", 0);
        counts.put("awaiting_manager", 0);
        counts.put("in_calibration", 0);
        counts.put("committed", 0);
        for (PerformanceService.PerEmp r : scoped) counts.merge(r.status(), 1, Integer::sum);

        String needle = q.trim().toLowerCase();
        List<ReviewRow> rows = new ArrayList<>();
        for (PerformanceService.PerEmp r : scoped) {
            if (!"all".equals(status) && !status.equals(r.status())) continue;
            if (!needle.isEmpty() && !(r.name() + " " + r.dept() + " " + r.title()).toLowerCase().contains(needle)) continue;
            PerformanceMeta.StatusMeta m = PerformanceMeta.status(r.status());
            rows.add(new ReviewRow(r.empId(), r.name(), r.initials(), r.avatarBg(), r.title(), r.dept(), r.reviewer(),
                    m.label(), m.fg(),
                    seg(m, 1), seg(m, 2), seg(m, 3), seg(m, 4),
                    fmt1(r.self()), fmt1(r.mgr()), fmt1(r.cal()), r.cal() != null ? "#2f6f4f" : "#c3cad3"));
        }

        List<StatusFilter> filters = new ArrayList<>();
        String[][] defs = {{"all", "All"}, {"awaiting_self", "Self"}, {"awaiting_manager", "Manager"},
                {"in_calibration", "Calibrate"}, {"committed", "Done"}};
        for (String[] d : defs) {
            boolean on = d[0].equals(status);
            filters.add(new StatusFilter(d[0], d[1], counts.getOrDefault(d[0], 0),
                    on ? "#eaf1fa" : "#fff", on ? "#bcd0ea" : "#dde2e8", on ? "#17457f" : "#5a6472",
                    on ? "#d3e3f6" : "#eef1f4", on ? "#17457f" : "#8894a3"));
        }

        model.addAttribute("reviewRows", rows);
        model.addAttribute("statusFilters", filters);
        model.addAttribute("q", q);
        model.addAttribute("statusValue", status);
        model.addAttribute("emptyReviews", rows.isEmpty());
        model.addAttribute("emptyTitle", scoped.isEmpty() ? "No reviews in this cycle" : "No matches");
        model.addAttribute("emptySub", scoped.isEmpty() ? "Launch a cycle to generate reviews." : "Try a different search or filter.");
    }

    private static String seg(PerformanceMeta.StatusMeta m, int n) {
        return m.step() >= n ? m.bar() : "#e9edf1";
    }

    private void buildReports(Model model, String selId) {
        ReviewCycle cy = perf.getCycle(selId);
        PerformanceService.Completion comp = cy == null
                ? new PerformanceService.Completion(Map.of(), 0, 0, 0) : perf.completionFor(selId);
        int tot = comp.total() == 0 ? 1 : comp.total();
        model.addAttribute("compDone", comp.done());
        model.addAttribute("compTotal", comp.total());
        model.addAttribute("compBarCommitted", Math.round((comp.counts().getOrDefault("committed", 0)) * 100f / tot));
        model.addAttribute("compBarCal", Math.round((comp.counts().getOrDefault("in_calibration", 0)) * 100f / tot));
        model.addAttribute("compBarMgr", Math.round((comp.counts().getOrDefault("awaiting_manager", 0)) * 100f / tot));
        model.addAttribute("compBarSelf", Math.round((comp.counts().getOrDefault("awaiting_self", 0)) * 100f / tot));
        model.addAttribute("compTiles", List.of(
                new CompTile("Awaiting self", comp.counts().getOrDefault("awaiting_self", 0), "#c7cdd6"),
                new CompTile("Awaiting mgr", comp.counts().getOrDefault("awaiting_manager", 0), "#c68a2a"),
                new CompTile("In calibration", comp.counts().getOrDefault("in_calibration", 0), "#3f7cc4"),
                new CompTile("Committed", comp.counts().getOrDefault("committed", 0), "#4a9d7a")));

        model.addAttribute("distBars", cy == null ? List.of() : perf.distributionFor(selId));

        List<GapView> gaps = new ArrayList<>();
        if (cy != null) {
            for (PerformanceService.GapRow g : perf.gapsFor(selId)) {
                double d = g.delta();
                String dl = d > 0.05 ? "+" + fmt1(d) : d < -0.05 ? fmt1(d) : "aligned";
                gaps.add(new GapView(g.name(), (int) Math.round(g.self() / 5 * 100), (int) Math.round(g.mgr() / 5 * 100),
                        fmt1(g.self()), fmt1(g.mgr()), dl,
                        Math.abs(d) < 0.25 ? "#6b7480" : d > 0 ? "#b23b2e" : "#2f6f4f",
                        Math.abs(d) < 0.25 ? "#f1f4f7" : d > 0 ? "#fbeae8" : "#e6f3ec"));
            }
        }
        model.addAttribute("gapRows", gaps);

        List<SummaryRow> summary = new ArrayList<>();
        if (cy != null) {
            for (PerformanceService.PerEmp r : perf.perEmployee(selId)) {
                PerformanceMeta.Band band = PerformanceMeta.scoreBand(r.cal());
                boolean has = r.cal() != null;
                summary.add(new SummaryRow(r.name(), r.initials(), r.avatarBg(), r.dept(), r.reviewer(),
                        fmt1(r.self()), fmt1(r.mgr()), fmt1(r.cal()), has ? "#2f6f4f" : "#c3cad3",
                        has ? band.label() : "—", has ? band.bg() : "#f1f4f7", has ? band.color() : "#9aa3ad"));
            }
        }
        model.addAttribute("summaryRows", summary);
    }

    // ===================== cycle actions =====================

    @PostMapping("/performance/cycle/new")
    public String newCycle(RedirectAttributes ra) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        ReviewCycle c = perf.createCycle("New review cycle");
        return "redirect:/performance/designer?cycle=" + c.id;
    }

    @PostMapping("/performance/cycle/{id}/launch")
    public String launch(@org.springframework.web.bind.annotation.PathVariable String id, RedirectAttributes ra) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        ReviewCycle c = perf.getCycle(id);
        if (c == null) return "redirect:/performance?tab=cycles";
        if (!"draft".equals(c.status)) return "redirect:/performance?tab=cycles";
        if (!perf.launchable(c)) {
            ra.addFlashAttribute("toast",
                    "Can't launch yet — a cycle needs a name, weights totalling exactly 100%, and at least one participant.");
            ra.addFlashAttribute("toastDot", "#c0563f");
            return "redirect:/performance/designer?cycle=" + id;
        }
        perf.launchCycle(id);
        ra.addFlashAttribute("toast", "\"" + c.name + "\" launched — self-assessments are now open.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/performance?tab=reviews&cycle=" + id;
    }

    @PostMapping("/performance/cycle/{id}/close")
    public String close(@org.springframework.web.bind.annotation.PathVariable String id, RedirectAttributes ra) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        perf.closeCycle(id);
        ra.addFlashAttribute("toast", "Cycle closed.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return "redirect:/performance?tab=cycles";
    }

    @PostMapping("/performance/cycle/{id}/delete")
    public String delete(@org.springframework.web.bind.annotation.PathVariable String id, RedirectAttributes ra) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        ReviewCycle c = perf.getCycle(id);
        if (perf.deleteCycle(id)) {
            ra.addFlashAttribute("toast", "Draft \"" + (c != null ? c.name : "") + "\" deleted.");
            ra.addFlashAttribute("toastDot", "#8894a3");
        }
        return "redirect:/performance?tab=cycles";
    }

    // ===================== cycle designer =====================

    private static final String[] ACCENTS = {"#3f7cc4", "#4a9d7a", "#c68a2a", "#9a6ab5", "#c0563f",
            "#5a8fb5", "#b58f4a", "#6ba58f", "#b56b8f", "#7a6bb5"};

    @GetMapping("/performance/designer")
    public String designer(@RequestParam String cycle, Model model) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        ReviewCycle c = perf.getCycle(cycle);
        if (c == null) return "redirect:/performance";
        boolean isDraft = "draft".equals(c.status);
        boolean locked = !isDraft;

        model.addAttribute("active", "performance");
        model.addAttribute("cycle", c);
        model.addAttribute("isDraft", isDraft);
        model.addAttribute("locked", locked);
        String statusBg, statusFg, statusLabel;
        switch (c.status) {
            case "active" -> { statusBg = "#e8eefb"; statusFg = "#3a5aa8"; statusLabel = "Active"; }
            case "closed" -> { statusBg = "#eef1f4"; statusFg = "#6b7480"; statusLabel = "Closed"; }
            default -> { statusBg = "#f7f1e0"; statusFg = "#9a6a1a"; statusLabel = "Draft"; }
        }
        model.addAttribute("statusBg", statusBg);
        model.addAttribute("statusFg", statusFg);
        model.addAttribute("statusLabel", statusLabel);

        // Cycle & timeline
        List<TypeOption> typeOptions = new ArrayList<>();
        for (Map.Entry<String, String> en : PerformanceMeta.cycleTypes().entrySet()) {
            typeOptions.add(new TypeOption(en.getKey(), en.getValue(), en.getKey().equals(c.type)));
        }
        model.addAttribute("typeOptions", typeOptions);

        // Competencies & weights
        List<DesignComp> comps = new ArrayList<>();
        boolean removable = isDraft && c.competencies.size() > 1;
        for (int i = 0; i < c.competencies.size(); i++) {
            ReviewCycle.CompWeight cw = c.competencies.get(i);
            PerformanceMeta.Competency meta = perf.competencyOf(cw.id);
            comps.add(new DesignComp(cw.id, meta.name(), meta.blurb(), cw.weight,
                    ACCENTS[i % ACCENTS.length], removable));
        }
        model.addAttribute("comps", comps);
        List<PerformanceMeta.Competency> addable = new ArrayList<>();
        for (PerformanceMeta.Competency lib : perf.catalog()) {
            boolean chosen = false;
            for (ReviewCycle.CompWeight cw : c.competencies) {
                if (cw.id.equals(lib.id())) chosen = true;
            }
            if (!chosen) addable.add(lib);
        }
        model.addAttribute("addable", addable);
        int weightTotal = perf.weightTotal(c);
        boolean weightOk = weightTotal == 100;
        model.addAttribute("weightTotal", weightTotal);
        model.addAttribute("weightFg", weightOk ? "#2f6f4f" : "#9a6a1a");
        model.addAttribute("weightBg", weightOk ? "#e6f3ec" : "#f7f1e0");

        // Participants (per-department toggle cards over ACTIVE employees)
        List<DeptRow> deptRows = new ArrayList<>();
        Map<String, List<String>> byDept = new LinkedHashMap<>();
        for (String d : people.departmentNames()) byDept.put(d, new ArrayList<>());
        for (Employee e : perf.reviewables()) {
            byDept.computeIfAbsent(e.dept, k -> new ArrayList<>()).add(e.id);
        }
        for (Map.Entry<String, List<String>> en : byDept.entrySet()) {
            List<String> ids = en.getValue();
            if (ids.isEmpty()) continue;
            int sel = 0;
            for (String eid : ids) {
                if (c.participants.contains(eid)) sel++;
            }
            boolean allIn = sel == ids.size();
            deptRows.add(new DeptRow(en.getKey(), ids.size(), sel, allIn,
                    allIn ? "#bcd0ea" : "#e4e8ed", allIn ? "#f2f7fc" : "#fff",
                    allIn ? "#17457f" : "#cdd4dc", allIn ? "#17457f" : "#fff"));
        }
        model.addAttribute("deptRows", deptRows);
        model.addAttribute("partTotal", c.participants.size());

        // Launch gate
        boolean launchOk = isDraft && perf.launchable(c);
        model.addAttribute("launchOk", launchOk);
        model.addAttribute("launchBg", launchOk ? "#17457f" : "#e4e8ed");
        model.addAttribute("launchFg", launchOk ? "#fff" : "#9aa3ad");
        return "performance/designer";
    }

    private String backToDesigner(String cycle) {
        return "redirect:/performance/designer?cycle=" + cycle;
    }

    @PostMapping("/performance/designer/name")
    public String designerName(@RequestParam String cycle, @RequestParam(defaultValue = "") String name) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        perf.renameCycle(cycle, name);
        return backToDesigner(cycle);
    }

    @PostMapping("/performance/designer/timeline")
    public String designerTimeline(@RequestParam String cycle,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String selfDue,
                                   @RequestParam(required = false) String mgrDue,
                                   @RequestParam(required = false) String calibrationDate) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        perf.updateSchedule(cycle, type, startDate, selfDue, mgrDue, calibrationDate);
        return backToDesigner(cycle);
    }

    @PostMapping("/performance/designer/weight")
    public String designerWeight(@RequestParam String cycle, @RequestParam String comp,
                                 @RequestParam(defaultValue = "0") String weight) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        int w = 0;
        try {
            w = Integer.parseInt(weight.trim());
        } catch (NumberFormatException ignore) {
        }
        perf.setWeight(cycle, comp, w);
        return backToDesigner(cycle);
    }

    @PostMapping("/performance/designer/comp/add")
    public String designerAddComp(@RequestParam String cycle, @RequestParam(defaultValue = "") String comp) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        perf.addCompetency(cycle, comp);
        return backToDesigner(cycle);
    }

    @PostMapping("/performance/designer/comp/remove")
    public String designerRemoveComp(@RequestParam String cycle, @RequestParam String comp) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        perf.removeCompetency(cycle, comp);
        return backToDesigner(cycle);
    }

    @PostMapping("/performance/designer/balance")
    public String designerBalance(@RequestParam String cycle) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        perf.balanceWeights(cycle);
        return backToDesigner(cycle);
    }

    @PostMapping("/performance/designer/dept")
    public String designerToggleDept(@RequestParam String cycle, @RequestParam String dept) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return "redirect:/performance";
        perf.toggleDepartment(cycle, dept);
        return backToDesigner(cycle);
    }

    // ===================== review page =====================

    @GetMapping("/performance/review")
    public String review(@RequestParam String cycle, @RequestParam String emp, Model model) {
        Actor actor = session.actor();
        ReviewCycle cy = perf.getCycle(cycle);
        Review r = perf.getReview(cycle, emp);
        Employee e = people.get(emp);
        if (actor == null || cy == null || r == null || e == null) return "redirect:/performance";

        boolean isHr = policy.can(Permission.PERF_CYCLE_ADMIN);
        boolean isReviewer = actor.userId().equals(r.reviewerId);
        boolean isSelf = actor.userId().equals(emp);
        // Visibility: HR, the reviewer, or the employee themselves.
        if (!isHr && !isReviewer && !isSelf) return "redirect:/performance";

        String status = perf.reviewStatus(r);
        model.addAttribute("active", "performance");
        model.addAttribute("cycle", cy);
        model.addAttribute("emp", e);
        model.addAttribute("empName", e.fullName());
        model.addAttribute("reviewerName", perf.reviewerName(r.reviewerId));
        PerformanceMeta.StatusMeta sm = PerformanceMeta.status(status);
        model.addAttribute("stageLabel", sm.label());
        model.addAttribute("stageBg", sm.bg());
        model.addAttribute("stageFg", sm.fg());

        boolean selfSubmitted = r.self.submittedAt != null;
        boolean mgrSubmitted = r.mgr.submittedAt != null;
        boolean committed = r.cal.committed;

        model.addAttribute("selfSubmitted", selfSubmitted);
        model.addAttribute("mgrSubmitted", mgrSubmitted);
        model.addAttribute("committed", committed);
        model.addAttribute("selfNarrative", r.self.narrative);
        model.addAttribute("mgrNarrative", r.mgr.narrative);

        model.addAttribute("canEditSelf", (isSelf || isHr) && !selfSubmitted);
        model.addAttribute("canEditMgr", (isReviewer || isHr) && selfSubmitted && !mgrSubmitted);
        model.addAttribute("canCalibrate", (isReviewer || isHr) && mgrSubmitted && !committed);
        model.addAttribute("canReopen", isHr && committed);
        model.addAttribute("showMgrCol", mgrSubmitted || (isReviewer || isHr) && selfSubmitted);
        model.addAttribute("showCalCol", r.cal.started);

        List<CompScoreRow> comps = new ArrayList<>();
        for (ReviewCycle.CompWeight cw : cy.competencies) {
            PerformanceMeta.Competency meta = perf.competencyOf(cw.id);
            comps.add(new CompScoreRow(cw.id, meta.name(), meta.blurb(), cw.weight,
                    r.self.scores.get(cw.id), r.mgr.scores.get(cw.id), r.cal.scores.get(cw.id)));
        }
        model.addAttribute("comps", comps);
        model.addAttribute("scores", List.of(1, 2, 3, 4, 5));
        model.addAttribute("selfAvg", fmt1(perf.selfAvg(cy, r)));
        model.addAttribute("mgrAvg", fmt1(perf.mgrAvg(cy, r)));
        model.addAttribute("calAvg", fmt1(perf.calAvg(cy, r)));
        return "performance/review";
    }

    @PostMapping("/performance/review/self")
    public String submitSelf(@RequestParam String cycle, @RequestParam String emp,
                             @RequestParam(required = false) String narrative,
                             @RequestParam Map<String, String> all, RedirectAttributes ra) {
        Actor actor = session.actor();
        Review r = perf.getReview(cycle, emp);
        if (actor == null || r == null) return "redirect:/performance";
        if (!(actor.userId().equals(emp) || policy.can(Permission.PERF_CYCLE_ADMIN))) return backToReview(cycle, emp);
        perf.submitSelf(cycle, emp, readScores(cycle, all), narrative);
        ra.addFlashAttribute("toast", "Self-assessment submitted.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return backToReview(cycle, emp);
    }

    @PostMapping("/performance/review/manager")
    public String submitManager(@RequestParam String cycle, @RequestParam String emp,
                                @RequestParam(required = false) String narrative,
                                @RequestParam Map<String, String> all, RedirectAttributes ra) {
        Actor actor = session.actor();
        Review r = perf.getReview(cycle, emp);
        if (actor == null || r == null) return "redirect:/performance";
        if (!(actor.userId().equals(r.reviewerId) || policy.can(Permission.PERF_CYCLE_ADMIN))) return backToReview(cycle, emp);
        perf.submitManager(cycle, emp, readScores(cycle, all), narrative);
        ra.addFlashAttribute("toast", "Manager assessment submitted — calibration open.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return backToReview(cycle, emp);
    }

    @PostMapping("/performance/review/commit")
    public String commit(@RequestParam String cycle, @RequestParam String emp,
                         @RequestParam Map<String, String> all, RedirectAttributes ra) {
        Actor actor = session.actor();
        Review r = perf.getReview(cycle, emp);
        if (actor == null || r == null) return "redirect:/performance";
        boolean allowed = policy.can(Permission.PERF_CYCLE_ADMIN) || actor.userId().equals(r.reviewerId);
        if (!allowed) return backToReview(cycle, emp);
        for (Map.Entry<String, Integer> en : readScores(cycle, all).entrySet()) {
            perf.setCalibrated(cycle, emp, en.getKey(), en.getValue());
        }
        perf.commit(cycle, emp);
        ra.addFlashAttribute("toast", "Calibration committed.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return backToReview(cycle, emp);
    }

    @PostMapping("/performance/review/reopen")
    public String reopen(@RequestParam String cycle, @RequestParam String emp, RedirectAttributes ra) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) return backToReview(cycle, emp);
        perf.reopen(cycle, emp);
        ra.addFlashAttribute("toast", "Calibration reopened.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return backToReview(cycle, emp);
    }

    private String backToReview(String cycle, String emp) {
        return "redirect:/performance/review?cycle=" + cycle + "&emp=" + emp;
    }

    private Map<String, Integer> readScores(String cycleId, Map<String, String> all) {
        ReviewCycle cy = perf.getCycle(cycleId);
        Map<String, Integer> out = new LinkedHashMap<>();
        if (cy == null) return out;
        for (ReviewCycle.CompWeight cw : cy.competencies) {
            String raw = all.get("s_" + cw.id);
            if (raw != null && !raw.isBlank()) {
                try {
                    out.put(cw.id, Math.max(1, Math.min(5, Integer.parseInt(raw.trim()))));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return out;
    }

    // ===================== CSV export =====================

    @GetMapping("/performance/export")
    @ResponseBody
    public ResponseEntity<byte[]> export(@RequestParam String cycle) {
        if (!policy.can(Permission.PERF_CYCLE_ADMIN)) {
            return ResponseEntity.status(403).build();
        }
        ReviewCycle cy = perf.getCycle(cycle);
        StringBuilder sb = new StringBuilder("Employee,Title,Department,Reviewer,Status,Self,Manager,Calibrated\n");
        for (PerformanceService.PerEmp r : perf.perEmployee(cycle)) {
            sb.append(csv(r.name())).append(',').append(csv(r.title())).append(',').append(csv(r.dept())).append(',')
                    .append(csv(r.reviewer())).append(',').append(csv(PerformanceMeta.status(r.status()).label())).append(',')
                    .append(fmt1(r.self())).append(',').append(fmt1(r.mgr())).append(',').append(fmt1(r.cal())).append('\n');
        }
        String fname = (cy != null ? cy.name.replaceAll("\\s+", "-") : "cycle") + "-summary.csv";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("text/csv"));
        h.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename(fname).build());
        return new ResponseEntity<>(sb.toString().getBytes(StandardCharsets.UTF_8), h, 200);
    }

    private static String csv(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    // ---- helpers ----

    private static String fmt(String iso) {
        try {
            return LocalDate.parse(iso).format(SHORT);
        } catch (RuntimeException e) {
            return iso;
        }
    }

    private static String fmt1(Double v) {
        return v == null ? "—" : String.format(Locale.US, "%.1f", v);
    }

    // ===================== view records =====================

    public record TabView(String key, String label, boolean showCount, int count, boolean on) {
    }

    public record CycleOption(String id, String label, boolean on) {
    }

    public record CycleCard(String id, String name, String typeLabel, String dateRange, int partCount,
                            String statusBg, String statusFg, String statusLabel,
                            boolean canDesign, boolean canView, boolean showProgress,
                            int pct, int done, String barColor,
                            boolean launchOk, boolean canClose, boolean canDelete) {
    }

    public record StatusFilter(String id, String label, int count,
                               String bg, String border, String fg, String countBg, String countFg) {
    }

    public record ReviewRow(String empId, String name, String initials, String avatarBg, String title, String dept,
                            String reviewer, String stageLabel, String stageFg,
                            String seg1, String seg2, String seg3, String seg4,
                            String selfAvg, String mgrAvg, String calAvg, String calFg) {
    }

    public record CompTile(String label, int count, String color) {
    }

    public record GapView(String name, int selfPct, int mgrPct, String selfVal, String mgrVal,
                          String deltaLabel, String deltaFg, String deltaBg) {
    }

    public record SummaryRow(String name, String initials, String avatarBg, String dept, String reviewer,
                             String self, String mgr, String cal, String calFg,
                             String band, String bandBg, String bandFg) {
    }

    public record DesignComp(String id, String name, String blurb, int weight, String accent, boolean removable) {
    }

    public record TypeOption(String id, String label, boolean on) {
    }

    public record DeptRow(String dept, int total, int selected, boolean allIn,
                          String border, String bg, String boxBorder, String boxBg) {
    }

    public record CompScoreRow(String id, String name, String blurb, int weight,
                               Integer selfScore, Integer mgrScore, Integer calScore) {
    }
}
