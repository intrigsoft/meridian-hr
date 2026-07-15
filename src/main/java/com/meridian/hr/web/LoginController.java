package com.meridian.hr.web;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.Role;
import com.meridian.hr.session.SessionContext;
import com.meridian.hr.session.SessionService;
import com.meridian.hr.workspace.WorkspaceStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/** Login (demo identity picker + email sign-in), sign-out, and "start fresh" reset. */
@Controller
public class LoginController {

    /** The three quick-pick demo personas shown on the login screen (one per role). */
    private static final List<String> DEMO_IDS = List.of("sarah.chen", "david.okonkwo", "priya.nair");

    private final SessionContext session;
    private final SessionService sessions;
    private final WorkspaceStore store;

    public LoginController(SessionContext session, SessionService sessions, WorkspaceStore store) {
        this.session = session;
        this.sessions = sessions;
        this.store = store;
    }

    @GetMapping("/login")
    public String login(Model model, @RequestParam(required = false) String error) {
        if (session.isSignedIn()) {
            return "redirect:/";
        }
        List<DemoAccount> demos = new ArrayList<>();
        for (String id : DEMO_IDS) {
            Employee e = session.workspace().employee(id);
            if (e != null) demos.add(DemoAccount.of(e));
        }
        model.addAttribute("demos", demos);
        model.addAttribute("error", error != null);
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam(required = false) String userId,
                          @RequestParam(required = false) String email) {
        Employee user = sessions.signIn(session.workspace(), userId, email);
        return user != null ? "redirect:/" : "redirect:/login?error";
    }

    @PostMapping("/logout")
    public String logout() {
        sessions.signOut(session.workspace());
        return "redirect:/login";
    }

    @PostMapping("/reset")
    public String reset() {
        // Restore this device to a brand-new seeded workspace, then land on login.
        store.resetDevice(session.deviceId());
        return "redirect:/login";
    }

    /** View row for a demo persona with its role-pill styling. */
    public record DemoAccount(String id, String name, String title, String initials, String avatarBg,
                              String roleLabel, String roleColor, String roleTint) {
        static DemoAccount of(Employee e) {
            String color, tint;
            if (e.accessRole == Role.HR) {
                color = "#3a5aa8";
                tint = "#e8eefb";
            } else if (e.accessRole == Role.MANAGER) {
                color = "#9a6a1a";
                tint = "#f6eede";
            } else {
                color = "#2f6f4f";
                tint = "#e6f3ec";
            }
            return new DemoAccount(e.id, e.fullName(), e.title, e.initials, e.avatarBg,
                    e.accessRole.label, color, tint);
        }
    }
}
