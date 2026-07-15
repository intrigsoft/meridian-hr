package com.meridian.hr.web;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.leave.LeaveService;
import com.meridian.hr.session.SessionContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The post-login landing: greeting, leave-balance tiles, and who's out. */
@Controller
public class DashboardController {

    private final SessionContext session;
    private final LeaveService leave;

    public DashboardController(SessionContext session, LeaveService leave) {
        this.session = session;
        this.leave = leave;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Employee me = session.currentUser();

        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        model.addAttribute("greeting", greeting);
        model.addAttribute("today", LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)));

        // Real leave balances (allowance minus used) for the signed-in user.
        model.addAttribute("balances", leave.balances(me.id));

        // Who's out: anyone currently on leave (data-driven).
        List<OutRow> out = new ArrayList<>();
        for (Employee e : session.workspace().employees) {
            if (e.status == EmployeeStatus.LEAVE) {
                out.add(new OutRow(e.fullName(), e.initials, e.avatarBg, e.title + " · " + e.dept));
            }
        }
        model.addAttribute("teamOut", out);
        model.addAttribute("active", "dashboard");
        return "dashboard";
    }

    public record OutRow(String name, String initials, String avatarBg, String detail) {
    }
}
