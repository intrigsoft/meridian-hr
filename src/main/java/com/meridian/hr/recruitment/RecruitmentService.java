package com.meridian.hr.recruitment;

import com.meridian.hr.domain.Candidate;
import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.OnboardingCase;
import com.meridian.hr.domain.Requisition;
import com.meridian.hr.onboarding.OnboardingService;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applicant-tracking logic ported from the fixture's {@code recruitment-store.js}: requisition
 * lifecycle, candidate pipeline, interview debriefs, and the offer flow — accepting an offer
 * marks the candidate hired AND opens an {@link OnboardingService} case, closing the loop back
 * to onboarding. Operates on the calling device's {@link Workspace}.
 */
@Service
public class RecruitmentService {

    private final SessionContext session;
    private final PeopleService people;
    private final OnboardingService onboarding;

    public RecruitmentService(SessionContext session, PeopleService people, OnboardingService onboarding) {
        this.session = session;
        this.people = people;
        this.onboarding = onboarding;
    }

    private Workspace ws() {
        return session.workspace();
    }

    public String personName(String id) {
        Employee e = people.get(id);
        return e != null ? e.fullName() : (id == null ? "—" : id);
    }

    public String personInitials(String id) {
        Employee e = people.get(id);
        return e != null ? e.initials : "?";
    }

    public String personAvatarBg(String id) {
        Employee e = people.get(id);
        return e != null && e.avatarBg != null ? e.avatarBg : "#c7cdd6";
    }

    // ---- requisitions ----

    public List<Requisition> reqs() {
        return ws().requisitions;
    }

