package com.meridian.hr.workspace;

import com.meridian.hr.domain.Employee;
import com.meridian.hr.domain.Employee.Asset;
import com.meridian.hr.domain.Employee.Certification;
import com.meridian.hr.domain.Employee.Dependent;
import com.meridian.hr.domain.Employee.EmergencyContact;
import com.meridian.hr.domain.Employee.HistoryEvent;
import com.meridian.hr.domain.Employee.Skill;
import com.meridian.hr.domain.EmployeeStatus;
import com.meridian.hr.domain.Candidate;
import com.meridian.hr.domain.JobChange;
import com.meridian.hr.domain.LeaveRequest;
import com.meridian.hr.domain.Requisition;
import com.meridian.hr.domain.OffboardingCase;
import com.meridian.hr.domain.OnboardingCase;
import com.meridian.hr.domain.OnboardingTemplate;
import com.meridian.hr.domain.OnboardingTemplate.Step;
import com.meridian.hr.domain.OrgConfig;
import com.meridian.hr.domain.PolicyConfig;
import com.meridian.hr.domain.Review;
import com.meridian.hr.domain.ReviewCycle;
import com.meridian.hr.domain.Role;
import com.meridian.hr.domain.Timesheet;
import com.meridian.hr.leave.LeaveMeta;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.meridian.hr.domain.EmployeeStatus.ACTIVE;
import static com.meridian.hr.domain.EmployeeStatus.LEAVE;
import static com.meridian.hr.domain.EmployeeStatus.ONBOARDING;
import static com.meridian.hr.domain.Role.EMPLOYEE;
import static com.meridian.hr.domain.Role.HR;
import static com.meridian.hr.domain.Role.MANAGER;

/**
 * Builds a BRAND-NEW workspace object graph on every call, so each device is fully
 * isolated. Ported from the fixture's people/org/config seed data (relationships
 * drive the whole demo). Later domains extend this in their own phases.
 */
public final class Seed {

    private Seed() {
    }

    public static Workspace build() {
        Workspace ws = new Workspace();
        seedOrg(ws.org);
        seedPolicy(ws.policy);
        seedPeople(ws);
        seedLeave(ws);
        seedTime(ws);
        seedOnboarding(ws);
        seedOffboarding(ws);
        seedJobChanges(ws);
        seedPerformance(ws);
        seedRecruitment(ws);
        return ws;
    }

    // ===================== org taxonomy =====================

    private static void seedOrg(OrgConfig org) {
        org.departments.add(new OrgConfig.Department("Engineering", "#3f7cc4", "#e9f0f9", "david.okonkwo"));
        org.departments.add(new OrgConfig.Department("Design", "#9a6ab5", "#f0ecf8", "nadia.rahman"));
        org.departments.add(new OrgConfig.Department("Revenue", "#4a9d7a", "#e9f4ef", "elena.vasquez"));
        org.departments.add(new OrgConfig.Department("Operations", "#c68a2a", "#faf3e6", "marco.rossi"));
        org.departments.add(new OrgConfig.Department("People Operations", "#b56b8f", "#f7ecf1", "priya.nair"));

        org.levels.addAll(List.of("Junior", "Mid", "Senior", "Staff", "Lead", "Manager", "Executive"));

        org.bands.add(new OrgConfig.Band("IC1", "IC", "Associate", 72000, 94000));
        org.bands.add(new OrgConfig.Band("IC2", "IC", "Engineer / Designer", 90000, 118000));
        org.bands.add(new OrgConfig.Band("IC3", "IC", "Senior IC", 112000, 145000));
        org.bands.add(new OrgConfig.Band("IC4", "IC", "Staff IC", 140000, 178000));
        org.bands.add(new OrgConfig.Band("IC5", "IC", "Principal IC", 170000, 212000));
        org.bands.add(new OrgConfig.Band("M3", "Manager", "Manager", 150000, 192000));
        org.bands.add(new OrgConfig.Band("M4", "Manager", "Senior Manager", 182000, 228000));
        org.bands.add(new OrgConfig.Band("E1", "Executive", "Director / VP", 235000, 310000));
    }

    // ===================== policy / config =====================

    private static void seedPolicy(PolicyConfig p) {
        p.targetHours = 40;
        p.noticeDays = 7;
        p.ceilingDays = 10;
        p.sickCertDays = 2;

        p.leaveTypes.add(new PolicyConfig.LeaveType("annual", "Annual leave", 25, "#3f7cc4"));
        p.leaveTypes.add(new PolicyConfig.LeaveType("sick", "Sick leave", 10, "#5aa17f"));
        p.leaveTypes.add(new PolicyConfig.LeaveType("personal", "Personal leave", 5, "#c99b4e"));
        p.leaveTypes.add(new PolicyConfig.LeaveType("parental", "Parental leave", 90, "#9b7fc4"));
        p.leaveTypes.add(new PolicyConfig.LeaveType("unpaid", "Unpaid leave", 0, "#8894a3"));
        p.leaveTypes.add(new PolicyConfig.LeaveType("bereavement", "Bereavement", 5, "#b56b8f"));

        p.blackouts.add(new PolicyConfig.Blackout("bo-yearend", "Year-end freeze", "2026-12-20", "2026-12-31", "All teams"));

        p.holidays.add(new PolicyConfig.Holiday("h1", "2026-01-01", "New Year's Day"));
        p.holidays.add(new PolicyConfig.Holiday("h2", "2026-01-19", "MLK Day"));
        p.holidays.add(new PolicyConfig.Holiday("h3", "2026-05-25", "Memorial Day"));
        p.holidays.add(new PolicyConfig.Holiday("h4", "2026-07-03", "Independence Day (obs.)"));
        p.holidays.add(new PolicyConfig.Holiday("h5", "2026-09-07", "Labor Day"));
        p.holidays.add(new PolicyConfig.Holiday("h6", "2026-11-26", "Thanksgiving"));
        p.holidays.add(new PolicyConfig.Holiday("h7", "2026-11-27", "Day after Thanksgiving"));
        p.holidays.add(new PolicyConfig.Holiday("h8", "2026-12-24", "Christmas Eve"));
        p.holidays.add(new PolicyConfig.Holiday("h9", "2026-12-25", "Christmas Day"));
    }

    // ===================== people =====================

