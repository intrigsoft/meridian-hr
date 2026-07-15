package com.meridian.hr.performance;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.domain.Review;
import com.meridian.hr.domain.ReviewCycle;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance review orchestration ported from the fixture's {@code performance-store.js}.
 * Owns cycle lifecycle (draft → active → closed), review instances (self → manager →
 * calibration → committed), and the report roll-ups (completion, distribution, self-vs-manager
 * gaps). Reviewees derive from the single {@link PeopleService} population. Operates on the
 * calling device's {@link Workspace}.
 */
@Service
public class PerformanceService {

    private final SessionContext session;
    private final PeopleService people;

    public PerformanceService(SessionContext session, PeopleService people) {
        this.session = session;
        this.people = people;
    }

    private Workspace ws() {
        return session.workspace();
    }

    // ---- reviewable population (active employees only) ----

    public List<Employee> reviewables() {
        List<Employee> out = new ArrayList<>();
        for (Employee e : people.all()) {
            if (e.status != EmployeeStatus.INACTIVE) out.add(e);
        }
        return out;
    }

    public String reviewerName(String id) {
        Employee e = people.get(id);
        return e != null ? e.fullName() : "—";
    }

    // ---- cycles ----

    public List<ReviewCycle> allCycles() {
        return ws().reviewCycles;
    }

    public ReviewCycle getCycle(String id) {
        for (ReviewCycle c : ws().reviewCycles) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public ReviewCycle activeCycle() {
        for (ReviewCycle c : ws().reviewCycles) {
            if ("active".equals(c.status)) return c;
        }
        return ws().reviewCycles.isEmpty() ? null : ws().reviewCycles.get(0);
    }

    /** Cycles selectable in the reviews/reports dropdown (drafts have no instances). */
    public List<ReviewCycle> selectableCycles() {
        List<ReviewCycle> out = new ArrayList<>();
        for (ReviewCycle c : ws().reviewCycles) {
            if (!"draft".equals(c.status)) out.add(c);
        }
        return out;
    }

    public ReviewCycle createCycle(String name) {
        ReviewCycle c = new ReviewCycle();
        c.id = "cyc-" + Long.toString(System.currentTimeMillis(), 36) + ws().reviewCycles.size();
        c.name = name == null || name.isBlank() ? "New review cycle" : name.trim();
        c.type = "half";
        c.status = "draft";
        c.startDate = "2026-07-01";
        c.selfDue = "2026-07-15";
        c.mgrDue = "2026-07-25";
        c.calibrationDate = "2026-08-05";
        c.scaleMax = 5;
        c.comp("exec", 40).comp("collab", 30).comp("owner", 30);
        // Deferred-designer scope: prefill all active employees so the cycle is launchable.
        for (Employee e : reviewables()) {
            c.participants.add(e.id);
        }
        c.createdAt = System.currentTimeMillis();
        c.createdBy = "Priya Nair";
        ws().reviewCycles.add(0, c);
        return c;
    }

    public void launchCycle(String id) {
        ReviewCycle c = getCycle(id);
        if (c == null || !"draft".equals(c.status)) return;
        c.status = "active";
        ensureReviews(c);
    }

    public void closeCycle(String id) {
        ReviewCycle c = getCycle(id);
        if (c != null) c.status = "closed";
    }

    /** Create a blank review row for any participant that doesn't have one (used on launch). */
    private void ensureReviews(ReviewCycle cycle) {
        for (String eid : cycle.participants) {
            if (getReview(cycle.id, eid) == null) {
                Employee e = people.get(eid);
                ws().reviews.add(new Review(cycle.id, eid, e != null ? e.managerId : null));
            }
        }
    }

    // ---- reviews ----

    public List<Review> reviewsForCycle(String cycleId) {
        List<Review> out = new ArrayList<>();
        for (Review r : ws().reviews) {
            if (r.cycleId.equals(cycleId)) out.add(r);
        }
        return out;
    }

    public Review getReview(String cycleId, String empId) {
        for (Review r : ws().reviews) {
            if (r.cycleId.equals(cycleId) && r.empId.equals(empId)) return r;
        }
        return null;
    }

    public void submitSelf(String cycleId, String empId, Map<String, Integer> scores, String narrative) {
        Review r = getReview(cycleId, empId);
        if (r == null) return;
        r.self.scores.clear();
        r.self.scores.putAll(scores);
        r.self.narrative = narrative == null ? "" : narrative;
        r.self.submittedAt = System.currentTimeMillis();
    }

    public void submitManager(String cycleId, String empId, Map<String, Integer> scores, String narrative) {
        Review r = getReview(cycleId, empId);
        if (r == null) return;
        r.mgr.scores.clear();
        r.mgr.scores.putAll(scores);
        r.mgr.narrative = narrative == null ? "" : narrative;
        r.mgr.submittedAt = System.currentTimeMillis();
        // Seed calibration from the manager assessment.
        r.cal.scores.clear();
        r.cal.scores.putAll(scores);
        r.cal.started = true;
    }

    public void setCalibrated(String cycleId, String empId, String compId, int value) {
        Review r = getReview(cycleId, empId);
        if (r == null || r.cal.committed) return;
        r.cal.scores.put(compId, Math.max(1, Math.min(5, value)));
        r.cal.started = true;
    }

    public void commit(String cycleId, String empId) {
        Review r = getReview(cycleId, empId);
        if (r == null || !r.cal.started) return;
        r.cal.committed = true;
        r.cal.committedAt = System.currentTimeMillis();
    }

    public void reopen(String cycleId, String empId) {
        Review r = getReview(cycleId, empId);
        if (r == null) return;
        r.cal.committed = false;
        r.cal.committedAt = null;
    }

    // ---- status computation ----

    public String reviewStatus(Review r) {
        if (r == null || r.self.submittedAt == null) return "awaiting_self";
        if (r.mgr.submittedAt == null) return "awaiting_manager";
        if (!r.cal.committed) return "in_calibration";
        return "committed";
    }

    public Double weightedAvg(ReviewCycle cycle, Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) return null;
        double tot = 0, wsum = 0;
        for (ReviewCycle.CompWeight c : cycle.competencies) {
            Integer v = scores.get(c.id);
            if (v != null) {
                tot += (double) v * c.weight;
                wsum += c.weight;
            }
        }
        return wsum == 0 ? null : tot / wsum;
    }