    public Requisition getReq(String id) {
        for (Requisition r : ws().requisitions) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    public Requisition createReq() {
        Requisition r = new Requisition();
        r.id = "REQ-" + (2053 + ws().requisitions.size());
        r.title = "New requisition";
        r.dept = "Engineering";
        r.level = "Mid";
        r.location = "Remote";
        r.headcount = 1;
        r.status = "draft";
        r.ownerId = "david.okonkwo";
        r.recruiterId = "priya.nair";
        r.scorecard.addAll(List.of("problem", "comm", "ownership"));
        r.round("interview", "david.okonkwo").round("onsite", "david.okonkwo");
        r.approvalStatus = "none";
        r.approverId = "alex.whitfield";
        r.createdAt = System.currentTimeMillis();
        ws().requisitions.add(0, r);
        return r;
    }

    /** The title a fresh draft is born with — must be replaced before the req can be submitted. */
    public static final String DEFAULT_TITLE = "New requisition";

    /** Edit a draft's role details. No-op once the req has left draft — the setup locks. */
    public void updateReqDetails(String id, String title, String dept, String level,
                                 Integer headcount, String location, String ownerId) {
        Requisition r = getReq(id);
        if (r == null || !"draft".equals(r.status)) return;
        if (title != null && !title.isBlank()) r.title = title.trim();
        if (dept != null && !dept.isBlank()) r.dept = dept;
        if (level != null && !level.isBlank()) r.level = level;
        if (headcount != null && headcount >= 1) r.headcount = headcount;
        if (location != null && !location.isBlank()) r.location = location.trim();
        if (ownerId != null && people.get(ownerId) != null) r.ownerId = ownerId;
    }

    /** Toggle a scorecard-library attribute on a draft req (fixture's toggleAttr). */
    public void toggleScorecardAttr(String id, String attrId) {
        Requisition r = getReq(id);
        if (r == null || !"draft".equals(r.status) || !RecruitmentMeta.isLibraryAttr(attrId)) return;
        if (!r.scorecard.remove(attrId)) r.scorecard.add(attrId);
    }

    /** Toggle an interviewer on a draft req's round (fixture's toggleInterviewer). */
    public void togglePanelMember(String id, String stageId, String personId) {
        Requisition r = getReq(id);
        if (r == null || !"draft".equals(r.status) || personId == null || people.get(personId) == null) return;
        for (Requisition.Round rd : r.interviewPlan) {
            if (rd.stageId.equals(stageId)) {
                if (!rd.interviewerIds.remove(personId)) rd.interviewerIds.add(personId);
            }
        }
    }

    /**
     * The designer's submit gate (fixture's validity rule): a real title (not the create
     * default), at least one scorecard attribute, and every round staffed.
     */
    public boolean reqReadyForSubmit(Requisition r) {
        if (r == null) return false;
        if (r.title == null || r.title.isBlank() || DEFAULT_TITLE.equalsIgnoreCase(r.title.trim())) return false;
        if (r.scorecard.isEmpty()) return false;
        for (Requisition.Round rd : r.interviewPlan) {
            if (rd.interviewerIds.isEmpty()) return false;
        }
        return true;
    }

    /** Draft → pending approval; refuses non-drafts and drafts that fail the submit gate. */
    public boolean submitForApproval(String id) {
        Requisition r = getReq(id);
        if (r == null || !"draft".equals(r.status) || !reqReadyForSubmit(r)) return false;
        r.status = "pending_approval";
        r.approvalStatus = "pending";
        r.approvalAt = null;
        return true;
    }

    /** VP declines a pending req: it drops back to draft for rework (the store keeps no rejected state). */
    public void rejectReq(String id) {
        Requisition r = getReq(id);
        if (r == null || !"pending_approval".equals(r.status)) return;
        r.status = "draft";
        r.approvalStatus = "none";
        r.approvalAt = null;
    }

    /** Delete a draft req and its candidates (fixture's deleteReq). Only drafts can be deleted. */
    public boolean deleteReq(String id) {
        Requisition r = getReq(id);
        if (r == null || !"draft".equals(r.status)) return false;
        ws().requisitions.remove(r);
        ws().candidates.removeIf(c -> c.reqId.equals(id));
        return true;
    }

    public void approveReq(String id) {
        Requisition r = getReq(id);
        if (r == null) return;
        r.status = "open";
        r.openedAt = System.currentTimeMillis();
        r.approvalStatus = "approved";
        r.approvalAt = System.currentTimeMillis();
    }

    public void closeReq(String id, boolean filled) {
        Requisition r = getReq(id);
        if (r == null) return;
        r.status = filled ? "filled" : "closed";
        r.closedAt = System.currentTimeMillis();
    }

    // ---- candidates ----

    public List<Candidate> candidatesForReq(String reqId) {
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : ws().candidates) {
            if (c.reqId.equals(reqId)) out.add(c);
        }
        return out;
    }