    private static void seedPeople(Workspace ws) {
        List<Employee> e = ws.employees;

        e.add(emp("alex.whitfield", "Alex", "Whitfield", "#7a6bb5", "VP, Operations", "Operations", "Executive",
                HR, null, "alex.whitfield@meridian.co", "+1 415 555 0114", "San Francisco, CA", "Hybrid", "Full-time",
                "2019-02-04", ACTIVE, 265000, "E1")
                .document("Employment agreement", "Contract", "2019-02-04"));

        e.add(emp("priya.nair", "Priya", "Nair", "#4a9d7a", "HR Business Partner", "People Operations", "Manager",
                HR, "alex.whitfield", "priya.nair@meridian.co", "+1 415 555 0132", "San Francisco, CA", "Hybrid", "Full-time",
                "2021-06-14", ACTIVE, 158000, "M3")
                .document("Offer letter", "Offer", "2021-05-20")
                .document("Employment agreement", "Contract", "2021-06-14"));

        e.add(emp("david.okonkwo", "David", "Okonkwo", "#c47f3f", "Engineering Manager", "Engineering", "Manager",
                MANAGER, "alex.whitfield", "david.okonkwo@meridian.co", "+1 512 555 0188", "Austin, TX", "Hybrid", "Full-time",
                "2020-09-01", ACTIVE, 192000, "M4")
                .document("Offer letter", "Offer", "2020-08-10")
                .document("Employment agreement", "Contract", "2020-09-01"));

        e.add(emp("marcus.reid", "Marcus", "Reid", "#6b7db5", "Senior Engineer", "Engineering", "Senior",
                EMPLOYEE, "david.okonkwo", "marcus.reid@meridian.co", "+1 512 555 0199", "Austin, TX", "Remote", "Full-time",
                "2022-01-17", ACTIVE, 148000, "IC4")
                .document("Offer letter", "Offer", "2021-12-15"));

        e.add(emp("aisha.khan", "Aisha", "Khan", "#4a9d9d", "Staff Engineer", "Engineering", "Staff",
                EMPLOYEE, "david.okonkwo", "aisha.khan@meridian.co", "+1 206 555 0143", "Seattle, WA", "Remote", "Full-time",
                "2020-11-30", ACTIVE, 176000, "IC5")
                .document("Offer letter", "Offer", "2020-11-02"));

        e.add(emp("tom.bradley", "Tom", "Bradley", "#6ba58f", "Engineer", "Engineering", "Mid",
                EMPLOYEE, "david.okonkwo", "tom.bradley@meridian.co", "+1 512 555 0177", "Austin, TX", "Hybrid", "Full-time",
                "2023-03-06", LEAVE, 122000, "IC3")
                .document("Offer letter", "Offer", "2023-02-10"));

        e.add(emp("james.okoro", "James", "Okoro", "#7a6bb5", "Engineer", "Engineering", "Mid",
                EMPLOYEE, "david.okonkwo", "james.okoro@meridian.co", "+1 617 555 0121", "Boston, MA", "Remote", "Full-time",
                "2025-08-22", ACTIVE, 128000, "IC3")
                .document("Offer letter", "Offer", "2022-07-29"));

        e.add(emp("nadia.rahman", "Nadia", "Rahman", "#9a6ab5", "Design Manager", "Design", "Manager",
                MANAGER, "alex.whitfield", "nadia.rahman@meridian.co", "+1 415 555 0166", "San Francisco, CA", "Hybrid", "Full-time",
                "2021-02-08", ACTIVE, 168000, "M3")
                .document("Offer letter", "Offer", "2021-01-18"));

        e.add(emp("sarah.chen", "Sarah", "Chen", "#3f7cc4", "Product Designer", "Design", "Mid",
                EMPLOYEE, "nadia.rahman", "sarah.chen@meridian.co", "+1 415 555 0155", "San Francisco, CA", "Hybrid", "Full-time",
                "2022-05-16", ACTIVE, 132000, "IC3")
                .document("Offer letter", "Offer", "2022-04-25"));

        e.add(emp("ken.ito", "Ken", "Ito", "#6ba58f", "Product Designer", "Design", "Senior",
                EMPLOYEE, "nadia.rahman", "ken.ito@meridian.co", "+1 415 555 0190", "San Francisco, CA", "On-site", "Full-time",
                "2021-10-04", ACTIVE, 141000, "IC4")
                .document("Offer letter", "Offer", "2021-09-13"));

        Employee anna = emp("anna.kim", "Anna", "Kim", "#b56b8f", "Designer", "Design", "Junior",
                EMPLOYEE, "nadia.rahman", "anna.kim@meridian.co", "+1 213 555 0138", "Los Angeles, CA", "Remote", "Full-time",
                "2026-03-08", LEAVE, 104000, "IC2");
        anna.initials = "AN";  // fixture override (avoids AK collision with Aisha Khan)
        anna.document("Offer letter", "Offer", "2023-12-11");
        e.add(anna);

        e.add(emp("elena.vasquez", "Elena", "Vasquez", "#b58f4a", "Revenue Manager", "Revenue", "Manager",
                MANAGER, "alex.whitfield", "elena.vasquez@meridian.co", "+1 305 555 0102", "Miami, FL", "Hybrid", "Full-time",
                "2020-04-20", ACTIVE, 164000, "M3")
                .document("Offer letter", "Offer", "2020-03-30"));

        e.add(emp("sofia.alvarez", "Sofia", "Alvarez", "#6ba58f", "Account Executive", "Revenue", "Senior",
                EMPLOYEE, "elena.vasquez", "sofia.alvarez@meridian.co", "+1 305 555 0119", "Miami, FL", "On-site", "Full-time",
                "2025-11-14", ACTIVE, 118000, "IC3")
                .document("Offer letter", "Offer", "2022-10-24"));

        Employee marco = emp("marco.rossi", "Marco", "Rossi", "#6b8fb5", "Operations Manager", "Operations", "Manager",
                MANAGER, "alex.whitfield", "marco.rossi@meridian.co", "+1 415 555 0175", "San Francisco, CA", "Hybrid", "Full-time",
                "2021-07-26", ACTIVE, 152000, "M3");
        marco.initials = "MO";  // fixture override
        marco.document("Offer letter", "Offer", "2021-07-05");
        e.add(marco);

        e.add(emp("julia.novak", "Julia", "Novak", "#b56b8f", "Program Manager", "Operations", "Senior",
                EMPLOYEE, "marco.rossi", "julia.novak@meridian.co", "+1 415 555 0183", "Denver, CO", "Remote", "Full-time",
                "2026-06-15", ONBOARDING, 126000, "IC3")
                .document("Offer letter", "Offer", "2023-05-15"));

        seedProfiles(ws);
        seedHistory(ws);
    }

    private static Employee emp(String id, String first, String last, String bg, String title, String dept,
                                String level, Role role, String managerId, String email, String phone, String location,
                                String workMode, String employmentType, String startDate, EmployeeStatus status,
                                Integer salary, String band) {
        return new Employee(id, first, last, bg, title, dept, level, role, managerId, email, phone, location,
                workMode, employmentType, startDate, status, salary, band);
    }

    // ----- rich profile seed for a few people (rest start sparse) -----