    public Double selfAvg(ReviewCycle cy, Review r) {
        return r.self.submittedAt != null ? weightedAvg(cy, r.self.scores) : null;
    }

    public Double mgrAvg(ReviewCycle cy, Review r) {
        return r.mgr.submittedAt != null ? weightedAvg(cy, r.mgr.scores) : null;
    }

    public Double calAvg(ReviewCycle cy, Review r) {
        return r.cal.committed ? weightedAvg(cy, r.cal.scores) : null;
    }

    // ---- report roll-ups ----

    public record Completion(Map<String, Integer> counts, int total, int done, int pct) {
    }

    public Completion completionFor(String cycleId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("awaiting_self", 0);
        counts.put("awaiting_manager", 0);
        counts.put("in_calibration", 0);
        counts.put("committed", 0);
        List<Review> rows = reviewsForCycle(cycleId);
        for (Review r : rows) {
            String s = reviewStatus(r);
            counts.merge(s, 1, Integer::sum);
        }
        int total = rows.size();
        int done = counts.get("committed");
        return new Completion(counts, total, done, total == 0 ? 0 : Math.round(done * 100f / total));
    }

    public record DistBand(String key, String label, String color, int count, int pct) {
    }

    public List<DistBand> distributionFor(String cycleId) {
        ReviewCycle cy = getCycle(cycleId);
        int below = 0, meets = 0, strong = 0, top = 0, n = 0;
        for (Review r : reviewsForCycle(cycleId)) {
            if (!r.cal.committed) continue;
            Double v = weightedAvg(cy, r.cal.scores);
            if (v == null) continue;
            n++;
            if (v >= 4.5) top++;
            else if (v >= 3.5) strong++;
            else if (v >= 2.5) meets++;
            else below++;
        }
        int t = n == 0 ? 1 : n;
        List<DistBand> out = new ArrayList<>();
        out.add(new DistBand("below", "Below (1–2.4)", "#c0563f", below, Math.round(below * 100f / t)));
        out.add(new DistBand("meets", "Meets (2.5–3.4)", "#c68a2a", meets, Math.round(meets * 100f / t)));
        out.add(new DistBand("strong", "Exceeds (3.5–4.4)", "#3f7cc4", strong, Math.round(strong * 100f / t)));
        out.add(new DistBand("top", "Outstanding (4.5–5)", "#3d8564", top, Math.round(top * 100f / t)));
        return out;
    }

    public record GapRow(String id, String name, int weight, double self, double mgr, double delta, int n) {
    }

    public List<GapRow> gapsFor(String cycleId) {
        ReviewCycle cy = getCycle(cycleId);
        List<Review> rows = new ArrayList<>();
        for (Review r : reviewsForCycle(cycleId)) {
            if (r.self.submittedAt != null && r.mgr.submittedAt != null) rows.add(r);
        }
        List<GapRow> out = new ArrayList<>();
        for (ReviewCycle.CompWeight c : cy.competencies) {
            double sSum = 0, mSum = 0;
            int n = 0;
            for (Review r : rows) {
                Integer s = r.self.scores.get(c.id);
                Integer m = r.mgr.scores.get(c.id);
                if (s != null && m != null) {
                    sSum += s;
                    mSum += m;
                    n++;
                }
            }
            double self = n == 0 ? 0 : sSum / n;
            double mgr = n == 0 ? 0 : mSum / n;
            out.add(new GapRow(c.id, PerformanceMeta.competency(c.id).name(), c.weight, self, mgr, self - mgr, n));
        }
        return out;
    }

    public record PerEmp(String empId, String name, String initials, String avatarBg, String title, String dept,
                         String reviewer, String status, Double self, Double mgr, Double cal) {
    }

    public List<PerEmp> perEmployee(String cycleId) {
        ReviewCycle cy = getCycle(cycleId);
        List<PerEmp> out = new ArrayList<>();
        for (Review r : reviewsForCycle(cycleId)) {
            Employee e = people.get(r.empId);
            out.add(new PerEmp(r.empId,
                    e != null ? e.fullName() : r.empId, e != null ? e.initials : "?",
                    e != null ? e.avatarBg : "#c7cdd6", e != null ? e.title : "", e != null ? e.dept : "—",
                    reviewerName(r.reviewerId), reviewStatus(r),
                    selfAvg(cy, r), mgrAvg(cy, r), calAvg(cy, r)));
        }
        return out;
    }
}