    public Candidate getCandidate(String id) {
        for (Candidate c : ws().candidates) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public Candidate addCandidate(String reqId, String name, String currentRole, String source) {
        Requisition req = getReq(reqId);
        if (req == null || name == null || name.isBlank()) return null;
        Candidate c = new Candidate();
        String slug = name.trim().toLowerCase().replaceAll("[^a-z]+", ".");
        c.id = reqId + "-" + slug + "-" + Integer.toString(ws().candidates.size(), 36);
        c.reqId = reqId;
        c.name = name.trim();
        c.initials = initialsOf(name);
        c.bg = AVATARS[Math.floorMod((name + ws().candidates.size()).hashCode(), AVATARS.length)];
        c.currentRole = currentRole == null ? "" : currentRole;
        c.exp = "";
        c.source = source == null || source.isBlank() ? "Inbound" : source;
        c.fit = 75;
        c.stage = "applied";
        c.appliedAt = System.currentTimeMillis();
        c.email = slug + "@example.com";
        c.summary = "Added manually — awaiting screen.";
        ws().candidates.add(c);
        return c;
    }

    public void advance(String id) {
        Candidate c = getCandidate(id);
        if (c == null || "rejected".equals(c.stage)) return;
        int idx = RecruitmentMeta.stageIndex(c.stage);
        c.stage = RecruitmentMeta.stages().get(Math.min(RecruitmentMeta.stages().size() - 1, idx + 1)).id();
    }

    public void move(String id, String stage) {
        Candidate c = getCandidate(id);
        if (c == null) return;
        c.stage = stage;
        if (!"rejected".equals(stage)) {
            c.rejectionReason = null;
            c.rejectedAt = null;
        }
    }

    public void reject(String id, String reason) {
        Candidate c = getCandidate(id);
        if (c == null) return;
        c.stage = "rejected";
        c.rejectionReason = reason == null || reason.isBlank() ? "Not a fit" : reason;
        c.rejectedAt = System.currentTimeMillis();
    }

    public void reopen(String id, String stage) {
        Candidate c = getCandidate(id);
        if (c == null) return;
        c.stage = stage == null || stage.isBlank() ? "screen" : stage;
        c.rejectionReason = null;
        c.rejectedAt = null;
    }

    public void addNote(String id, String authorId, String text) {
        Candidate c = getCandidate(id);
        if (c == null || text == null || text.isBlank()) return;
        c.notes.add(0, new Candidate.Note(authorId, text.trim(), System.currentTimeMillis()));
    }

    /**
     * Record an interviewer's scorecard for a scored stage (fixture's submitScorecard).
     * Rules: the stage must be a scored round the candidate has reached (rejected counts
     * as having reached interview, matching the seed), the interviewer must sit on that
     * round's panel, ratings are clamped 1–5 and keyed to the req's scorecard attributes.
     * One card per interviewer per stage — resubmitting replaces the earlier card.
     */
    public boolean submitScorecard(String candId, String stageId, String interviewerId,
                                   Map<String, Integer> ratings, String recId, String comment) {
        Candidate c = getCandidate(candId);
        if (c == null || interviewerId == null || !RecruitmentMeta.SCORED_STAGES.contains(stageId)) return false;
        Requisition req = getReq(c.reqId);
        if (req == null) return false;
        int reached = RecruitmentMeta.stageIndex("rejected".equals(c.stage) ? "interview" : c.stage);
        if (reached < RecruitmentMeta.stageIndex(stageId)) return false;
        boolean onPanel = false;
        for (Requisition.Round rd : req.interviewPlan) {
            if (rd.stageId.equals(stageId) && rd.interviewerIds.contains(interviewerId)) onPanel = true;
        }
        if (!onPanel) return false;
        Candidate.Scorecard card = new Candidate.Scorecard();
        for (String aid : req.scorecard) {
            Integer v = ratings == null ? null : ratings.get(aid);
            if (v != null) card.ratings.put(aid, Math.max(1, Math.min(5, v)));
        }
        if (card.ratings.isEmpty()) return false;
        card.rec = switch (recId == null ? "" : recId) {
            case "strong_yes", "yes", "no", "strong_no" -> recId;
            default -> "yes";
        };
        card.comment = comment == null ? "" : comment.trim();
        card.submittedAt = System.currentTimeMillis();
        c.scorecards.computeIfAbsent(stageId, k -> new LinkedHashMap<>()).put(interviewerId, card);
        return true;
    }

    // ---- offers ----

    public void makeOffer(String candId, int base, int bonus, double equity, String level, String startDate) {
        Candidate c = getCandidate(candId);
        if (c == null) return;
        Candidate.Offer o = new Candidate.Offer();
        o.base = base;
        o.bonus = bonus;
        o.equity = equity;
        o.level = level;
        o.startDate = startDate;
        o.status = "pending_approval";
        o.approverId = "alex.whitfield";
        c.offer = o;
        c.stage = "offer";
    }

    public void approveOffer(String candId) {
        Candidate c = getCandidate(candId);
        if (c == null || c.offer == null) return;
        c.offer.status = "approved";
        c.offer.approvedAt = System.currentTimeMillis();
    }

    public void extendOffer(String candId) {
        Candidate c = getCandidate(candId);
        if (c == null || c.offer == null) return;
        c.offer.status = "extended";
        c.offer.extendedAt = System.currentTimeMillis();
    }

    public void declineOffer(String candId) {
        Candidate c = getCandidate(candId);
        if (c == null || c.offer == null) return;
        c.offer.status = "declined";
        c.offer.declinedAt = System.currentTimeMillis();
        c.stage = "rejected";
        c.rejectionReason = "Offer declined";
        c.rejectedAt = System.currentTimeMillis();
    }

    /** Accept an offer → mark hired AND open an onboarding case, linking it back to the candidate. */
    public OnboardingCase acceptOffer(String candId) {
        Candidate c = getCandidate(candId);
        if (c == null || c.offer == null) return null;
        Requisition req = getReq(c.reqId);
        c.offer.status = "accepted";
        c.offer.acceptedAt = System.currentTimeMillis();
        c.stage = "hired";
        String role = req != null ? RecruitmentMeta.DEPT_TO_ROLE.getOrDefault(req.dept, "general") : "general";
        String slug = c.name.toLowerCase().replaceAll("[^a-z]+", ".");
        OnboardingCase kase = onboarding.startOnboarding(c.name, role,
                req != null ? req.title : "New hire",
                c.offer.startDate != null ? c.offer.startDate : "2026-08-17",
                req != null ? personName(req.ownerId) : "—", slug + "@meridian.co");
        c.onboardingCaseId = kase.id;
        return kase;
    }

    // ---- roll-ups ----

    public record Funnel(Map<String, Integer> counts, Map<String, Integer> reach, int total, int active) {
    }

    public Funnel funnelFor(String reqId) {
        List<Candidate> cands = candidatesForReq(reqId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RecruitmentMeta.Stage s : RecruitmentMeta.stages()) counts.put(s.id(), 0);
        counts.put("rejected", 0);
        for (Candidate c : cands) counts.merge(c.stage, 1, Integer::sum);
        Map<String, Integer> reach = new LinkedHashMap<>();
        List<RecruitmentMeta.Stage> stages = RecruitmentMeta.stages();
        for (int i = 0; i < stages.size(); i++) {
            int idx = i;
            int n = 0;
            for (Candidate c : cands) {
                if ("rejected".equals(c.stage)) continue;
                if (RecruitmentMeta.stageIndex(c.stage) >= idx) n++;
            }
            reach.put(stages.get(i).id(), n);
        }
        int active = 0;
        for (Candidate c : cands) {
            if (!"rejected".equals(c.stage) && !"hired".equals(c.stage)) active++;
        }
        return new Funnel(counts, reach, cands.size(), active);
    }

    public record ReqSummary(int active, int total, int hired, int offers) {
    }

    public ReqSummary reqSummary(String reqId) {
        Funnel f = funnelFor(reqId);
        return new ReqSummary(f.active(), f.total(), f.counts().getOrDefault("hired", 0), f.counts().getOrDefault("offer", 0));
    }

    public record AttrAvg(String id, String name, Double avg, int n) {
    }

    public record Debrief(List<Candidate.Scorecard> cards, List<String> cardStages, List<String> cardInterviewers,
                          List<AttrAvg> attrs, Map<String, Integer> recTally, int count, double overall) {
    }

    public Debrief debriefFor(String candId) {
        Candidate c = getCandidate(candId);
        if (c == null) return null;
        Requisition req = getReq(c.reqId);
        List<Candidate.Scorecard> cards = new ArrayList<>();
        List<String> stages = new ArrayList<>();
        List<String> interviewers = new ArrayList<>();
        for (Map.Entry<String, Map<String, Candidate.Scorecard>> se : c.scorecards.entrySet()) {
            for (Map.Entry<String, Candidate.Scorecard> ie : se.getValue().entrySet()) {
                cards.add(ie.getValue());
                stages.add(se.getKey());
                interviewers.add(ie.getKey());
            }
        }
        List<AttrAvg> attrs = new ArrayList<>();
        if (req != null) {
            for (String aid : req.scorecard) {
                double sum = 0;
                int n = 0;
                for (Candidate.Scorecard cd : cards) {
                    Integer v = cd.ratings.get(aid);
                    if (v != null) {
                        sum += v;
                        n++;
                    }
                }
                attrs.add(new AttrAvg(aid, RecruitmentMeta.attrName(aid), n == 0 ? null : sum / n, n));
            }
        }
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (RecruitmentMeta.Rec r : RecruitmentMeta.recOrder()) tally.put(r.id(), 0);
        double overall = 0;
        for (Candidate.Scorecard cd : cards) {
            if (cd.rec != null) {
                tally.merge(cd.rec, 1, Integer::sum);
                overall += RecruitmentMeta.rec(cd.rec).val();
            }
        }
        overall = cards.isEmpty() ? 0 : overall / cards.size();
        return new Debrief(cards, stages, interviewers, attrs, tally, cards.size(), overall);
    }

    public record Reports(int openCount, int totalReqs, int totalCandidates, int hires, Integer avgTtf,
                          Map<String, Integer> funnelCounts, List<SourceRow> sources, List<FilledRow> filled) {
    }

    public record SourceRow(String source, int total, int hired) {
    }

    public record FilledRow(String id, String title, int days) {
    }

    public Reports reportsAll() {
        List<Requisition> reqs = reqs();
        int open = 0;
        for (Requisition r : reqs) if ("open".equals(r.status)) open++;
        List<Candidate> cands = ws().candidates;

        Map<String, Integer> funnel = new LinkedHashMap<>();
        for (RecruitmentMeta.Stage s : RecruitmentMeta.stages()) funnel.put(s.id(), 0);
        for (Candidate c : cands) {
            Requisition r = getReq(c.reqId);
            if (r == null || (!"open".equals(r.status) && !"filled".equals(r.status))) continue;
            if ("rejected".equals(c.stage)) {
                funnel.merge("applied", 1, Integer::sum);
                continue;
            }
            for (int i = 0; i < RecruitmentMeta.stages().size(); i++) {
                if (RecruitmentMeta.stageIndex(c.stage) >= i) {
                    funnel.merge(RecruitmentMeta.stages().get(i).id(), 1, Integer::sum);
                }
            }
        }

        Map<String, int[]> bySource = new LinkedHashMap<>();
        for (String s : RecruitmentMeta.SOURCES) bySource.put(s, new int[]{0, 0});
        for (Candidate c : cands) {
            bySource.computeIfAbsent(c.source, k -> new int[]{0, 0});
            bySource.get(c.source)[0]++;
            if ("hired".equals(c.stage)) bySource.get(c.source)[1]++;
        }
        List<SourceRow> sources = new ArrayList<>();
        for (Map.Entry<String, int[]> e : bySource.entrySet()) {
            if (e.getValue()[0] > 0) sources.add(new SourceRow(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        sources.sort((a, b) -> Integer.compare(b.total(), a.total()));

        List<FilledRow> filled = new ArrayList<>();
        int ttfSum = 0;
        for (Requisition r : reqs) {
            if ("filled".equals(r.status) && r.openedAt != null && r.closedAt != null) {
                int days = (int) Math.round((r.closedAt - r.openedAt) / 86400000.0);
                filled.add(new FilledRow(r.id, r.title, days));
                ttfSum += days;
            }
        }
        Integer avgTtf = filled.isEmpty() ? null : Math.round((float) ttfSum / filled.size());
        int hires = 0;
        for (Candidate c : cands) if ("hired".equals(c.stage)) hires++;

        return new Reports(open, reqs.size(), cands.size(), hires, avgTtf, funnel, sources, filled);
    }

    private static final String[] AVATARS = {"#6b7db5", "#6ba58f", "#b56b8f", "#c07f4f", "#7a6bb5",
            "#5a8fb5", "#4a9d9d", "#b58f4a", "#9a6ab5", "#6b8fb5"};

    private static String initialsOf(String name) {
        String[] w = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length && sb.length() < 2; i++) {
            if (!w[i].isEmpty()) sb.append(Character.toUpperCase(w[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }
}