    private static void seedProfiles(Workspace ws) {
        Employee sarah = ws.employee("sarah.chen");
        sarah.legalName = "Sarah Anne Chen";
        sarah.preferredName = "Sarah";
        sarah.dob = "1993-04-12";
        sarah.pronouns = "she/her";
        sarah.personalEmail = "sarah.chen.sf@gmail.com";
        sarah.personalPhone = "+1 415 555 0155";
        sarah.address = new Employee.Address("482 Valencia St, Apt 3", "San Francisco", "CA", "94103");
        sarah.emergencyContacts.add(new EmergencyContact("Daniel Chen", "Spouse", "+1 415 555 0182"));
        sarah.dependents.add(new Dependent("Mia Chen", "Child", "2020-08-03"));
        sarah.skills.addAll(List.of(new Skill("Product design", "Expert"), new Skill("Prototyping", "Advanced"),
                new Skill("User research", "Intermediate")));
        sarah.certifications.add(new Certification("NN/g UX Certification", "Nielsen Norman Group", "2021-06-01"));
        sarah.assets.addAll(List.of(new Asset("MacBook Pro 16\"", "MRD-3391", "2022-05-16"),
                new Asset("Studio Display", "MRD-3392", "2022-05-16")));
        sarah.taxIds = new Employee.TaxIds("4821", "");
        sarah.bank = new Employee.Bank("First Republic", "Sarah A Chen", "6642", "0114");

        Employee david = ws.employee("david.okonkwo");
        david.legalName = "David Chukwuemeka Okonkwo";
        david.preferredName = "David";
        david.dob = "1987-11-02";
        david.gender = "Male";
        david.pronouns = "he/him";
        david.personalEmail = "d.okonkwo@gmail.com";
        david.personalPhone = "+1 512 555 0188";
        david.address = new Employee.Address("1204 E 6th St", "Austin", "TX", "78702");
        david.emergencyContacts.add(new EmergencyContact("Grace Okonkwo", "Spouse", "+1 512 555 0190"));
        david.dependents.addAll(List.of(new Dependent("Ada Okonkwo", "Child", "2016-02-14"),
                new Dependent("Emeka Okonkwo", "Child", "2018-07-21")));
        david.skills.addAll(List.of(new Skill("Distributed systems", "Expert"), new Skill("People management", "Advanced")));
        david.certifications.add(new Certification("AWS Solutions Architect", "Amazon", "2020-03-01"));
        david.assets.add(new Asset("MacBook Pro 16\"", "MRD-2210", "2020-09-01"));
        david.taxIds = new Employee.TaxIds("7733", "");
        david.bank = new Employee.Bank("Chase", "David C Okonkwo", "1180", "0021");

        Employee priya = ws.employee("priya.nair");
        priya.legalName = "Priya Nair";
        priya.preferredName = "Priya";
        priya.dob = "1990-06-25";
        priya.gender = "Female";
        priya.pronouns = "she/her";
        priya.personalEmail = "priya.nair.hr@gmail.com";
        priya.personalPhone = "+1 415 555 0132";
        priya.address = new Employee.Address("77 Dolores St", "San Francisco", "CA", "94110");
        priya.emergencyContacts.add(new EmergencyContact("Arjun Nair", "Spouse", "+1 415 555 0140"));
        priya.skills.addAll(List.of(new Skill("Employee relations", "Expert"), new Skill("Comp & benefits", "Advanced")));
        priya.certifications.add(new Certification("SHRM-SCP", "SHRM", "2019-09-01"));
        priya.assets.add(new Asset("MacBook Air", "MRD-2601", "2021-06-14"));
        priya.taxIds = new Employee.TaxIds("3092", "");
        priya.bank = new Employee.Bank("Wells Fargo", "Priya Nair", "5521", "0098");

        Employee marcus = ws.employee("marcus.reid");
        marcus.preferredName = "Marcus";
        marcus.dob = "1991-01-18";
        marcus.pronouns = "he/him";
        marcus.personalPhone = "+1 512 555 0199";
        marcus.emergencyContacts.add(new EmergencyContact("Tara Reid", "Partner", "+1 512 555 0201"));
        marcus.skills.add(new Skill("Backend", "Advanced"));
        marcus.assets.add(new Asset("MacBook Pro 14\"", "MRD-3050", "2022-01-17"));

        Employee nadia = ws.employee("nadia.rahman");
        nadia.legalName = "Nadia Rahman";
        nadia.preferredName = "Nadia";
        nadia.dob = "1988-09-09";
        nadia.gender = "Female";
        nadia.pronouns = "she/her";
        nadia.personalPhone = "+1 415 555 0166";
        nadia.emergencyContacts.add(new EmergencyContact("Sam Rahman", "Sibling", "+1 415 555 0170"));
        nadia.skills.add(new Skill("Design leadership", "Expert"));
        nadia.assets.add(new Asset("MacBook Pro 16\"", "MRD-2705", "2021-02-08"));
    }

    // ----- job history: a "Joined" event for everyone, plus a couple of seeded moves -----

    private static void seedHistory(Workspace ws) {
        for (Employee e : ws.employees) {
            e.history.add(new HistoryEvent("ev-join-" + e.id, "join", "Joined Meridian",
                    e.title + " · " + e.dept, e.startDate, epoch(e.startDate), "System"));
        }
        Employee david = ws.employee("david.okonkwo");
        david.history.add(new HistoryEvent("ev-d1", "promotion", "Promoted to Engineering Manager",
                "Senior Engineer → Engineering Manager", "2022-04-01", epoch("2022-04-01"), "Alex Whitfield"));

        Employee sarah = ws.employee("sarah.chen");
        sarah.history.add(new HistoryEvent("ev-s1", "comp", "Compensation review",
                "$124,000 → $132,000", "2024-01-15", epoch("2024-01-15"), "Nadia Rahman"));
    }

