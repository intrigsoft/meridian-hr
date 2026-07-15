package com.meridian.hr.recruitment;

import com.meridian.hr.domain.Candidate;
import com.meridian.hr.domain.Requisition;
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

import java.util.ArrayList;
import java.util.List;

/**
 * People Ops → Recruitment (ATS). HR + managers browse requisitions and pipelines; HR opens
 * requisitions, approves them, and runs the offer flow. Accepting an offer hires the candidate
 * and opens an onboarding case. The requisition designer is read-only (editable builder deferred)
 * and interviewer scorecards are seeded read-only. Ports the fixture's {@code Recruitment.dc.html}.
 */
@Controller
public class RecruitmentController {

    private final RecruitmentService rec;
    private final SessionContext session;
    private final AccessPolicy policy;

    public RecruitmentController(RecruitmentService rec, SessionContext session, AccessPolicy policy) {
        this.rec = rec;
        this.session = session;
        this.policy = policy;
    }

    // ===================== requisitions + reports =====================

    @GetMapping("/recruitment")
    public String index(@RequestParam(required = false) String tab, Model model) {
        Actor actor = session.actor();
        boolean allowed = policy.can(Permission.RECRUIT_VIEW);
        boolean isHr = policy.can(Permission.RECRUIT_ADMIN);

        model.addAttribute("active", "recruitment");
        model.addAttribute("allowed", allowed);
        model.addAttribute("restricted", !allowed);
        model.addAttribute("isHr", isHr);
        if (!allowed) return "recruitment/recruitment";

        String view = "reports".equals(tab) && isHr ? "reports" : "reqs";
        model.addAttribute("tab", view);
        model.addAttribute("heading", "reports".equals(view) ? "Recruitment reports" : "Requisitions");
        List<TabView> tabs = new ArrayList<>();
        tabs.add(new TabView("reqs", "Requisitions", true, rec.reqs().size(), "reqs".equals(view)));
        if (isHr) tabs.add(new TabView("reports", "Reports", false, 0, "reports".equals(view)));
        model.addAttribute("tabs", tabs);

        if ("reports".equals(view)) {
            buildReports(model);
        } else {
            List<ReqCard> cards = new ArrayList<>();
            for (Requisition r : rec.reqs()) cards.add(toCard(r, isHr));
            model.addAttribute("reqCards", cards);
            model.addAttribute("emptyReqs", cards.isEmpty());
        }
        return "recruitment/recruitment";
    }

    private ReqCard toCard(Requisition r, boolean isHr) {
        RecruitmentMeta.StatusMeta sm = RecruitmentMeta.reqStatus(r.status);
        RecruitmentService.Funnel f = rec.funnelFor(r.id);
        List<FunnelCell> funnel = new ArrayList<>();
        for (RecruitmentMeta.Stage s : RecruitmentMeta.stages()) {
            funnel.add(new FunnelCell(s.shortLabel(), f.counts().getOrDefault(s.id(), 0), s.color()));
        }
        boolean pending = "pending_approval".equals(r.status);
        boolean nonDraft = !"draft".equals(r.status) && !pending;
        return new ReqCard(r.id, r.title, sm.label(), sm.bg(), sm.fg(),
                r.dept + " · " + r.level + " · " + r.location + " · " + r.headcount + " opening · HM " + rec.personName(r.ownerId),
                isHr && pending, "draft".equals(r.status), nonDraft || pending, funnel, nonDraft,
                pending, "Awaiting VP approval before the role opens.");
    }

    private void buildReports(Model model) {
        RecruitmentService.Reports rp = rec.reportsAll();
        model.addAttribute("statTiles", List.of(
                new StatTile("Open requisitions", String.valueOf(rp.openCount()), rp.totalReqs() + " total"),
                new StatTile("Candidates", String.valueOf(rp.totalCandidates()), "in all pipelines"),
                new StatTile("Hires", String.valueOf(rp.hires()), "this period"),
                new StatTile("Avg time-to-fill", rp.avgTtf() == null ? "—" : rp.avgTtf() + "d", "open → close")));
        List<FunnelBar> bars = new ArrayList<>();
        int max = 1;
        for (RecruitmentMeta.Stage s : RecruitmentMeta.stages()) max = Math.max(max, rp.funnelCounts().getOrDefault(s.id(), 0));
        for (RecruitmentMeta.Stage s : RecruitmentMeta.stages()) {
            int n = rp.funnelCounts().getOrDefault(s.id(), 0);
            bars.add(new FunnelBar(s.label(), n, Math.round(n * 100f / max), s.color()));
        }
        model.addAttribute("funnelBars", bars);
        model.addAttribute("sourceRows", rp.sources());
        model.addAttribute("filledRows", rp.filled());
    }

