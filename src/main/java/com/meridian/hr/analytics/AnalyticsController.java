package com.meridian.hr.analytics;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.performance.PerformanceService;
import com.meridian.hr.recruitment.RecruitmentService;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * People Ops → Analytics. A read-only workforce dashboard derived from the single people
 * population plus cross-domain signals (open requisitions, active onboardings, review
 * completion). Managers and HR only. Ports the fixture's {@code Analytics.dc.html}.
 */
@Controller
public class AnalyticsController {

    private final PeopleService people;
    private final RecruitmentService recruitment;
    private final PerformanceService performance;
    private final SessionContext session;
    private final AccessPolicy policy;

    public AnalyticsController(PeopleService people, RecruitmentService recruitment,
                               PerformanceService performance, SessionContext session,
                               AccessPolicy policy) {
        this.people = people;
        this.recruitment = recruitment;
        this.performance = performance;
        this.session = session;
        this.policy = policy;
    }

    @GetMapping("/analytics")
    public String index(Model model) {
        boolean allowed = policy.can(Permission.ANALYTICS_VIEW);
        model.addAttribute("active", "analytics");
        model.addAttribute("allowed", allowed);
        model.addAttribute("restricted", !allowed);
        if (!allowed) return "analytics/analytics";

        PeopleService.Stats stats = people.stats();
        List<Employee> all = people.all();
        int inactive = (int) all.stream().filter(e -> e.status == EmployeeStatus.INACTIVE).count();

        int openReqs = 0;
        for (var r : recruitment.reqs()) if ("open".equals(r.status)) openReqs++;
        model.addAttribute("kpis", List.of(
                new Kpi("Headcount", String.valueOf(stats.headcount()), stats.teams() + " teams"),
                new Kpi("Active", String.valueOf(stats.active()), "currently working"),
                new Kpi("Onboarding", String.valueOf(stats.onboarding()), "ramping up"),
                new Kpi("On leave", String.valueOf(stats.onLeave()), "away now"),
                new Kpi("Open roles", String.valueOf(openReqs), "hiring"),
                new Kpi("Avg tenure", stats.avgTenureLabel(), "across the company")));

        // Headcount by department
        List<Bar> byDept = new ArrayList<>();
        int deptMax = 1;
        for (PeopleService.DeptCount d : stats.byDept()) deptMax = Math.max(deptMax, d.count());
        for (PeopleService.DeptCount d : stats.byDept()) {
            byDept.add(new Bar(d.id(), d.count(), Math.round(d.count() * 100f / deptMax), d.color()));
        }
        model.addAttribute("byDept", byDept);

        // By status
        model.addAttribute("byStatus", List.of(
                new Bar("Active", stats.active(), pct(stats.active(), stats.headcount()), "#4a9d7a"),
                new Bar("Onboarding", stats.onboarding(), pct(stats.onboarding(), stats.headcount()), "#3f7cc4"),
                new Bar("On leave", stats.onLeave(), pct(stats.onLeave(), stats.headcount()), "#c68a2a"),
                new Bar("Inactive", inactive, pct(inactive, stats.headcount()), "#b6bdc6")));

        // By work mode + level (derived)
        model.addAttribute("byMode", distribution(all, e -> e.workMode,
                Map.of("Remote", "#5a8fb5", "Hybrid", "#9a6ab5", "On-site", "#c68a2a")));
        model.addAttribute("byLevel", distribution(all, e -> e.level, Map.of()));

        // Review completion for the active cycle
        var cycle = performance.activeCycle();
        if (cycle != null && !"draft".equals(cycle.status)) {
            PerformanceService.Completion comp = performance.completionFor(cycle.id);
            model.addAttribute("reviewCycle", cycle.name);
            model.addAttribute("reviewPct", comp.pct());
            model.addAttribute("reviewDone", comp.done());
            model.addAttribute("reviewTotal", comp.total());
        }
        return "analytics/analytics";
    }

    private interface Field {
        String of(Employee e);
    }

    private List<Bar> distribution(List<Employee> all, Field f, Map<String, String> colors) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Employee e : all) {
            if (e.status == EmployeeStatus.INACTIVE) continue;
            String k = f.of(e);
            if (k == null || k.isBlank()) k = "—";
            counts.merge(k, 1, Integer::sum);
        }
        int max = 1;
        for (int v : counts.values()) max = Math.max(max, v);
        String[] palette = {"#3f7cc4", "#4a9d7a", "#9a6ab5", "#c68a2a", "#b56b8f", "#5a8fb5", "#6b8fb5"};
        List<Bar> out = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String color = colors.getOrDefault(e.getKey(), palette[i % palette.length]);
            out.add(new Bar(e.getKey(), e.getValue(), Math.round(e.getValue() * 100f / max), color));
            i++;
        }
        return out;
    }

    private static int pct(int n, int total) {
        return total == 0 ? 0 : Math.round(n * 100f / total);
    }

    public record Kpi(String label, String value, String sub) {
    }

    public record Bar(String label, int count, int pct, String color) {
    }
}