    private static long epoch(String isoDate) {
        return LocalDate.parse(isoDate).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    // ===================== leave requests =====================

    private static void seedLeave(Workspace ws) {
        List<LeaveRequest> L = ws.leaveRequests;

        // ----- Sarah Chen's own history (her My Leave) -----
        LeaveRequest r = lr("R-1042", "sarah.chen", "Sarah Chen", "SC", "#3f7cc4", "Product Designer",
                "annual", "2026-09-29", "2026-10-03", 5, "M. Reid", "Family trip booked over the long weekend.",
                "david.okonkwo", "david.okonkwo", false, "pending", "Jun 28");
        ev(r, "submitted", "sarah.chen", "Sarah Chen", "Family trip booked over the long weekend.", "2026-06-28", 9);
        ev(r, "routed", "system", "System", "Within manager authority — routed to D. Okonkwo.", "2026-06-28", 9);
        L.add(r);

        r = lr("R-0988", "sarah.chen", "Sarah Chen", "SC", "#3f7cc4", "Product Designer",
                "annual", "2026-08-11", "2026-08-15", 5, "M. Reid", null,
                "david.okonkwo", "david.okonkwo", false, "approved", "Jun 10");
        ev(r, "submitted", "sarah.chen", "Sarah Chen", "", "2026-06-10", 11);
        ev(r, "routed", "system", "System", "Routed to D. Okonkwo.", "2026-06-10", 11);
        ev(r, "approved", "david.okonkwo", "D. Okonkwo", "Enjoy — coverage looks fine.", "2026-06-12", 14);
        L.add(r);

        r = lr("R-0975", "sarah.chen", "Sarah Chen", "SC", "#3f7cc4", "Product Designer",
                "sick", "2026-06-24", "2026-06-24", 1, "—", null,
                "david.okonkwo", "david.okonkwo", false, "posted", "Jun 24");
        ev(r, "submitted", "sarah.chen", "Sarah Chen", "Woke up unwell.", "2026-06-24", 8);
        ev(r, "approved", "david.okonkwo", "D. Okonkwo", "Auto-approved — under 2 days.", "2026-06-24", 8);
        ev(r, "posted", "system", "System", "Posted to team calendar & payroll.", "2026-06-25", 6);
        L.add(r);

        r = lr("R-0900", "sarah.chen", "Sarah Chen", "SC", "#3f7cc4", "Product Designer",
                "personal", "2026-05-02", "2026-05-02", 1, "E. Vasquez", null,
                "david.okonkwo", "david.okonkwo", false, "rejected", "Apr 28");
        ev(r, "submitted", "sarah.chen", "Sarah Chen", "", "2026-04-28", 15);
        ev(r, "routed", "system", "System", "Routed to D. Okonkwo.", "2026-04-28", 15);
        ev(r, "rejected", "david.okonkwo", "D. Okonkwo", "Design review is locked for that Friday — can we move it a week?", "2026-04-30", 10);
        L.add(r);

        r = lr("R-0854", "sarah.chen", "Sarah Chen", "SC", "#3f7cc4", "Product Designer",
                "annual", "2026-03-17", "2026-03-18", 2, "M. Reid", null,
                "david.okonkwo", "david.okonkwo", false, "posted", "Mar 1");
        ev(r, "submitted", "sarah.chen", "Sarah Chen", "", "2026-03-01", 9);
        ev(r, "routed", "system", "System", "Routed to D. Okonkwo.", "2026-03-01", 9);
        ev(r, "approved", "david.okonkwo", "D. Okonkwo", "", "2026-03-03", 13);
        ev(r, "posted", "system", "System", "Posted to team calendar & payroll.", "2026-03-19", 6);
        L.add(r);

        // ----- David's approval queue — within his authority, pending -----
        r = lr("R-1050", "marcus.reid", "Marcus Reid", "MR", "#6b7db5", "Senior Engineer",
                "annual", "2026-08-24", "2026-08-28", 5, "T. Bradley", "Pre-booked holiday.",
                "david.okonkwo", "david.okonkwo", false, "pending", "Jun 30");
        ev(r, "submitted", "marcus.reid", "Marcus Reid", "Pre-booked holiday.", "2026-06-30", 16);
        ev(r, "routed", "system", "System", "Routed to D. Okonkwo.", "2026-06-30", 16);
        L.add(r);

        r = lr("R-1051", "tom.bradley", "Tom Bradley", "TB", "#6ba58f", "Engineer",
                "sick", "2026-07-08", "2026-07-14", 5, "M. Reid", "Minor surgery — recovery week.",
                "david.okonkwo", "david.okonkwo", false, "pending", "Jul 1");
        r.flag = "certificate pending";
        ev(r, "submitted", "tom.bradley", "Tom Bradley", "Minor surgery — recovery week.", "2026-07-01", 7);
        ev(r, "routed", "system", "System", "Routed to D. Okonkwo. Flagged: certificate pending.", "2026-07-01", 7);
        L.add(r);

        r = lr("R-1052", "elena.vasquez", "Elena Vasquez", "EV", "#c07f4f", "Revenue Manager",
                "annual", "2026-09-01", "2026-09-12", 8, "M. Reid", null,
                "david.okonkwo", "david.okonkwo", false, "pending", "Jun 26");
        ev(r, "submitted", "elena.vasquez", "Elena Vasquez", "", "2026-06-26", 12);
        ev(r, "routed", "system", "System", "Routed to D. Okonkwo.", "2026-06-26", 12);
        L.add(r);

        // ----- Over ceiling → escalated to HR (Priya). Read-only in David's queue. -----
        r = lr("R-1048", "anna.kim", "Anna Kim", "AN", "#b56b8f", "Designer",
                "parental", "2026-11-03", "2026-11-28", 20, "E. Vasquez", "Parental leave — expecting in November.",
                "david.okonkwo", "priya.nair", true, "escalated", "Jun 15");
        r.ceilingNote = "Extended parental leave exceeds a manager's 10-day authority ceiling.";
        ev(r, "submitted", "anna.kim", "Anna Kim", "Parental leave — expecting in November.", "2026-06-15", 10);
        ev(r, "escalated", "system", "System", "Over the 10-day manager ceiling — escalated to HR (P. Nair).", "2026-06-15", 10);
        L.add(r);

        r = lr("R-1049", "james.okoro", "James Okoro", "JO", "#7a6bb5", "Engineer",
                "unpaid", "2026-10-06", "2026-10-24", 15, "T. Bradley", "Personal sabbatical.",
                "david.okonkwo", "priya.nair", true, "escalated", "Jun 22");
        r.ceilingNote = "Extended unpaid leave over 10 days requires higher approval.";
        ev(r, "submitted", "james.okoro", "James Okoro", "Personal sabbatical.", "2026-06-22", 9);
        ev(r, "escalated", "system", "System", "Over the 10-day manager ceiling — escalated to HR (P. Nair).", "2026-06-22", 9);
        L.add(r);
    }

    private static LeaveRequest lr(String id, String empId, String name, String init, String bg, String role,
                                   String type, String start, String end, double days, String cover, String reason,
                                   String managerId, String approverId, boolean overCeiling, String status, String submitted) {
        LeaveRequest r = new LeaveRequest();
        r.id = id;
        r.empId = empId;
        r.empName = name;
        r.empInitials = init;
        r.empBg = bg;
        r.empRole = role;
        r.type = type;
        r.startDate = start;
        r.endDate = end;
        r.days = days;
        r.cover = cover;
        r.reason = reason;
        r.managerId = managerId;
        r.approverId = approverId;
        r.overCeiling = overCeiling;
        r.status = status;
        r.submitted = submitted;
        r.createdAt = LeaveMeta.epochAt(start, 9);
        return r;
    }

    private static void ev(LeaveRequest r, String kind, String actor, String actorName, String note, String date, int hour) {
        r.events.add(new LeaveRequest.Event(kind, actor, actorName, note,
                LeaveMeta.epochAt(date, hour), LeaveMeta.stampLabel(date, hour)));
    }

    // ===================== timesheets =====================

    private static void seedTime(Workspace ws) {
        String thisWeek = mondayOf(LocalDate.now());
        // Sarah: three approved prior weeks so history isn't empty.
        ws.timesheets.add(ts("sarah.chen", weekBefore(thisWeek, 1), new double[]{8, 8, 7.5, 8, 8, 0, 0}, "approved", "nadia.rahman"));
        ws.timesheets.add(ts("sarah.chen", weekBefore(thisWeek, 2), new double[]{8, 8, 8, 8, 6, 0, 0}, "approved", "nadia.rahman"));
        ws.timesheets.add(ts("sarah.chen", weekBefore(thisWeek, 3), new double[]{8, 8, 8, 8, 8, 0, 0}, "approved", "nadia.rahman"));
        // Teammates' submitted-but-unapproved weeks so a manager has something to action.
        ws.timesheets.add(ts("marcus.reid", weekBefore(thisWeek, 1), new double[]{8, 8, 8, 8, 9, 0, 0}, "submitted", null));
        ws.timesheets.add(ts("ken.ito", weekBefore(thisWeek, 1), new double[]{8, 7, 8, 8, 8, 0, 0}, "submitted", null));
    }

    private static Timesheet ts(String empId, String weekStart, double[] hours, String status, String approvedBy) {
        Timesheet t = new Timesheet(empId, weekStart);
        for (int i = 0; i < Timesheet.DAY_IDS.length; i++) {
            t.days.put(Timesheet.DAY_IDS[i], hours[i]);
        }
        t.status = status;
        t.approvedBy = approvedBy;
        return t;
    }

    private static String mondayOf(LocalDate d) {
        return d.minusDays((d.getDayOfWeek().getValue() + 6) % 7).toString();
    }

    private static String weekBefore(String mondayIso, int n) {
        return LocalDate.parse(mondayIso).minusWeeks(n).toString();
    }

    // ===================== onboarding =====================

    private static void seedOnboarding(Workspace ws) {
        ws.onboardingTemplates.add(designTpl());
        ws.onboardingTemplates.add(engTpl());
        ws.onboardingTemplates.add(salesTpl());
        ws.onboardingTemplates.add(generalTpl());

        // Anna Kim — design hire, mid-flight (badge blocked on I-9, training waiting).
        ws.onboardingCases.add(new OnboardingCase("onb-anna", "Anna Kim", "AK", "#b56b8f", "design",
                "Product Designer", "Design", "akim@meridian.co", "2026-07-13", "David Okonkwo",
                "tpl-design", daysAgo(6))
                .done("identity", "Dioschub", daysAgo(5), false)
                .done("email", "Dioschub", daysAgo(5), false)
                .done("hardware", "IT · S. Patel", daysAgo(3), false));

        // Ravi Menon — engineering hire, further along (badge in progress, yubikey waiting).
        ws.onboardingCases.add(new OnboardingCase("onb-ravi", "Ravi Menon", "RM", "#6b7db5", "eng",
                "Backend Engineer", "Engineering", "rmenon@meridian.co", "2026-07-13", "David Okonkwo",
                "tpl-eng", daysAgo(6))
                .done("identity", "Dioschub", daysAgo(5), false)
                .done("email", "Dioschub", daysAgo(5), false)
                .done("hardware", "IT · S. Patel", daysAgo(4), false)
                .done("payroll", "Payroll", daysAgo(2), false)
                .done("source", "IT · S. Patel", daysAgo(2), false));

        // Sofia Alvarez — sales hire, fully complete → ready to convert to directory.
        ws.onboardingCases.add(new OnboardingCase("onb-sofia", "Sofia Alvarez", "SA", "#6ba58f", "sales",
                "Account Executive", "Revenue", "salvarez@meridian.co", "2026-06-29", "Elena Vasquez",
                "tpl-sales", daysAgo(20))
                .done("identity", "Dioschub", daysAgo(19), false)
                .done("email", "Dioschub", daysAgo(19), false)
                .done("hardware", "IT", daysAgo(17), false)
                .done("payroll", "Payroll", daysAgo(16), false)
                .done("badge", "Facilities", daysAgo(15), true)
                .done("training", "Dioschub", daysAgo(14), false)
                .done("crm", "E. Vasquez", daysAgo(13), false));
    }

    /** The shared 6-step provisioning spine every schema starts from. */
    private static OnboardingTemplate spine(String id, String name, String role, String dept, String desc, int updDays) {
        OnboardingTemplate t = new OnboardingTemplate(id, name, role, dept, desc);
        t.updatedAt = daysAgo(updDays);
        t.step(new Step(1, "identity", "Create identity & directory account", "azure_ad", "IT").auto().due(-2));
        t.step(new Step(2, "email", "Provision email & calendar", "google_ws", "IT").depends("identity").auto().due(-2));
        t.step(new Step(3, "hardware", "Order hardware", "servicenow", "IT").depends("identity").due(-5));
        t.step(new Step(4, "payroll", "Enroll in payroll & benefits", "workday", "Payroll").depends("identity").due(0));
        t.step(new Step(5, "badge", "Issue building badge & access", "genetec", "Facilities").depends("identity").doc("Signed I-9").due(0));
        t.step(new Step(6, "training", "Assign compliance training", "docebo", "People Ops").depends("badge").auto().due(1));
        return t;
    }

    private static OnboardingTemplate designTpl() {
        OnboardingTemplate t = spine("tpl-design", "Design / Product hire", "design", "Design",
                "Standard schema for designers and PMs.", 9);
        t.step(new Step(7, "tools", "Add to design tooling (Figma, Slack)", "slack", "Hiring Manager").depends("email").due(1));
        return t;
    }

    private static OnboardingTemplate engTpl() {
        OnboardingTemplate t = spine("tpl-eng", "Engineering hire", "eng", "Engineering",
                "Adds source access + hardware security key for engineers.", 4);
        t.step(new Step(7, "source", "Provision source & CI access", "onepass", "IT").depends("identity").due(0));
        t.step(new Step(8, "yubikey", "Issue hardware security key", "onepass", "Security").depends("source").doc("Key acknowledgement").due(0));
        return t;
    }

    private static OnboardingTemplate salesTpl() {
        OnboardingTemplate t = spine("tpl-sales", "Sales / GTM hire", "sales", "Revenue",
                "Adds CRM seat and territory assignment.", 2);
        t.step(new Step(7, "crm", "Assign CRM seat & territory", "slack", "Hiring Manager").depends("email").due(1));
        return t;
    }

    private static OnboardingTemplate generalTpl() {
        return spine("tpl-general", "General employee", "general", "Operations",
                "Baseline schema for any role without a specialised template.", 20);
    }

    private static long daysAgo(int n) {
        return System.currentTimeMillis() - 86400000L * n;
    }

    // ===================== offboarding =====================

    private static void seedOffboarding(Workspace ws) {
        // Ken Ito — a resignation mid-checklist, so the board isn't empty and progress shows.
        Employee ken = ws.employee("ken.ito");
        OffboardingCase c = new OffboardingCase();
        c.id = "off-seed-ken";
        c.empId = "ken.ito";
        c.empName = ken != null ? ken.fullName() : "Ken Ito";
        c.dept = ken != null ? ken.dept : "Design";
        c.title = ken != null ? ken.title : "Product Designer";
        c.type = "resignation";
        c.lastDay = LocalDate.now().plusWeeks(2).toString();
        c.reason = "Moving to a new city; gave four weeks' notice. Amicable departure.";
        c.initiatedBy = "priya.nair";
        c.initiatedAt = daysAgo(3);
        c.checklist.addAll(OffboardingCase.defaultChecklist());
        markDone(c, "notice");
        markDone(c, "knowledge");
        markDone(c, "exit_interview");
        ws.offboardingCases.add(c);
    }

    private static void markDone(OffboardingCase c, String taskId) {
        OffboardingCase.Task t = c.task(taskId);
        if (t != null) t.done = true;
    }

    // ===================== job changes =====================

    private static void seedJobChanges(Workspace ws) {
        String today = LocalDate.now().toString();

        // Pending: Sarah Chen promotion to Senior, effective in ~3 weeks.
        JobChange p = jc(ws, "sarah.chen", "Sarah Chen", "promotion",
                LocalDate.now().plusDays(21).toString(), "nadia.rahman", "Nadia Rahman", "pending",
                "Consistently strong delivery; ready for the senior track.", daysAgo(2));
        p.change("title", "Product Designer", "Senior Product Designer");
        p.change("level", "Mid", "Senior");
        p.change("salary", "132000", "148000");
        p.change("band", "IC3", "IC4");
        ws.jobChanges.add(p);

        // Scheduled: Marcus Reid comp adjustment, already approved, effective in ~10 days.
        JobChange s = jc(ws, "marcus.reid", "Marcus Reid", "comp",
                LocalDate.now().plusDays(10).toString(), "david.okonkwo", "David Okonkwo", "scheduled",
                "Market adjustment to retain a key senior engineer.", daysAgo(4));
        s.change("salary", "148000", "158000");
        s.change("band", "IC4", "IC5");
        s.decidedBy = "priya.nair";
        s.decidedByName = "Priya Nair";
        s.decidedAt = daysAgo(1);
        ws.jobChanges.add(s);

        // Applied: David Okonkwo's historical promotion to Engineering Manager (matches his record).
        JobChange a = jc(ws, "david.okonkwo", "David Okonkwo", "promotion",
                LocalDate.now().minusMonths(15).toString(), "alex.whitfield", "Alex Whitfield", "applied",
                "Stepping up to lead the platform team.", daysAgo(470));
        a.change("title", "Senior Engineer", "Engineering Manager");
        a.change("level", "Senior", "Manager");
        a.change("salary", "170000", "192000");
        a.decidedBy = "alex.whitfield";
        a.decidedByName = "Alex Whitfield";
        a.decidedAt = daysAgo(465);
        a.appliedAt = daysAgo(455);
        ws.jobChanges.add(a);
    }

    private static JobChange jc(Workspace ws, String empId, String empName, String type, String eff,
                                String byId, String byName, String status, String reason, long createdAt) {
        JobChange r = new JobChange();
        r.id = "jc-seed-" + empId + "-" + type;
        r.empId = empId;
        r.empName = empName;
        r.type = type;
        r.effectiveDate = eff;
        r.reason = reason;
        r.requestedBy = byId;
        r.requestedByName = byName;
        r.status = status;
        r.createdAt = createdAt;
        return r;
    }

    // ===================== performance =====================

    private static void seedPerformance(Workspace ws) {
        List<String> allActive = new java.util.ArrayList<>();
        List<String> engActive = new java.util.ArrayList<>();
        for (Employee e : ws.employees) {
            if (e.status == EmployeeStatus.INACTIVE) continue;
            allActive.add(e.id);
            if ("Engineering".equals(e.dept)) engActive.add(e.id);
        }

        ReviewCycle h1 = cycle("cyc-h1-2026", "H1 2026 Review", "half", "active",
                "2026-06-01", "2026-06-19", "2026-06-30", "2026-07-10", daysAgo(40));
        stdComps(h1);
        h1.participants.addAll(allActive);

        ReviewCycle h2 = cycle("cyc-h2-2025", "H2 2025 Review", "half", "closed",
                "2025-12-01", "2025-12-15", "2025-12-22", "2026-01-08", daysAgo(220));
        stdComps(h2);
        h2.participants.addAll(allActive);

        ReviewCycle q3 = cycle("cyc-q3-2026", "Q3 2026 Check-in", "quarter", "draft",
                "2026-09-01", "2026-09-12", "2026-09-20", "2026-09-30", daysAgo(3));
        q3.comp("exec", 40).comp("collab", 30).comp("owner", 30);
        q3.participants.addAll(engActive);

        ws.reviewCycles.add(h1);
        ws.reviewCycles.add(h2);
        ws.reviewCycles.add(q3);

        // Materialize rich review instances for the two non-draft cycles.
        for (ReviewCycle cy : List.of(h1, h2)) {
            for (String eid : cy.participants) {
                Employee e = ws.employee(eid);
                if (e != null) ws.reviews.add(buildReview(cy, e));
            }
        }
    }

    private static ReviewCycle cycle(String id, String name, String type, String status,
                                     String start, String selfDue, String mgrDue, String cal, long createdAt) {
        ReviewCycle c = new ReviewCycle();
        c.id = id;
        c.name = name;
        c.type = type;
        c.status = status;
        c.startDate = start;
        c.selfDue = selfDue;
        c.mgrDue = mgrDue;
        c.calibrationDate = cal;
        c.scaleMax = 5;
        c.createdAt = createdAt;
        c.createdBy = "Priya Nair";
        return c;
    }

    private static void stdComps(ReviewCycle c) {
        c.comp("exec", 30).comp("craft", 20).comp("collab", 20).comp("owner", 15).comp("comm", 15);
    }

    private static final String[] SELF_NOTES = {
            "Delivered my committed roadmap this half and picked up two stretch projects. I'd rate my cross-team collaboration as a clear strength.",
            "Focused on raising the quality bar — fewer regressions, cleaner handoffs. Stakeholder communication is where I invested most.",
            "Led a major initiative end-to-end and mentored a junior. I see myself exceeding expectations on ownership this cycle.",
            "Steady delivery against goals with a few standout wins. Looking to grow into more strategic, cross-org work next half."};
    private static final String[] MGR_NOTES = {
            "Strong technical execution — among the most dependable on the team. Growth area is proactively surfacing risk earlier.",
            "Excellent craft and consistently high quality. Collaboration slipped on one cross-team effort; addressed and improving.",
            "Owns outcomes and raises the bar for peers. Ready for more scope; a clear path to the next level.",
            "Meets expectations with a strong trajectory. Communication with stakeholders is the biggest lever for the next half."};

    private static final java.util.Map<String, String> PHASE_OVERRIDE = java.util.Map.of(
            "sarah.chen", "awaiting_self",
            "marcus.reid", "in_calibration",
            "james.okoro", "awaiting_manager",
            "tom.bradley", "awaiting_manager",
            "aisha.khan", "in_calibration",
            "ken.ito", "committed");

    /** Deterministic (stable) rich review instance, ported from the fixture's buildReview. */
    private static Review buildReview(ReviewCycle cycle, Employee emp) {
        int[] rng = {hashStr(cycle.id + ":" + emp.id)};
        double base = 2.7 + rnd(rng) * 1.9;
        java.util.Map<String, Integer> self = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> mgr = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> cal = new java.util.LinkedHashMap<>();
        for (ReviewCycle.CompWeight c : cycle.competencies) {
            int s = clamp5(base + 0.45 + (rnd(rng) - 0.35) * 1.4);
            int m = clamp5(base + (rnd(rng) - 0.5) * 1.2);
            self.put(c.id, s);
            mgr.put(c.id, m);
            cal.put(c.id, m);
        }
        if ("marcus.reid".equals(emp.id)) {
            java.util.Map<String, int[]> f = java.util.Map.of(
                    "exec", new int[]{5, 5, 5}, "craft", new int[]{4, 4, 4}, "collab", new int[]{5, 3, 3},
                    "owner", new int[]{4, 4, 4}, "comm", new int[]{5, 4, 4});
            for (ReviewCycle.CompWeight c : cycle.competencies) {
                int[] v = f.get(c.id);
                if (v != null) {
                    self.put(c.id, v[0]);
                    mgr.put(c.id, v[1]);
                    cal.put(c.id, v[2]);
                }
            }
        }

        String phase = PHASE_OVERRIDE.get(emp.id);
        if (phase == null) {
            if ("closed".equals(cycle.status)) phase = "committed";
            else {
                int r = (int) Math.floor(rnd(rng) * 10);
                phase = r < 5 ? "committed" : r < 7 ? "in_calibration" : r < 9 ? "awaiting_manager" : "awaiting_self";
            }
        }

        int ni = Math.floorMod(hashStr(emp.id), 4);
        Review review = new Review(cycle.id, emp.id, emp.managerId);
        boolean selfDone = !"awaiting_self".equals(phase);
        boolean mgrDone = "committed".equals(phase) || "in_calibration".equals(phase);
        boolean committed = "committed".equals(phase);

        if (selfDone) {
            review.self.scores.putAll(self);
            review.self.narrative = SELF_NOTES[ni];
            review.self.submittedAt = daysAgo(18);
        }
        if (mgrDone) {
            review.mgr.scores.putAll(mgr);
            review.mgr.narrative = MGR_NOTES[(ni + 1) % 4];
            review.mgr.submittedAt = daysAgo(9);
            review.cal.scores.putAll(cal);
            review.cal.started = true;
            review.cal.committed = committed;
            review.cal.committedAt = committed ? daysAgo(3) : null;
        }
        return review;
    }

    // Deterministic RNG (mulberry32) + FNV-1a hash, ported to match the fixture exactly.
    private static double rnd(int[] state) {
        state[0] = state[0] + 0x6D2B79F5;
        int t = (state[0] ^ (state[0] >>> 15)) * (1 | state[0]);
        t = (t + (t ^ (t >>> 7)) * (61 | t)) ^ t;
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }

    private static int hashStr(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 16777619;
        }
        return h;
    }