    // ===================== requisition lifecycle =====================

    @PostMapping("/recruitment/req/new")
    public String newReq() {
        Actor actor = session.actor();
        if (!policy.can(Permission.RECRUIT_ADMIN)) return "redirect:/recruitment";
        Requisition r = rec.createReq();
        return "redirect:/recruitment/req/" + r.id;
    }

    @PostMapping("/recruitment/req/{id}/submit")
    public String submit(@PathVariable String id, RedirectAttributes ra) {
        if (approver()) rec.submitForApproval(id);
        ra.addFlashAttribute("toast", "Requisition submitted for approval.");
        ra.addFlashAttribute("toastDot", "#e0a13a");
        return "redirect:/recruitment/req/" + id;
    }

    @PostMapping("/recruitment/req/{id}/approve")
    public String approve(@PathVariable String id, RedirectAttributes ra) {
        Actor actor = session.actor();
        if (policy.can(Permission.RECRUIT_ADMIN)) {
            rec.approveReq(id);
            ra.addFlashAttribute("toast", "Requisition approved — the role is now open.");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
        }
        return "redirect:/recruitment";
    }

    @PostMapping("/recruitment/req/{id}/close")
    public String close(@PathVariable String id, @RequestParam(defaultValue = "true") boolean filled, RedirectAttributes ra) {
        if (approver()) rec.closeReq(id, filled);
        ra.addFlashAttribute("toast", filled ? "Requisition marked filled." : "Requisition closed.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return "redirect:/recruitment/req/" + id;
    }

    @GetMapping("/recruitment/req/{id}")
    public String reqDetail(@PathVariable String id, Model model) {
        Actor actor = session.actor();
        Requisition r = rec.getReq(id);
        if (!policy.can(Permission.RECRUIT_VIEW) || r == null) return "redirect:/recruitment";
        model.addAttribute("active", "recruitment");
        model.addAttribute("req", r);
        model.addAttribute("statusMeta", RecruitmentMeta.reqStatus(r.status));
        model.addAttribute("ownerName", rec.personName(r.ownerId));
        model.addAttribute("recruiterName", rec.personName(r.recruiterId));
        model.addAttribute("isHr", policy.can(Permission.RECRUIT_ADMIN));
        model.addAttribute("isDraft", "draft".equals(r.status));
        model.addAttribute("isPending", "pending_approval".equals(r.status));
        model.addAttribute("isOpen", "open".equals(r.status));
        List<AttrView> attrs = new ArrayList<>();
        for (String aid : r.scorecard) attrs.add(new AttrView(RecruitmentMeta.attrName(aid)));
        model.addAttribute("attrs", attrs);
        List<RoundView> rounds = new ArrayList<>();
        for (Requisition.Round rd : r.interviewPlan) {
            List<String> names = new ArrayList<>();
            for (String iid : rd.interviewerIds) names.add(rec.personName(iid));
            rounds.add(new RoundView(RecruitmentMeta.stage(rd.stageId).label(), String.join(", ", names)));
        }
        model.addAttribute("rounds", rounds);
        model.addAttribute("summary", rec.reqSummary(id));
        return "recruitment/requisition";
    }

    // ===================== pipeline =====================

    @GetMapping("/recruitment/req/{id}/pipeline")
    public String pipeline(@PathVariable String id, Model model) {
        Actor actor = session.actor();
        Requisition r = rec.getReq(id);
        if (!policy.can(Permission.RECRUIT_VIEW) || r == null) return "redirect:/recruitment";
        model.addAttribute("active", "recruitment");
        model.addAttribute("req", r);
        model.addAttribute("ownerName", rec.personName(r.ownerId));

        List<Candidate> cands = rec.candidatesForReq(id);
        List<StageColumn> cols = new ArrayList<>();
        List<String> colStages = new ArrayList<>();
        for (RecruitmentMeta.Stage s : RecruitmentMeta.stages()) colStages.add(s.id());
        colStages.add("rejected");
        for (String sid : colStages) {
            RecruitmentMeta.Stage s = RecruitmentMeta.stage(sid);
            List<CandCard> cc = new ArrayList<>();
            for (Candidate c : cands) {
                if (c.stage.equals(sid)) {
                    cc.add(new CandCard(c.id, c.name, c.initials, c.bg, c.currentRole, c.fit, c.source));
                }
            }
            if (!cc.isEmpty() || !"rejected".equals(sid)) {
                cols.add(new StageColumn(s.label(), s.color(), s.bg(), cc.size(), cc));
            }
        }
        model.addAttribute("columns", cols);
        model.addAttribute("sourceOptions", RecruitmentMeta.SOURCES);
        return "recruitment/pipeline";
    }

    @PostMapping("/recruitment/req/{id}/candidate")
    public String addCandidate(@PathVariable String id, @RequestParam String name,
                               @RequestParam(required = false) String currentRole,
                               @RequestParam(required = false) String source, RedirectAttributes ra) {
        if (approver()) rec.addCandidate(id, name, currentRole, source);
        ra.addFlashAttribute("toast", "Candidate added to the pipeline.");
        ra.addFlashAttribute("toastDot", "#3ecf8e");
        return "redirect:/recruitment/req/" + id + "/pipeline";
    }

    // ===================== candidate =====================

    @GetMapping("/recruitment/candidate/{id}")
    public String candidate(@PathVariable String id, Model model) {
        Actor actor = session.actor();
        Candidate c = rec.getCandidate(id);
        if (!policy.can(Permission.RECRUIT_VIEW) || c == null) return "redirect:/recruitment";
        Requisition r = rec.getReq(c.reqId);
        model.addAttribute("active", "recruitment");
        model.addAttribute("cand", c);
        model.addAttribute("req", r);
        model.addAttribute("isHr", policy.can(Permission.RECRUIT_ADMIN));
        RecruitmentMeta.Stage sm = RecruitmentMeta.stage(c.stage);
        model.addAttribute("stageLabel", sm.label());
        model.addAttribute("stageBg", sm.bg());
        model.addAttribute("stageFg", sm.color());
        model.addAttribute("rejected", "rejected".equals(c.stage));
        model.addAttribute("hired", "hired".equals(c.stage));
        model.addAttribute("canAdvance", !"rejected".equals(c.stage) && !"hired".equals(c.stage) && !"offer".equals(c.stage));
        model.addAttribute("stages", RecruitmentMeta.stages());
        model.addAttribute("rejectReasons", RecruitmentMeta.REJECTION_REASONS);

        // debrief
        RecruitmentService.Debrief db = rec.debriefFor(id);
        model.addAttribute("debrief", db);
        List<AttrScore> attrScores = new ArrayList<>();
        if (db != null) {
            for (RecruitmentService.AttrAvg a : db.attrs()) {
                attrScores.add(new AttrScore(a.name(), a.avg() == null ? "—" : String.format(java.util.Locale.US, "%.1f", a.avg()),
                        a.avg() == null ? 0 : (int) Math.round(a.avg() / 5 * 100)));
            }
        }
        model.addAttribute("attrScores", attrScores);
        List<CardView> cardViews = new ArrayList<>();
        if (db != null) {
            for (int i = 0; i < db.cards().size(); i++) {
                Candidate.Scorecard cd = db.cards().get(i);
                RecruitmentMeta.Rec rc = RecruitmentMeta.rec(cd.rec);
                cardViews.add(new CardView(RecruitmentMeta.stage(db.cardStages().get(i)).label(),
                        rec.personName(db.cardInterviewers().get(i)), rc.label(), rc.color(), rc.bg(), cd.comment));
            }
        }
        model.addAttribute("cardViews", cardViews);

        // offer
        model.addAttribute("hasOffer", c.offer != null);
        model.addAttribute("offer", c.offer);
        model.addAttribute("canMakeOffer", c.offer == null && ("onsite".equals(c.stage) || "interview".equals(c.stage)));
        model.addAttribute("offerLevel", r != null ? r.level : c.stage);
        model.addAttribute("onboardingCaseId", c.onboardingCaseId);
        return "recruitment/candidate";
    }

    @PostMapping("/recruitment/candidate/{id}/advance")
    public String advance(@PathVariable String id, RedirectAttributes ra) {
        if (approver()) rec.advance(id);
        return back(id);
    }

    @PostMapping("/recruitment/candidate/{id}/reject")
    public String reject(@PathVariable String id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        if (approver()) rec.reject(id, reason);
        ra.addFlashAttribute("toast", "Candidate rejected.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return back(id);
    }

    @PostMapping("/recruitment/candidate/{id}/reopen")
    public String reopen(@PathVariable String id, RedirectAttributes ra) {
        if (approver()) rec.reopen(id, "screen");
        return back(id);
    }

    @PostMapping("/recruitment/candidate/{id}/offer/make")
    public String makeOffer(@PathVariable String id, @RequestParam int base, @RequestParam int bonus,
                            @RequestParam double equity, @RequestParam String level,
                            @RequestParam String startDate, RedirectAttributes ra) {
        if (approver()) rec.makeOffer(id, base, bonus, equity, level, startDate);
        ra.addFlashAttribute("toast", "Offer drafted — pending VP approval.");
        ra.addFlashAttribute("toastDot", "#e0a13a");
        return back(id);
    }

    @PostMapping("/recruitment/candidate/{id}/offer/approve")
    public String approveOffer(@PathVariable String id, RedirectAttributes ra) {
        Actor a = session.actor();
        if (policy.can(Permission.RECRUIT_ADMIN)) rec.approveOffer(id);
        return back(id);
    }

    @PostMapping("/recruitment/candidate/{id}/offer/extend")
    public String extendOffer(@PathVariable String id, RedirectAttributes ra) {
        if (approver()) rec.extendOffer(id);
        return back(id);
    }

    @PostMapping("/recruitment/candidate/{id}/offer/accept")
    public String acceptOffer(@PathVariable String id, RedirectAttributes ra) {
        if (approver()) {
            rec.acceptOffer(id);
            ra.addFlashAttribute("toast", "Offer accepted — hired & onboarding started.");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
        }
        return back(id);
    }

    @PostMapping("/recruitment/candidate/{id}/offer/decline")
    public String declineOffer(@PathVariable String id, RedirectAttributes ra) {
        if (approver()) rec.declineOffer(id);
        ra.addFlashAttribute("toast", "Offer declined.");
        ra.addFlashAttribute("toastDot", "#8894a3");
        return back(id);
    }

    private boolean approver() {
        return policy.can(Permission.RECRUIT_MANAGE);
    }

    private String back(String candId) {
        return "redirect:/recruitment/candidate/" + candId;
    }

    // ===================== view records =====================

    public record TabView(String key, String label, boolean showCount, int count, boolean on) {
    }

    public record FunnelCell(String label, int count, String color) {
    }

    public record ReqCard(String id, String title, String statusLabel, String statusBg, String statusFg,
                          String meta, boolean canApprove, boolean canEdit, boolean canPipeline,
                          List<FunnelCell> funnel, boolean showFunnel, boolean showPending, String pendingMsg) {
    }

    public record StatTile(String label, String value, String sub) {
    }

    public record FunnelBar(String label, int count, int pct, String color) {
    }

    public record AttrView(String name) {
    }

    public record RoundView(String stage, String interviewers) {
    }

    public record StageColumn(String label, String color, String bg, int count, List<CandCard> cards) {
    }

    public record CandCard(String id, String name, String initials, String bg, String role, int fit, String source) {
    }

    public record AttrScore(String name, String avg, int pct) {
    }

    public record CardView(String stage, String interviewer, String recLabel, String recColor, String recBg, String comment) {
    }
}
