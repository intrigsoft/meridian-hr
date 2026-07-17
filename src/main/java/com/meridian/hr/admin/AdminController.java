package com.meridian.hr.admin;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.JobChange;
import com.meridian.hr.domain.LeaveRequest;
import com.meridian.hr.domain.OffboardingCase;
import com.meridian.hr.domain.OrgConfig;
import com.meridian.hr.domain.PolicyConfig;
import com.meridian.hr.domain.ProfileChange;
import com.meridian.hr.domain.ReviewCycle;
import com.meridian.hr.domain.Role;
import com.meridian.hr.jobchange.JobChangeMeta;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.performance.PerformanceMeta;
import com.meridian.hr.security.AccessPolicy;
import com.meridian.hr.security.Permission;
import com.meridian.hr.security.RolePermissions;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.workspace.Workspace;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin surfaces (HR only): Settings (policy config), Org Structure (departments /
 * levels / comp bands), Roles &amp; Access (who holds which access role), and a unified
 * Audit Log synthesized from every domain's event trail plus the admin change trail.
 * All edits are plain form POSTs (PRG); the leave / time / job-change engines read the
 * same workspace config objects, so changes take effect immediately.
 */
@Controller
public class AdminController {

    private final PeopleService people;
    private final SessionContext session;
    private final AccessPolicy policy;
    private final AdminAudit audit;

    public AdminController(PeopleService people, SessionContext session, AccessPolicy policy, AdminAudit audit) {
        this.people = people;
        this.session = session;
        this.policy = policy;
        this.audit = audit;
    }

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter MON_D = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MON = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY_Y = DateTimeFormatter.ofPattern("EEEE, yyyy", Locale.ENGLISH);

    private static final List<String> ALL_DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final List<String> DAY_LABELS = List.of("M", "T", "W", "T", "F", "S", "S");

    private boolean guard(Model model, String active) {
        Permission required = switch (active) {
            case "org" -> Permission.ADMIN_ORG;
            case "roles" -> Permission.ADMIN_ROLES;
            case "audit" -> Permission.AUDIT_VIEW;
            default -> Permission.ADMIN_SETTINGS;
        };
        boolean hr = policy.can(required);
        model.addAttribute("active", active);
        model.addAttribute("isHr", hr);
        return hr;
    }

    private Workspace ws() {
        return session.workspace();
    }

    private String actorName() {
        Employee u = session.currentUser();
        return u == null ? "HR" : u.fullName();
    }

    private static void toast(RedirectAttributes ra, String msg, String dot) {
        ra.addFlashAttribute("toast", msg);
        ra.addFlashAttribute("toastDot", dot);
    }

    private static final String OK = "#3ecf8e";
    private static final String NEUTRAL = "#8894a3";
    private static final String WARN = "#e0a13a";