    private static int clamp5(double n) {
        return Math.max(1, Math.min(5, (int) Math.round(n)));
    }

    // ===================== recruitment =====================

    private static void seedRecruitment(Workspace ws) {
        Requisition r2041 = req("REQ-2041", "Senior Backend Engineer", "Engineering", "Senior", "Remote · EU", 1,
                "open", "david.okonkwo", "priya.nair", daysAgo(32), daysAgo(30), null, daysAgo(34));
        r2041.scorecard.addAll(List.of("coding", "system", "problem", "comm", "ownership"));
        r2041.round("interview", "david.okonkwo", "marcus.reid").round("onsite", "marcus.reid", "aisha.khan", "david.okonkwo");
        ws.requisitions.add(r2041);

        Requisition r2044 = req("REQ-2044", "Product Designer", "Design", "Mid", "London, UK", 1,
                "open", "nadia.rahman", "priya.nair", daysAgo(19), daysAgo(17), null, daysAgo(20));
        r2044.scorecard.addAll(List.of("craft", "product", "comm", "collab", "culture"));
        r2044.round("interview", "nadia.rahman", "sarah.chen").round("onsite", "nadia.rahman", "ken.ito", "sarah.chen");
        ws.requisitions.add(r2044);

        Requisition r2050 = req("REQ-2050", "Account Executive", "Revenue", "Mid", "New York, US", 2,
                "open", "elena.vasquez", "priya.nair", daysAgo(11), daysAgo(9), null, daysAgo(12));
        r2050.scorecard.addAll(List.of("sales", "comm", "domain", "ownership", "culture"));
        r2050.round("interview", "elena.vasquez").round("onsite", "elena.vasquez", "sofia.alvarez");
        ws.requisitions.add(r2050);

        Requisition r2038 = req("REQ-2038", "Staff Engineer", "Engineering", "Staff", "Remote · US", 1,
                "filled", "david.okonkwo", "priya.nair", daysAgo(92), daysAgo(90), daysAgo(34), daysAgo(95));
        r2038.scorecard.addAll(List.of("coding", "system", "problem", "ownership", "culture"));
        r2038.round("interview", "david.okonkwo", "aisha.khan").round("onsite", "aisha.khan", "marcus.reid", "david.okonkwo");
        ws.requisitions.add(r2038);

        Requisition r2052 = req("REQ-2052", "Operations Analyst", "Operations", "Junior", "Berlin, DE", 1,
                "pending_approval", "marco.rossi", "priya.nair", daysAgo(3), null, null, null);
        r2052.approvalStatus = "pending";
        r2052.scorecard.addAll(List.of("problem", "comm", "domain", "ownership"));
        r2052.round("interview", "marco.rossi", "julia.novak").round("onsite", "marco.rossi", "julia.novak");
        ws.requisitions.add(r2052);

        candSpec(ws, r2041,
                c("Wei Zhang", "Staff Engineer, Stripe", "8 yrs · Go, Kafka", "Referral", "onsite"),
                c("Daniel Osei", "Senior Eng, Monzo", "7 yrs · Go, k8s", "LinkedIn", "onsite"),
                c("Priyanka Rao", "Tech Lead, Uber", "9 yrs · Go, gRPC", "CV corpus", "interview"),
                c("Lucas Meyer", "Backend Eng, SAP", "6 yrs · Java→Go", "Job board", "interview"),
                c("Aisha Bello", "Engineer, Andela", "5 yrs · Go, AWS", "CV corpus", "screen"),
                c("Tomás Rivera", "Principal, Cloudflare", "10 yrs · Go, Rust", "Referral", "offer"),
                c("Nina Petrova", "Senior Eng, Bolt", "6 yrs · Go, PG", "Inbound", "applied"),
                c("Kwame Mensah", "Eng, Paystack", "4 yrs · Go", "CV corpus", "applied"),
                c("Sara Lindqvist", "Backend Eng, Klarna", "7 yrs · Go, K8s", "LinkedIn", "rejected"));

        candSpec(ws, r2044,
                c("Mara Devlin", "Product Designer, Figma", "6 yrs", "Referral", "onsite"),
                c("Ibrahim Sy", "Designer, Deezer", "5 yrs", "CV corpus", "interview"),
                c("Chloe Park", "Sr Designer, Revolut", "7 yrs", "LinkedIn", "interview"),
                c("Ravi Anand", "Product Designer, N26", "4 yrs", "Job board", "screen"),
                c("Elsa Berg", "UX Designer, Spotify", "5 yrs", "Inbound", "applied"),
                c("Marcus Webb", "Designer, Wise", "3 yrs", "CV corpus", "rejected"));

        candSpec(ws, r2050,
                c("Jordan Blake", "AE, Salesforce", "5 yrs · SaaS", "Referral", "onsite"),
                c("Fatima Nasser", "AE, HubSpot", "4 yrs · SaaS", "LinkedIn", "interview"),
                c("Diego Torres", "Sr AE, Datadog", "6 yrs", "Agency", "interview"),
                c("Hannah Cole", "AE, Notion", "3 yrs", "Inbound", "screen"),
                c("Sam Whitfield", "SDR→AE, Gong", "3 yrs", "Job board", "applied"),
                c("Léa Dubois", "AE, Qonto", "5 yrs", "CV corpus", "applied"));

        candSpec(ws, r2038,
                c("Grace Okoro", "Staff Eng, Shopify", "11 yrs", "Referral", "hired"),
                c("Victor Lang", "Principal, Elastic", "12 yrs", "LinkedIn", "rejected"),
                c("Mei Tan", "Staff Eng, GitLab", "10 yrs", "CV corpus", "rejected"));
    }

