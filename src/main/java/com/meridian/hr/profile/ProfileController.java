package com.meridian.hr.profile;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.ProfileChange;
import com.meridian.hr.people.PeopleService;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * My Profile: self-service view/edit of one's own record. Non-sensitive fields apply
 * immediately; sensitive fields (legal name, bank, tax) route through HR approval via
 * {@link PeopleService#requestChange}. Lists (emergency/dependents/skills/certs) edit
 * in place. HR decides the requests on the employee's directory profile.
 */
@Controller
public class ProfileController {

    private final PeopleService people;
    private final SessionContext session;

    public ProfileController(PeopleService people, SessionContext session) {
        this.people = people;
        this.session = session;
    }

    // About-you fields (path, label, sensitive, type).
    private static final String[][] INFO = {
            {"legalName", "Legal name", "1", "text"}, {"preferredName", "Preferred name", "0", "text"},
            {"dob", "Date of birth", "0", "date"}, {"gender", "Gender", "0", "text"},
            {"pronouns", "Pronouns", "0", "text"}, {"personalEmail", "Personal email", "0", "text"},
            {"personalPhone", "Personal phone", "0", "text"}, {"address.line1", "Home address", "0", "text"},
            {"address.city", "City", "0", "text"}, {"address.state", "State", "0", "text"},
            {"address.zip", "ZIP", "0", "text"}};

    private static final String[][] BANK = {
            {"bank.bankName", "Bank name", "0"}, {"bank.accountName", "Account holder", "0"},
            {"bank.accountLast4", "Account (last 4)", "1"}, {"bank.routingLast4", "Routing (last 4)", "1"},
            {"taxIds.ssnLast4", "Tax ID (last 4)", "1"}, {"taxIds.nationalId", "National ID", "0"}};

    @GetMapping("/profile")
    public String profile(@RequestParam(required = false) String edit, Model model) {
        Employee me = session.currentUser();
        boolean editInfo = "info".equals(edit);
        boolean editBank = "bank".equals(edit);

        PeopleService.Completeness comp = people.completeness(me);
        Employee mgr = people.managerOf(me.id);

        model.addAttribute("me", me);
        model.addAttribute("comp", comp);
        model.addAttribute("compColor", comp.pct() == 100 ? "#2f6f4f" : (comp.pct() >= 60 ? "#17457f" : "#c68a2a"));
        model.addAttribute("managerName", mgr == null ? "—" : mgr.fullName());
        model.addAttribute("salaryLabel", PeopleService.formatSalary(me.salary, me.currency));
        model.addAttribute("startLabel", PeopleService.formatDate(me.startDate) + " · " + PeopleService.tenureLabel(me.startDate));

        // About you
        model.addAttribute("editInfo", editInfo);
        model.addAttribute("infoFields", infoFields(me, editInfo));
        // Bank & tax
        model.addAttribute("editBank", editBank);
        model.addAttribute("bankFields", bankFields(me, editBank));
        model.addAttribute("bankPending", people.pendingChangesFor(me.id).size());
        // lists
        model.addAttribute("levelOptions", List.of("Beginner", "Intermediate", "Advanced", "Expert"));
        model.addAttribute("active", "profile");
        model.addAttribute("noteTitle", "Profile " + comp.pct() + "% complete");
        return "profile/my-profile";
    }

    @PostMapping("/profile/info")
    public String saveInfo(@RequestParam java.util.Map<String, String> all, RedirectAttributes ra) {
        Employee me = session.currentUser();
        int sensitive = 0;
        for (String[] f : INFO) {
            String path = f[0];
            String key = path.replace(".", "_");
            if (!all.containsKey(key)) continue;
            String nv = all.get(key) == null ? "" : all.get(key).trim();
            String cur = people.getPath(me, path);
            if (nv.equals(cur)) continue;
            if (people.isSensitive(path)) {
                people.requestChange(me.id, path, f[1], cur, nv, me.id);
                sensitive++;
            } else {
                people.setPath(me, path, nv);
            }
        }
        ra.addFlashAttribute("toast", sensitive > 0 ? "Saved. Legal-name change sent to HR for approval." : "Profile updated.");
        ra.addFlashAttribute("toastDot", sensitive > 0 ? "#e0a13a" : "#3ecf8e");
        return "redirect:/profile";
    }

    @PostMapping("/profile/bank")
    public String saveBank(@RequestParam java.util.Map<String, String> all, RedirectAttributes ra) {
        Employee me = session.currentUser();
        int n = 0;
        for (String[] f : BANK) {
            String path = f[0];
            String key = path.replace(".", "_");
            if (!all.containsKey(key)) continue;
            String nv = all.get(key) == null ? "" : all.get(key).trim();
            String cur = people.getPath(me, path);
            if (nv.equals(cur)) continue;
            people.requestChange(me.id, path, f[1], cur, nv, me.id);
            n++;
        }
        ra.addFlashAttribute("toast", n > 0 ? n + " change(s) submitted to HR for approval." : "No changes to submit.");
        ra.addFlashAttribute("toastDot", n > 0 ? "#e0a13a" : "#8894a3");
        return "redirect:/profile";
    }

    // ---- list editing (emergency / dependents / skills / certs) ----

    @PostMapping("/profile/lists")
    public String saveLists(@RequestParam org.springframework.util.MultiValueMap<String, String> params,
                            @RequestParam(required = false) String add,
                            @RequestParam(required = false) String remove,
                            RedirectAttributes ra) {
        Employee me = session.currentUser();

        me.emergencyContacts.clear();
        List<String> en = params.getOrDefault("em_name", List.of());
        List<String> er = params.getOrDefault("em_rel", List.of());
        List<String> ep = params.getOrDefault("em_phone", List.of());
        for (int i = 0; i < en.size(); i++) {
            String n = at(en, i), r = at(er, i), p = at(ep, i);
            if (!(n + r + p).isBlank()) me.emergencyContacts.add(new Employee.EmergencyContact(n, r, p));
        }

        me.dependents.clear();
        List<String> dn = params.getOrDefault("dep_name", List.of());
        List<String> dr = params.getOrDefault("dep_rel", List.of());
        List<String> dd = params.getOrDefault("dep_dob", List.of());
        for (int i = 0; i < dn.size(); i++) {
            String n = at(dn, i), r = at(dr, i), d = at(dd, i);
            if (!(n + r + d).isBlank()) me.dependents.add(new Employee.Dependent(n, r, d));
        }

        me.skills.clear();
        List<String> sn = params.getOrDefault("sk_name", List.of());
        List<String> sl = params.getOrDefault("sk_level", List.of());
        for (int i = 0; i < sn.size(); i++) {
            String n = at(sn, i), l = at(sl, i);
            if (!n.isBlank()) me.skills.add(new Employee.Skill(n, l.isBlank() ? "Intermediate" : l));
        }

        me.certifications.clear();
        List<String> cn = params.getOrDefault("ct_name", List.of());
        List<String> ci = params.getOrDefault("ct_issuer", List.of());
        for (int i = 0; i < cn.size(); i++) {
            String n = at(cn, i), iss = at(ci, i);
            if (!n.isBlank()) me.certifications.add(new Employee.Certification(n, iss, ""));
        }

        // Add/remove operate AFTER the rebuild so in-progress edits survive.
        if (add != null) {
            switch (add) {
                case "em" -> me.emergencyContacts.add(new Employee.EmergencyContact("", "", ""));
                case "dep" -> me.dependents.add(new Employee.Dependent("", "", ""));
                case "sk" -> me.skills.add(new Employee.Skill("", "Intermediate"));
                case "ct" -> me.certifications.add(new Employee.Certification("", "", ""));
                default -> {
                }
            }
        } else if (remove != null && remove.contains(":")) {
            String which = remove.substring(0, remove.indexOf(':'));
            int idx = parseInt(remove.substring(remove.indexOf(':') + 1));
            switch (which) {
                case "em" -> removeAt(me.emergencyContacts, idx);
                case "dep" -> removeAt(me.dependents, idx);
                case "sk" -> removeAt(me.skills, idx);
                case "ct" -> removeAt(me.certifications, idx);
                default -> {
                }
            }
        } else {
            ra.addFlashAttribute("toast", "Profile updated.");
            ra.addFlashAttribute("toastDot", "#3ecf8e");
        }
        return "redirect:/profile";
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- view builders ----

    private List<InfoField> infoFields(Employee me, boolean editing) {
        List<InfoField> out = new ArrayList<>();
        for (String[] f : INFO) {
            String path = f[0], label = f[1];
            boolean sensitive = "1".equals(f[2]);
            String type = f[3];
            String cur = people.getPath(me, path);
            String display = "date".equals(type) && !cur.isBlank() ? PeopleService.formatDate(cur) : (cur.isBlank() ? "—" : cur);
            boolean pending = people.hasPendingChange(me.id, path);
            out.add(new InfoField(path.replace(".", "_"), label, sensitive, type, cur, display,
                    cur.isBlank() ? "#b6bdc6" : "#28323f", pending && !editing));
        }
        return out;
    }

    private List<BankField> bankFields(Employee me, boolean editing) {
        List<BankField> out = new ArrayList<>();
        for (String[] f : BANK) {
            String path = f[0];
            String cur = people.getPath(me, path);
            boolean mask = path.endsWith("Last4");
            String display = cur.isBlank() ? "—" : (mask ? "•••• " + cur : cur);
            out.add(new BankField(path.replace(".", "_"), f[1], cur, display,
                    mask ? "var(--mono)" : "inherit"));
        }
        return out;
    }

    private static <T> void removeAt(List<T> list, int i) {
        if (i >= 0 && i < list.size()) list.remove(i);
    }

    private static String at(List<String> l, int i) {
        return i < l.size() && l.get(i) != null ? l.get(i).trim() : "";
    }

    public record InfoField(String key, String label, boolean sensitive, String type,
                            String value, String display, String textColor, boolean pending) {
    }

    public record BankField(String key, String label, String value, String display, String mono) {
    }
}