    /** Parse the digits of a numeric form value, clamped to [min,max]. */
    private static int intOf(String raw, int fallback, int min, int max) {
        if (raw == null) return fallback;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return fallback;
        try {
            long v = Long.parseLong(digits);
            return (int) Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ===================== settings =====================

    /** The workspace competency catalog — shared lazy-seed lives in PerformanceMeta. */
    private List<PolicyConfig.Competency> competencies() {
        return PerformanceMeta.catalog(ws().policy);
    }

    /** True if any review cycle (any status) references competency {@code compId}. */
    private boolean referencedByCycle(String compId) {
        for (ReviewCycle c : ws().reviewCycles) {
            for (ReviewCycle.CompWeight cw : c.competencies) {
                if (cw.id.equals(compId)) return true;
            }
        }
        return false;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        if (!guard(model, "settings")) return "admin/settings";
        PolicyConfig p = ws().policy;

        List<DayChip> dayChips = new ArrayList<>();
        for (int i = 0; i < ALL_DAYS.size(); i++) {
            dayChips.add(new DayChip(ALL_DAYS.get(i), DAY_LABELS.get(i), p.workingDays.contains(ALL_DAYS.get(i))));
        }

        List<BlackoutRow> blackoutRows = new ArrayList<>();
        for (PolicyConfig.Blackout b : p.blackouts) {
            blackoutRows.add(new BlackoutRow(b.id, b.label, rangeLabel(b.start, b.end), b.scope));
        }

        List<HolidayRow> holidayRows = new ArrayList<>();
        for (PolicyConfig.Holiday h : p.holidaysSorted()) {
            try {
                LocalDate d = LocalDate.parse(h.date);
                holidayRows.add(new HolidayRow(h.id, h.name, d.format(MON), String.valueOf(d.getDayOfMonth()),
                        d.format(WEEKDAY_Y)));
            } catch (RuntimeException e) {
                holidayRows.add(new HolidayRow(h.id, h.name, "—", "—", h.date));
            }
        }

        List<PolicyConfig.Competency> lib = competencies();
        List<CompRow> compRows = new ArrayList<>();
        for (PolicyConfig.Competency c : lib) {
            boolean referenced = referencedByCycle(c.id);
            boolean blocked = lib.size() <= 1 || referenced;
            String title = lib.size() <= 1 ? "Keep at least one competency"
                    : referenced ? "In use by a review cycle" : "Remove competency";
            compRows.add(new CompRow(c.id, c.name, c.blurb == null ? "" : c.blurb, blocked, title));
        }

        List<ChangeTypeView> ctViews = new ArrayList<>();
        for (JobChangeMeta.ChangeType ct : JobChangeMeta.types()) {
            List<String> fields = p.changeTypeFields(ct.id(), ct.fields());
            List<FieldChip> chips = new ArrayList<>();
            for (String f : JobChangeMeta.allFields()) {
                chips.add(new FieldChip(f, JobChangeMeta.fieldLabel(f), fields.contains(f)));
            }
            ctViews.add(new ChangeTypeView(ct.id(), ct.label(), ct.color(), ct.bg(), fields.size(), chips));
        }

        model.addAttribute("policy", p);
        model.addAttribute("dayChips", dayChips);
        model.addAttribute("leaveTypes", p.leaveTypes);
        model.addAttribute("blackoutRows", blackoutRows);
        model.addAttribute("noBlackouts", blackoutRows.isEmpty());
        model.addAttribute("holidayRows", holidayRows);
        model.addAttribute("holidayCount", holidayRows.size());
        model.addAttribute("compRows", compRows);
        model.addAttribute("compCount", compRows.size());
        model.addAttribute("changeTypes", ctViews);
        return "admin/settings";
    }

    private static String rangeLabel(String startIso, String endIso) {
        try {
            LocalDate s = LocalDate.parse(startIso), e = LocalDate.parse(endIso);
            return s.format(MON_D) + " – " + e.format(MON_D) + ", " + e.getYear();
        } catch (RuntimeException ex) {
            return startIso + " → " + endIso;
        }
    }

    // ---- settings actions ----

    @PostMapping("/settings/workweek")
    public String saveWorkweek(@RequestParam(required = false) String targetHours, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        PolicyConfig p = ws().policy;
        int next = intOf(targetHours, p.targetHours, 0, 80);
        if (next != p.targetHours) {
            audit.log(ws(), actorName(), "Policy updated — work week", "Weekly target " + p.targetHours + "h → " + next + "h");
            p.targetHours = next;
            toast(ra, "Weekly target set to " + next + " hours.", OK);
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/workweek/day")
    public String toggleWorkday(@RequestParam String day, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        if (!ALL_DAYS.contains(day)) return "redirect:/settings";
        PolicyConfig p = ws().policy;
        List<String> next = new ArrayList<>();
        for (String d : ALL_DAYS) {
            boolean on = p.workingDays.contains(d);
            if (d.equals(day)) on = !on;
            if (on) next.add(d);
        }
        p.workingDays = next;
        audit.log(ws(), actorName(), "Policy updated — work week", "Working days: " + String.join(", ", next));
        return "redirect:/settings";
    }

    @PostMapping("/settings/allowances")
    public String saveAllowances(@RequestParam Map<String, String> all, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        PolicyConfig p = ws().policy;
        List<String> changes = new ArrayList<>();
        for (PolicyConfig.LeaveType t : p.leaveTypes) {
            String raw = all.get("allow_" + t.id);
            if (raw == null) continue;
            int next = intOf(raw, t.allowance, 0, 365);
            if (next != t.allowance) {
                changes.add(t.label + " " + t.allowance + " → " + next);
                t.allowance = next;
            }
        }
        if (changes.isEmpty()) {
            toast(ra, "Allowances unchanged.", NEUTRAL);
        } else {
            audit.log(ws(), actorName(), "Policy updated — leave allowances", String.join("; ", changes));
            toast(ra, "Leave allowances saved.", OK);
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/rules")
    public String saveRules(@RequestParam(required = false) String noticeDays,
                            @RequestParam(required = false) String ceilingDays,
                            @RequestParam(required = false) String sickCertDays,
                            RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        PolicyConfig p = ws().policy;
        List<String> changes = new ArrayList<>();
        int notice = intOf(noticeDays, p.noticeDays, 0, 365);
        int ceiling = intOf(ceilingDays, p.ceilingDays, 0, 365);
        int cert = intOf(sickCertDays, p.sickCertDays, 0, 365);
        if (notice != p.noticeDays) changes.add("Notice " + p.noticeDays + " → " + notice);
        if (ceiling != p.ceilingDays) changes.add("Manager ceiling " + p.ceilingDays + " → " + ceiling);
        if (cert != p.sickCertDays) changes.add("Sick certificate after " + p.sickCertDays + " → " + cert);
        p.noticeDays = notice;
        p.ceilingDays = ceiling;
        p.sickCertDays = cert;
        if (changes.isEmpty()) {
            toast(ra, "Approval rules unchanged.", NEUTRAL);
        } else {
            audit.log(ws(), actorName(), "Policy updated — approval rules", String.join("; ", changes));
            toast(ra, "Approval rules saved.", OK);
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/blackouts/add")
    public String addBlackout(@RequestParam(required = false) String label,
                              @RequestParam(required = false) String start,
                              @RequestParam(required = false) String end,
                              RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        if (blank(start) || blank(end) || end.compareTo(start) < 0) {
            toast(ra, "Pick a valid start and end date for the blackout.", WARN);
            return "redirect:/settings";
        }
        String name = blank(label) ? "Blackout" : label.trim();
        ws().policy.blackouts.add(new PolicyConfig.Blackout(
                "bo-" + System.currentTimeMillis(), name, start, end, "All teams"));
        audit.log(ws(), actorName(), "Blackout period added", name + " · " + rangeLabel(start, end));
        toast(ra, "Blackout period added.", "#e0483a");
        return "redirect:/settings";
    }

    @PostMapping("/settings/blackouts/remove")
    public String removeBlackout(@RequestParam String id, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        PolicyConfig p = ws().policy;
        PolicyConfig.Blackout victim = null;
        for (PolicyConfig.Blackout b : p.blackouts) {
            if (b.id.equals(id)) victim = b;
        }
        if (victim != null) {
            p.blackouts.remove(victim);
            audit.log(ws(), actorName(), "Blackout period removed", victim.label + " · " + rangeLabel(victim.start, victim.end));
            toast(ra, "Blackout removed.", NEUTRAL);
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/holidays/add")
    public String addHoliday(@RequestParam(required = false) String date,
                             @RequestParam(required = false) String name,
                             RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        if (blank(date)) {
            toast(ra, "Pick a date for the holiday.", WARN);
            return "redirect:/settings";
        }
        String label = blank(name) ? "Holiday" : name.trim();
        ws().policy.holidays.add(new PolicyConfig.Holiday("h" + System.currentTimeMillis(), date, label));
        audit.log(ws(), actorName(), "Public holiday added", label + " · " + date);
        toast(ra, "Holiday added.", OK);
        return "redirect:/settings";
    }

    @PostMapping("/settings/holidays/remove")
    public String removeHoliday(@RequestParam String id, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        PolicyConfig p = ws().policy;
        PolicyConfig.Holiday victim = null;
        for (PolicyConfig.Holiday h : p.holidays) {
            if (h.id.equals(id)) victim = h;
        }
        if (victim != null) {
            p.holidays.remove(victim);
            audit.log(ws(), actorName(), "Public holiday removed", victim.name + " · " + victim.date);
            toast(ra, "Holiday removed.", NEUTRAL);
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/change-types/toggle")
    public String toggleChangeTypeField(@RequestParam String type, @RequestParam String field,
                                        RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        if (!JobChangeMeta.allFields().contains(field)) return "redirect:/settings";
        JobChangeMeta.ChangeType ct = null;
        for (JobChangeMeta.ChangeType t : JobChangeMeta.types()) {
            if (t.id().equals(type)) ct = t;
        }
        if (ct == null) return "redirect:/settings";
        PolicyConfig p = ws().policy;
        List<String> fields = new ArrayList<>(p.changeTypeFields(ct.id(), ct.fields()));
        boolean removed = fields.remove(field);
        if (!removed) fields.add(field);
        p.changeTypeFields.put(ct.id(), fields);
        audit.log(ws(), actorName(), "Job-change type updated — " + ct.label(),
                (removed ? "Removed " : "Added ") + JobChangeMeta.fieldLabel(field)
                        + " (" + fields.size() + " fields)");
        return "redirect:/settings";
    }

    @PostMapping("/settings/competencies/add")
    public String addCompetency(@RequestParam(required = false) String name, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        if (blank(name)) {
            toast(ra, "Give the competency a name.", WARN);
            return "redirect:/settings";
        }
        List<PolicyConfig.Competency> lib = competencies();
        lib.add(new PolicyConfig.Competency("c" + System.currentTimeMillis(), name.trim(), ""));
        audit.log(ws(), actorName(), "Competency added", name.trim());
        toast(ra, "Competency added.", OK);
        return "redirect:/settings";
    }

    @PostMapping("/settings/competencies/update")
    public String updateCompetency(@RequestParam String id,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) String blurb,
                                   RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        competencies();
        PolicyConfig.Competency c = ws().policy.competency(id);
        if (c == null) return "redirect:/settings";
        List<String> changes = new ArrayList<>();
        if (!blank(name) && !name.trim().equals(c.name)) {
            changes.add(c.name + " → " + name.trim());
            c.name = name.trim();
        }
        String nextBlurb = blurb == null ? "" : blurb.trim();
        if (!nextBlurb.equals(c.blurb == null ? "" : c.blurb)) {
            if (changes.isEmpty()) changes.add("Description updated");
            c.blurb = nextBlurb;
        }
        if (changes.isEmpty()) {
            toast(ra, "Competency unchanged.", NEUTRAL);
        } else {
            audit.log(ws(), actorName(), "Competency updated", String.join("; ", changes));
            toast(ra, "Competency saved.", OK);
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/competencies/remove")
    public String removeCompetency(@RequestParam String id, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_SETTINGS)) return "redirect:/settings";
        List<PolicyConfig.Competency> lib = competencies();
        PolicyConfig.Competency c = ws().policy.competency(id);
        if (c == null) return "redirect:/settings";
        if (lib.size() <= 1) {
            toast(ra, "Keep at least one competency in the library.", WARN);
            return "redirect:/settings";
        }
        if (referencedByCycle(id)) {
            toast(ra, c.name + " is used by a review cycle and can’t be removed.", WARN);
            return "redirect:/settings";
        }
        lib.remove(c);
        audit.log(ws(), actorName(), "Competency removed", c.name);
        toast(ra, "Competency removed.", NEUTRAL);
        return "redirect:/settings";
    }

    // ===================== org structure =====================

    private int deptHeadcount(String dept) {
        int n = 0;
        for (Employee e : people.all()) {
            if (dept.equals(e.dept)) n++;
        }
        return n;
    }

    private int levelHeadcount(String level) {
        int n = 0;
        for (Employee e : people.all()) {
            if (level.equals(e.level)) n++;
        }
        return n;
    }

    private int bandHeadcount(String bandId) {
        int n = 0;
        for (Employee e : people.all()) {
            if (bandId.equals(e.band)) n++;
        }
        return n;
    }

    @GetMapping("/org")
    public String org(Model model) {
        if (!guard(model, "org")) return "admin/org";
        OrgConfig org = ws().org;

        List<DeptRow> depts = new ArrayList<>();
        for (OrgConfig.Department d : org.departments) {
            Employee lead = d.lead == null ? null : people.get(d.lead);
            int count = deptHeadcount(d.id);
            boolean canRemove = count == 0;
            depts.add(new DeptRow(d.id, d.color, d.tint, lead == null ? "—" : lead.fullName(), count,
                    canRemove, canRemove ? "Remove department" : "Reassign its " + count + " people first"));
        }

        List<LevelRow> levels = new ArrayList<>();
        for (int i = 0; i < org.levels.size(); i++) {
            String name = org.levels.get(i);
            int count = levelHeadcount(name);
            boolean canRemove = count == 0;
            levels.add(new LevelRow(name, i + 1, count, i == 0, i == org.levels.size() - 1,
                    canRemove, canRemove ? "Remove level" : count + " people are at this level"));
        }

        Map<String, String> accents = Map.of("IC", "#3f7cc4", "Manager", "#9a6ab5", "Executive", "#c68a2a");
        List<TrackGroup> trackGroups = new ArrayList<>();
        for (String track : OrgConfig.TRACKS) {
            List<BandRow> rows = new ArrayList<>();
            for (OrgConfig.Band b : org.bands) {
                if (!track.equals(b.track)) continue;
                int count = bandHeadcount(b.id);
                boolean canRemove = count == 0;
                rows.add(new BandRow(b.id, b.label, b.min, b.max, count,
                        canRemove, canRemove ? "Remove band" : count + " people are on this band"));
            }
            trackGroups.add(new TrackGroup(track, accents.getOrDefault(track, "#5a6472"), rows));
        }

        model.addAttribute("depts", depts);
        model.addAttribute("levels", levels);
        model.addAttribute("trackGroups", trackGroups);
        return "admin/org";
    }

    // ---- org actions ----

    @PostMapping("/org/departments/add")
    public String addDepartment(@RequestParam(required = false) String name, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        if (blank(name)) {
            toast(ra, "Give the department a name.", WARN);
            return "redirect:/org";
        }
        OrgConfig.Department d = ws().org.addDepartment(name);
        if (d == null) {
            toast(ra, "A department with that name already exists.", WARN);
        } else {
            audit.log(ws(), actorName(), "Department added", d.id);
            toast(ra, "“" + d.id + "” added.", OK);
        }
        return "redirect:/org";
    }

    @PostMapping("/org/departments/remove")
    public String removeDepartment(@RequestParam String name, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        int count = deptHeadcount(name);
        if (count > 0) {
            toast(ra, "Reassign its " + count + " people before removing this department.", WARN);
            return "redirect:/org";
        }
        if (ws().org.removeDepartment(name)) {
            audit.log(ws(), actorName(), "Department removed", name);
            toast(ra, "“" + name + "” removed.", NEUTRAL);
        }
        return "redirect:/org";
    }

    @PostMapping("/org/levels/add")
    public String addLevel(@RequestParam(required = false) String name, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        if (blank(name)) {
            toast(ra, "Give the level a name.", WARN);
            return "redirect:/org";
        }
        if (ws().org.addLevel(name)) {
            audit.log(ws(), actorName(), "Level added", name.trim());
            toast(ra, "Level “" + name.trim() + "” added.", OK);
        } else {
            toast(ra, "That level already exists.", WARN);
        }
        return "redirect:/org";
    }

    @PostMapping("/org/levels/remove")
    public String removeLevel(@RequestParam String name, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        int count = levelHeadcount(name);
        if (count > 0) {
            toast(ra, count + " people are at “" + name + "” — move them first.", WARN);
            return "redirect:/org";
        }
        if (ws().org.removeLevel(name)) {
            audit.log(ws(), actorName(), "Level removed", name);
            toast(ra, "Level “" + name + "” removed.", NEUTRAL);
        }
        return "redirect:/org";
    }

    @PostMapping("/org/levels/move")
    public String moveLevel(@RequestParam String name, @RequestParam int dir, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        if (ws().org.moveLevel(name, dir)) {
            audit.log(ws(), actorName(), "Levels reordered",
                    "“" + name + "” moved " + (dir < 0 ? "up" : "down"));
        }
        return "redirect:/org";
    }

    @PostMapping("/org/bands/add")
    public String addBand(@RequestParam String track, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        OrgConfig.Band b = ws().org.addBand(track);
        audit.log(ws(), actorName(), "Comp band added", b.id + " on the " + b.track + " track");
        toast(ra, "Band " + b.id + " added to the " + b.track + " track.", OK);
        return "redirect:/org";
    }

    @PostMapping("/org/bands/update")
    public String updateBand(@RequestParam String id,
                             @RequestParam(required = false) String label,
                             @RequestParam(required = false) String min,
                             @RequestParam(required = false) String max,
                             RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        OrgConfig.Band b = ws().org.bandMeta(id);
        if (b == null) return "redirect:/org";
        int nextMin = intOf(min, b.min, 0, 10_000_000);
        int nextMax = intOf(max, b.max, 0, 10_000_000);
        if (nextMax < nextMin) {
            toast(ra, "Band " + id + ": max must be at least the min.", WARN);
            return "redirect:/org";
        }
        List<String> changes = new ArrayList<>();
        if (!blank(label) && !label.trim().equals(b.label)) {
            changes.add(b.label + " → " + label.trim());
            b.label = label.trim();
        }
        if (nextMin != b.min || nextMax != b.max) {
            changes.add("range $" + b.min + "–$" + b.max + " → $" + nextMin + "–$" + nextMax);
            b.min = nextMin;
            b.max = nextMax;
        }
        if (changes.isEmpty()) {
            toast(ra, "Band " + id + " unchanged.", NEUTRAL);
        } else {
            audit.log(ws(), actorName(), "Comp band updated — " + id, String.join("; ", changes));
            toast(ra, "Band " + id + " saved.", OK);
        }
        return "redirect:/org";
    }

    @PostMapping("/org/bands/remove")
    public String removeBand(@RequestParam String id, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ORG)) return "redirect:/org";
        int count = bandHeadcount(id);
        if (count > 0) {
            toast(ra, count + " people are on band " + id + " — move them first.", WARN);
            return "redirect:/org";
        }
        if (ws().org.removeBand(id)) {
            audit.log(ws(), actorName(), "Comp band removed", id);
            toast(ra, "Band " + id + " removed.", NEUTRAL);
        }
        return "redirect:/org";
    }

    // ===================== roles & access =====================

    private long hrCount() {
        long n = 0;
        for (Employee e : people.all()) {
            if (e.accessRole == Role.HR) n++;
        }
        return n;
    }

    @GetMapping("/roles")
    public String roles(@RequestParam(required = false, defaultValue = "all") String filter, Model model) {
        if (!guard(model, "roles")) return "admin/roles";
        Employee me = session.currentUser();
        long hrUsers = hrCount();

        Map<String, String[]> selStyles = Map.of(
                "employee", new String[]{"#f8fafc", "#dde2e8", "#5a6472"},
                "manager", new String[]{"#e8eefb", "#c9d6ef", "#3a5aa8"},
                "hr", new String[]{"#e6eefb", "#bcd0ea", "#17457f"});

        List<Employee> sorted = new ArrayList<>(people.all());
        Map<String, Integer> rank = Map.of("hr", 0, "manager", 1, "employee", 2);
        sorted.sort(Comparator
                .comparingInt((Employee e) -> rank.getOrDefault(e.accessRole.key, 2))
                .thenComparing(Employee::fullName, String.CASE_INSENSITIVE_ORDER));

        List<PersonRow> rows = new ArrayList<>();
        int total = sorted.size();
        for (Employee e : sorted) {
            String roleKey = e.accessRole.key;
            if (!"all".equals(filter) && !filter.equals(roleKey)) continue;
            boolean self = me != null && me.id.equals(e.id);
            boolean lastHr = e.accessRole == Role.HR && hrUsers <= 1;
            String lockTitle = self ? "You can’t change your own access level"
                    : lastHr ? "The only remaining HR executive — assign another first" : "";
            String[] ss = selStyles.getOrDefault(roleKey, selStyles.get("employee"));
            int reports = people.directReports(e.id).size();
            rows.add(new PersonRow(e.id, e.fullName(), e.initials, e.avatarBg, e.title,
                    e.dept, people.deptMeta(e.dept).color,
                    reports > 0 ? reports + (reports == 1 ? " report" : " reports") : "—",
                    roleKey, e.accessRole.label, self || lastHr, lockTitle, ss[0], ss[1], ss[2]));
        }

        List<FilterChip> filters = new ArrayList<>();
        for (String[] f : new String[][]{{"all", "All"}, {"employee", "Employees"}, {"manager", "Managers"}, {"hr", "HR"}}) {
            filters.add(new FilterChip(f[0], f[1], filter.equals(f[0])));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("shownCount", rows.size());
        model.addAttribute("totalCount", total);
        model.addAttribute("filters", filters);
        model.addAttribute("permGroups", permissionMatrix());
        model.addAttribute("roleCols", List.of(Role.EMPLOYEE.label, Role.MANAGER.label, Role.HR.label));
        return "admin/roles";
    }

    @PostMapping("/roles/set")
    public String setRole(@RequestParam String empId, @RequestParam String role, RedirectAttributes ra) {
        if (!policy.can(Permission.ADMIN_ROLES)) return "redirect:/roles";
        Employee target = people.get(empId);
        Employee me = session.currentUser();
        if (target == null) return "redirect:/roles";
        Role next = Role.fromKey(role);
        if (next == target.accessRole) return "redirect:/roles";
        if (me != null && me.id.equals(target.id)) {
            toast(ra, "You can’t change your own access level.", WARN);
            return "redirect:/roles";
        }
        if (target.accessRole == Role.HR && next != Role.HR && hrCount() <= 1) {
            toast(ra, "That would leave no HR executive — assign another person to HR first.", WARN);
            return "redirect:/roles";
        }
        Role before = target.accessRole;
        target.accessRole = next;
        audit.log(ws(), actorName(), "Access role changed — " + target.fullName(),
                before.label + " → " + next.label);
        toast(ra, target.fullName() + " is now " + next.label + ".", "#4a86d8");
        return "redirect:/roles";
    }

    /** The RBAC catalog as a matrix: permissions grouped by area, each showing which roles hold it. */
    private static List<PermGroup> permissionMatrix() {
        Map<String, List<PermRow>> byGroup = new LinkedHashMap<>();
        for (Permission p : Permission.values()) {
            byGroup.computeIfAbsent(p.group(), k -> new ArrayList<>()).add(new PermRow(
                    p.label(),
                    RolePermissions.has(Role.EMPLOYEE, p),
                    RolePermissions.has(Role.MANAGER, p),
                    RolePermissions.has(Role.HR, p)));
        }
        List<PermGroup> out = new ArrayList<>();
        byGroup.forEach((group, rows) -> out.add(new PermGroup(group, rows)));
        return out;
    }

    // ===================== audit log =====================

    @GetMapping("/audit")
    public String auditLog(@RequestParam(required = false) String category,
                           @RequestParam(required = false) String q,
                           Model model) {
        if (!guard(model, "audit")) return "admin/audit";
        Workspace ws = ws();
        List<Entry> entries = new ArrayList<>();

        for (Employee e : ws.employees) {
            for (Employee.HistoryEvent h : e.history) {
                entries.add(new Entry(h.at, h.by, h.label + " — " + e.fullName(), h.detail,
                        cat(h.type), catColor(h.type), catBg(h.type)));
            }
        }
        for (LeaveRequest r : ws.leaveRequests) {
            for (LeaveRequest.Event ev : r.events) {
                entries.add(new Entry(ev.at, ev.actorName, "Leave " + ev.kind + " — " + r.empName,
                        ev.note == null ? "" : ev.note, "Leave", "#2f6aa8", "#e9f0f9"));
            }
        }
        for (ProfileChange c : ws.profileChanges) {
            long at = c.decidedAt != 0 ? c.decidedAt : c.requestedAt;
            entries.add(new Entry(at, c.requestedBy, "Profile change (" + c.status + ") — " + c.label,
                    c.oldValue + " → " + c.newValue, "Profile", "#7a5aa8", "#f0ecf8"));
        }
        for (JobChange j : ws.jobChanges) {
            if (j.decidedAt != 0) {
                entries.add(new Entry(j.decidedAt, j.decidedByName, "Job change (" + j.status + ") — " + j.empName,
                        j.type, "Job change", "#9a6a1a", "#f7f1e0"));
            }
        }
        for (OffboardingCase o : ws.offboardingCases) {
            if ("completed".equals(o.status)) {
                entries.add(new Entry(o.completedAt, "HR", "Offboarded — " + o.empName, o.type, "Exit", "#b23b2e", "#fbeae8"));
            }
        }
        for (AdminAudit.Event ev : audit.trail(ws)) {
            entries.add(new Entry(ev.at(), ev.actor(), ev.action(), ev.detail(), "Admin", "#17457f", "#e6eefb"));
        }

        entries.sort(Comparator.comparingLong((Entry e) -> e.at).reversed());

        // Filter chips (category) + free-text search over actor/action/detail — applied
        // BEFORE the 80-row cap so a filter surfaces older matching events, not just
        // whatever survived the cap.
        List<String> categories = new ArrayList<>();
        for (Entry e : entries) {
            if (!categories.contains(e.category)) categories.add(e.category);
        }
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<Entry> visible = new ArrayList<>();
        for (Entry e : entries) {
            if (category != null && !category.isBlank() && !category.equals(e.category)) continue;
            if (!needle.isEmpty()) {
                String hay = ((e.actor == null ? "" : e.actor) + " " + e.action + " " + e.detail).toLowerCase();
                if (!hay.contains(needle)) continue;
            }
            visible.add(e);
        }

        List<AuditRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(visible.size(), 80); i++) {
            Entry e = visible.get(i);
            rows.add(new AuditRow(e.at == 0 ? "—" : STAMP.format(Instant.ofEpochMilli(e.at)),
                    e.actor == null ? "System" : e.actor, e.action, e.detail, e.category, e.color, e.bg));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("total", visible.size());
        model.addAttribute("categories", categories);
        model.addAttribute("activeCategory", category == null ? "" : category);
        model.addAttribute("q", q == null ? "" : q);
        return "admin/audit";
    }

    private static String cat(String type) {
        return switch (type) {
            case "promotion", "comp" -> "Comp/level";
            case "transfer" -> "Transfer";
            case "status", "exit" -> "Status";
            case "join" -> "Hire";
            default -> "Job";
        };
    }

    private static String catColor(String type) {
        return switch (type) {
            case "promotion", "comp" -> "#9a6a1a";
            case "transfer" -> "#3a5aa8";
            case "status", "exit" -> "#b23b2e";
            case "join" -> "#2f6f4f";
            default -> "#5a6472";
        };
    }

    private static String catBg(String type) {
        return switch (type) {
            case "promotion", "comp" -> "#f7f1e0";
            case "transfer" -> "#e8eefb";
            case "status", "exit" -> "#fbeae8";
            case "join" -> "#e6f3ec";
            default -> "#eef1f4";
        };
    }

    private record Entry(long at, String actor, String action, String detail, String category, String color, String bg) {
    }

    // ---- view records ----

    public record DayChip(String id, String label, boolean on) {
    }

    public record BlackoutRow(String id, String label, String range, String scope) {
    }

    public record HolidayRow(String id, String name, String mon, String day, String weekday) {
    }

    public record CompRow(String id, String name, String blurb, boolean removeBlocked, String removeTitle) {
    }

    public record FieldChip(String field, String label, boolean on) {
    }

    public record ChangeTypeView(String id, String label, String color, String bg, int fieldCount,
                                 List<FieldChip> chips) {
    }

    public record DeptRow(String name, String color, String tint, String lead, int count,
                          boolean canRemove, String removeTitle) {
    }

    public record LevelRow(String name, int rank, int count, boolean first, boolean last,
                           boolean canRemove, String removeTitle) {
    }

    public record TrackGroup(String track, String accent, List<BandRow> bands) {
    }

    public record BandRow(String id, String label, int min, int max, int count,
                          boolean canRemove, String removeTitle) {
    }

    public record PersonRow(String id, String name, String initials, String avatarBg, String title,
                            String dept, String deptColor, String reports, String roleKey, String roleLabel,
                            boolean locked, String lockTitle, String selBg, String selBorder, String selFg) {
    }

    public record FilterChip(String id, String label, boolean on) {
    }

    public record PermGroup(String group, List<PermRow> rows) {
    }

    public record PermRow(String label, boolean employee, boolean manager, boolean hr) {
    }

    public record AuditRow(String when, String actor, String action, String detail, String category, String color, String bg) {
    }
}
