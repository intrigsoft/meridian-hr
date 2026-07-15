package com.meridian.hr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The person record — identity, employment, org (managerId), location, comp band,
 * self-service profile, job history and exit. Ported from the fixture's
 * {@code people-store}. Mutable: profile edits, status changes and job-change
 * applications mutate the live workspace copy. Fields are public for direct
 * SpringEL access in templates; derived values are methods (call as {@code emp.fullName()}).
 */
public class Employee {

    // ----- identity / employment -----
    public String id;
    public String first;
    public String last;
    public String initials;
    public String avatarBg;
    public String title;
    public String dept;
    public String level;
    public Role accessRole = Role.EMPLOYEE;
    public String managerId;          // null for org roots
    public String email;
    public String phone;
    public String location;
    public String workMode;           // Remote | Hybrid | On-site
    public String employmentType;     // Full-time | Part-time | Contract
    public String startDate;          // ISO yyyy-MM-dd
    public EmployeeStatus status = EmployeeStatus.ACTIVE;
    public Integer salary;            // null = withheld
    public String currency = "USD";
    public String band;
    public List<Document> documents = new ArrayList<>();

    // ----- extended profile (self-service) -----
    public String legalName = "";
    public String preferredName = "";
    public String dob = "";
    public String gender = "";
    public String pronouns = "";
    public String personalEmail = "";
    public String personalPhone = "";
    public Address address = new Address();
    public List<EmergencyContact> emergencyContacts = new ArrayList<>();
    public List<Dependent> dependents = new ArrayList<>();
    public List<Skill> skills = new ArrayList<>();
    public List<Certification> certifications = new ArrayList<>();
    public List<Asset> assets = new ArrayList<>();
    public TaxIds taxIds = new TaxIds();
    public Bank bank = new Bank();
    public List<HistoryEvent> history = new ArrayList<>();
    public ExitInfo exit;             // null unless offboarded

    public Employee() {
    }

    /** Core employment constructor; profile fields set separately in the seed. */
    public Employee(String id, String first, String last, String avatarBg, String title,
                    String dept, String level, Role accessRole, String managerId, String email,
                    String phone, String location, String workMode, String employmentType,
                    String startDate, EmployeeStatus status, Integer salary, String band) {
        this.id = id;
        this.first = first;
        this.last = last;
        this.initials = initialsOf(first, last);
        this.avatarBg = avatarBg;
        this.title = title;
        this.dept = dept;
        this.level = level;
        this.accessRole = accessRole;
        this.managerId = managerId;
        this.email = email;
        this.phone = phone;
        this.location = location;
        this.workMode = workMode;
        this.employmentType = employmentType;
        this.startDate = startDate;
        this.status = status;
        this.salary = salary;
        this.band = band;
    }

    public String fullName() {
        return (first == null ? "" : first) + " " + (last == null ? "" : last);
    }

    public static String initialsOf(String first, String last) {
        char a = first != null && !first.isBlank() ? Character.toUpperCase(first.charAt(0)) : 'N';
        char b = last != null && !last.isBlank() ? Character.toUpperCase(last.charAt(0)) : 'H';
        return "" + a + b;
    }

    public Employee document(String name, String type, String date) {
        this.documents.add(new Document(name, type, date));
        return this;
    }

    // ===================== nested value types =====================

    public static class Address {
        public String line1 = "";
        public String city = "";
        public String state = "";
        public String zip = "";

        public Address() {
        }

        public Address(String line1, String city, String state, String zip) {
            this.line1 = line1;
            this.city = city;
            this.state = state;
            this.zip = zip;
        }
    }

    public static class EmergencyContact {
        public String name;
        public String relationship;
        public String phone;

        public EmergencyContact() {
        }

        public EmergencyContact(String name, String relationship, String phone) {
            this.name = name;
            this.relationship = relationship;
            this.phone = phone;
        }
    }

    public static class Dependent {
        public String name;
        public String relationship;
        public String dob;

        public Dependent() {
        }

        public Dependent(String name, String relationship, String dob) {
            this.name = name;
            this.relationship = relationship;
            this.dob = dob;
        }
    }

    public static class Skill {
        public String name;
        public String level;

        public Skill() {
        }

        public Skill(String name, String level) {
            this.name = name;
            this.level = level;
        }
    }

    public static class Certification {
        public String name;
        public String issuer;
        public String date;

        public Certification() {
        }

        public Certification(String name, String issuer, String date) {
            this.name = name;
            this.issuer = issuer;
            this.date = date;
        }
    }

    public static class Asset {
        public String name;
        public String tag;
        public String assignedDate;

        public Asset() {
        }

        public Asset(String name, String tag, String assignedDate) {
            this.name = name;
            this.tag = tag;
            this.assignedDate = assignedDate;
        }
    }

    public static class TaxIds {
        public String ssnLast4 = "";
        public String nationalId = "";

        public TaxIds() {
        }

        public TaxIds(String ssnLast4, String nationalId) {
            this.ssnLast4 = ssnLast4;
            this.nationalId = nationalId;
        }
    }

    public static class Bank {
        public String bankName = "";
        public String accountName = "";
        public String accountLast4 = "";
        public String routingLast4 = "";

        public Bank() {
        }

        public Bank(String bankName, String accountName, String accountLast4, String routingLast4) {
            this.bankName = bankName;
            this.accountName = accountName;
            this.accountLast4 = accountLast4;
            this.routingLast4 = routingLast4;
        }
    }

    public static class Document {
        public String name;
        public String type;
        public String date;

        public Document() {
        }

        public Document(String name, String type, String date) {
            this.name = name;
            this.type = type;
            this.date = date;
        }
    }

    /** An entry in the employee's job history (join / promotion / comp / transfer / status / exit). */
    public static class HistoryEvent {
        public String id;
        public String type;
        public String label;
        public String detail;
        public String date;   // ISO yyyy-MM-dd
        public long at;       // epoch millis, for sorting
        public String by;

        public HistoryEvent() {
        }

        public HistoryEvent(String id, String type, String label, String detail, String date, long at, String by) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.detail = detail;
            this.date = date;
            this.at = at;
            this.by = by;
        }
    }

    public static class ExitInfo {
        public String type;
        public String typeLabel;
        public String reason;
        public String lastDay;
        public String by;
        public long completedAt;

        public ExitInfo() {
        }
    }
}