    private static Requisition req(String id, String title, String dept, String level, String loc, int hc,
                                   String status, String owner, String recruiter,
                                   long createdAt, Long openedAt, Long closedAt, Long approvalAt) {
        Requisition r = new Requisition();
        r.id = id;
        r.title = title;
        r.dept = dept;
        r.level = level;
        r.location = loc;
        r.headcount = hc;
        r.status = status;
        r.ownerId = owner;
        r.recruiterId = recruiter;
        r.approverId = "alex.whitfield";
        r.approvalStatus = approvalAt != null ? "approved" : "none";
        r.approvalAt = approvalAt;
        r.createdAt = createdAt;
        r.openedAt = openedAt;
        r.closedAt = closedAt;
        return r;
    }

    private record CandSpec(String name, String role, String exp, String source, String stage) {
    }

    private static CandSpec c(String name, String role, String exp, String source, String stage) {
        return new CandSpec(name, role, exp, source, stage);
    }

    private static void candSpec(Workspace ws, Requisition req, CandSpec... specs) {
        for (CandSpec s : specs) {
            ws.candidates.add(genCandidate(req, s));
        }
    }

    /** Deterministic candidate + seeded scorecards/offer, ported from the fixture's genCandidate. */
    private static Candidate genCandidate(Requisition req, CandSpec spec) {
        int[] rng = {hashStr(req.id + ":" + spec.name())};
        double strength = 2.9 + rnd(rng) * 1.7;
        int fit = (int) Math.round(58 + strength * 8 + rnd(rng) * 6);
        String slug = spec.name().toLowerCase().replaceAll("[^a-z]+", ".");
        boolean rejected = "rejected".equals(spec.stage());

        Candidate cand = new Candidate();
        cand.id = req.id + "-" + slug;
        cand.reqId = req.id;
        cand.name = spec.name();
        cand.initials = candInitials(spec.name());
        cand.bg = CAND_AVATARS[Math.floorMod(hashStr(spec.name()), CAND_AVATARS.length)];
        cand.currentRole = spec.role();
        cand.exp = spec.exp();
        cand.source = spec.source();
        cand.fit = fit;
        cand.stage = spec.stage();
        cand.appliedAt = daysAgo(5 + (int) Math.floor(rnd(rng) * 25));
        cand.email = slug + "@example.com";
        if (rejected) {
            cand.rejectionReason = com.meridian.hr.recruitment.RecruitmentMeta.REJECTION_REASONS
                    .get(Math.floorMod(hashStr(spec.name()), com.meridian.hr.recruitment.RecruitmentMeta.REJECTION_REASONS.size()));
            cand.rejectedAt = daysAgo(3 + (int) Math.floor(rnd(rng) * 10));
        }
        String company = spec.role().contains(",") ? spec.role().split(", ")[1] : "industry";
        cand.summary = spec.name().split(" ")[0] + " brings " + spec.exp().split(" ")[0] + " years across " + company + ". "
                + (fit >= 90 ? "Strong signal on the core competencies for this role."
                : fit >= 82 ? "Solid all-round profile with a couple of areas to probe."
                : "Promising but needs validation on depth.");

        int reachedIdx = com.meridian.hr.recruitment.RecruitmentMeta.stageIndex(rejected ? "interview" : spec.stage());
        for (Requisition.Round ip : req.interviewPlan) {
            int ipIdx = com.meridian.hr.recruitment.RecruitmentMeta.stageIndex(ip.stageId);
            if (ipIdx > reachedIdx) continue;
            boolean current = ip.stageId.equals(spec.stage());
            java.util.Map<String, Candidate.Scorecard> cards = new java.util.LinkedHashMap<>();
            for (int k = 0; k < ip.interviewerIds.size(); k++) {
                String iid = ip.interviewerIds.get(k);
                if (current && k == ip.interviewerIds.size() - 1 && rnd(rng) > 0.4) continue;
                int[] r2 = {hashStr(cand.id + ip.stageId + iid)};
                Candidate.Scorecard card = new Candidate.Scorecard();
                double sum = 0;
                for (String aid : req.scorecard) {
                    int v = clamp5(strength + (rnd(r2) - 0.5) * 1.3);
                    card.ratings.put(aid, v);
                    sum += v;
                }
                double avg = sum / Math.max(1, req.scorecard.size());
                card.rec = avg >= 4.2 ? "strong_yes" : avg >= 3.4 ? "yes" : avg >= 2.6 ? "no" : "strong_no";
                card.comment = switch (card.rec) {
                    case "strong_yes" -> "Excellent signal — clears the bar comfortably.";
                    case "yes" -> "Solid across the board; a hire for this role.";
                    case "no" -> "Some gaps on the core attributes; leaning no.";
                    default -> "Did not meet the bar on the fundamentals.";
                };
                card.submittedAt = daysAgo(2 + (int) Math.floor(rnd(r2) * 6));
                cards.put(iid, card);
            }
            if (!cards.isEmpty()) cand.scorecards.put(ip.stageId, cards);
        }

        if ("offer".equals(spec.stage()) || "hired".equals(spec.stage())) {
            int baseComp = switch (req.dept) {
                case "Engineering" -> 145;
                case "Revenue" -> 120;
                case "Design" -> 115;
                default -> 95;
            };
            Candidate.Offer o = new Candidate.Offer();
            o.base = baseComp + (int) Math.round(rnd(rng) * 20);
            o.bonus = 10 + (int) Math.round(rnd(rng) * 10);
            o.equity = 0.02 + Math.round(rnd(rng) * 5) / 100.0;
            o.level = req.level;
            o.startDate = "2026-08-17";
            o.status = "hired".equals(spec.stage()) ? "accepted" : "extended";
            o.approverId = "alex.whitfield";
            o.approvedAt = daysAgo(4);
            o.extendedAt = daysAgo(3);
            o.acceptedAt = "hired".equals(spec.stage()) ? daysAgo(1) : null;
            cand.offer = o;
        }
        return cand;
    }

    private static final String[] CAND_AVATARS = {"#6b7db5", "#6ba58f", "#b56b8f", "#c07f4f", "#7a6bb5",
            "#5a8fb5", "#4a9d9d", "#b58f4a", "#9a6ab5", "#6b8fb5"};

    private static String candInitials(String name) {
        String[] w = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length && sb.length() < 2; i++) {
            if (!w[i].isEmpty()) sb.append(Character.toUpperCase(w[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }
}
