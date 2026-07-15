# Meridian HR — build playbook (read this first)

A Dioschub **sample app**: a server-rendered Java/Thymeleaf enterprise HR portal.
Build it **app-first**; the Dioschub assistant + MCP integration is a **later phase**
(the assistant rail is already a reserved, inert mount). Keep `../../diosc-ai` **read-only**.

## Stack & run
- Spring Boot 3.3.5, Java 21, Thymeleaf + thymeleaf-layout-dialect, Gradle (wrapper 8.10.2). No DB.
- Dev port **3020**. Build: `./gradlew --no-daemon compileJava`. Run: `./gradlew --no-daemon bootRun`
  (background + poll `bootrun.log` for `Started MeridianHrApplication`).
- Login: pick a demo persona or type any seeded email (password decorative). Personas:
  `sarah.chen`=Employee, `david.okonkwo`=Manager, `priya.nair`=HR.

## The design (source of truth for data + logic)
- Fixture prototype in **`design/prototype/`** — 27 `*.dc.html` screens + `*-store.js` stores.
- Each `*-store.js` = the server-side model + business logic to PORT to Java (seed data,
  policy checks, approval flows, calibration, etc.). Screens = the Thymeleaf templates to build.
- `AppShell.dc.html` = the shell (already ported → `layout.html`). `Login.dc.html` → `login.html`.

## What's DONE (phases 0–2, verified running)
- `com.meridian.hr.domain`: `Role`, `EmployeeStatus`, `Employee` (+nested value classes),
  `OrgConfig`, `PolicyConfig`. **Convention: public-field classes** (reliable SpringEL,
  mutation-friendly), derived values as methods (call `${emp.fullName()}` in templates).
- `workspace`: `Workspace` (per-device state holder), `WorkspaceStore` (Map + on-access
  TTL[2h]/LRU[2000] + `@Scheduled sweep()`), `Seed.build()` (15 employees + org + policy).
- `session`: `DeviceFilter` (`meridian_device` cookie → resolves workspace onto request),
  `SessionContext` (@RequestScope: `currentUser()`, `actor()`, `workspace()`), `SessionService`.
- `web`: `AuthInterceptor`+`WebConfig` (redirect to /login), `LoginController` (/login,
  /logout, /reset), `HomeController` (/ → /dashboard), `DashboardController` (/dashboard),
  `LayoutAdvice` (@ControllerAdvice: feeds every page the nav + user + diosc config),
  `NavItem`.
- `diosc`: `DioscProperties` (meridian.diosc.*, empty → rail inert).
- templates: `layout.html` (decorator: topbar, role-gated nav, content slot, assistant rail,
  `content-after` overlay slot for modals/toasts), `fragments/assistant-rail.html`
  (reserved `<diosc-chat>` mount behind `diosc.configured`), `login.html`, `dashboard.html`.
  CSS tokens: `static/css/app.css`.

### Phase 3 — Directory + Employee (DONE, verified)
- `com.meridian.hr.people.PeopleService` (@Service, injects `SessionContext`): ports
  `people-store.js` — stats, org relationships (managerOf/directReports/orgRoots/chainOfCommand),
  `canViewComp` (HR / self / management-chain), `add`, `applyEdit` (+job-history events),
  `setStatus`, and static formatters (`formatDate`/`formatSalary`/`tenureLabel`, exposed as
  instance aliases `date`/`salary`/`tenure` for templates).
- `DirectoryController`: `/directory` (grid|list|org via `?view`, `?q` search, `?dept` filter,
  `?add` opens HR modal), `/directory/{id}` (360 profile, `?tab=overview|timeleave|performance`,
  `?edit` edit card), POST `/directory/new`, `/directory/{id}/edit`, `/directory/{id}/status`.
  Flash toast via `RedirectAttributes` (`toast`/`toastDot`). View records live on the controller.
- templates `directory.html`, `employee.html`. Time&Leave + Performance tabs render empty
  placeholders — wire them when the Leave/Time/Performance domains land. HR-only Add/Edit/Status;
  comp + bank/tax gated on `canComp`. Offboard button is a disabled "soon" stub until Offboarding.
- Fixed the active-nav nit: `navList(items, active)` now takes `active` as an explicit fragment
  param and compares with `#strings.equals`.

### Phase 4 — Leave (DONE, verified)
- Domain `LeaveRequest` (+ nested `Event`) in `com.meridian.hr.domain`; state on `Workspace`
  (`leaveRequests`, `leaveReadAt`); seeded by `Seed.seedLeave` (11 requests + event timelines).
- `com.meridian.hr.leave.LeaveMeta` — static type/status/event/approver metadata + date/stamp
  helpers (pure). `LeaveService` — queries, balances, mutations (add/decide/revert/cancel),
  the **policy engine** (`evaluate` → 6 checks: balance/blackout/coverage/notice/cert/overlap,
  + ceiling→HR routing), and notifications derived from the event log. Demo clock pinned
  `DEMO_TODAY = 2026-07-02` so seeded scenarios stay meaningful.
- `LeaveController`: `/leave` (My Leave), `/leave/new` (GET recompute + POST submit),
  `/approvals`, `/leave/{id}/{approve,reject,info,undo,withdraw}`. Shared drawer +
  toast in `fragments/request-drawer.html`; pages `leave/{my-leave,new-leave,approvals}.html`.
- New Leave is a single GET form: type radios / date inputs / half-day / cert-attach auto-submit
  (`onchange="this.form.submit()"`) to recompute checks live; the Submit button overrides to POST
  via `formmethod="post"`. The fixture's interactive calendar is replaced by native date inputs +
  a "holidays in range" panel (deliberate JS-free simplification).
- `LayoutAdvice` now wires the notification badge + Approvals nav badge from `LeaveService`.
  Dashboard balances + "New leave request" button now use real leave data.

### Phase 5 — Time (DONE, verified)
- Domain `Timesheet` (mon..sun `Map<String,Double>` + status open/submitted/approved); state on
  `Workspace` (`timesheets`, `clockIns`); `Seed.seedTime` (Sarah 3 approved weeks + Marcus/Ken
  submitted, relative to real `LocalDate.now()` Monday weeks).
- `com.meridian.hr.time.TimeService` — week math (Monday start), get/save/submit/approve,
  clock in/out (elapsed added to today on clock-out), holidays + approved/posted-leave grid
  overlays (via `LeaveService`), pending-approvals scoped (HR=all, manager=direct reports).
- `TimeController` `/time?week=`; POST `/time/{save,submit,approve,clock}`. Timesheet is one
  `<form>`: editable weekday inputs + "Save" and "Submit week" (`formaction` override). Clock +
  approve are their own POST forms. Week nav = `?week=` links. `time/my-time.html`.
- Isolation note: approvals are per-device — an employee's submit in their own workspace does NOT
  appear in a manager's separate device (expected; the seed drives each device's queue).

### Phase 6 — Profile (DONE, verified)
- Domain `ProfileChange` (pending sensitive-field edit); state on `Workspace.profileChanges`.
  `PeopleService` gained: `getPath`/`setPath` (dotted paths for top-level + address/bank/taxIds),
  `SENSITIVE_PATHS` + `isSensitive`, `completeness`, and change-request CRUD
  (`requestChange`/`pendingChangesFor`/`hasPendingChange`/`approveChange`/`rejectChange`).
- `com.meridian.hr.profile.ProfileController` `/profile` (+`?edit=info|bank`): non-sensitive edits
  apply immediately via `setPath`; sensitive (legalName, bank.*, taxIds.*) → `requestChange` (HR queue).
  Lists (emergency/dependents/skills/certs) = ONE form POST `/profile/lists` reading parallel arrays
  via **`MultiValueMap<String,String>`** (NOT `Map<String,List>`); Add/Remove are buttons in that same
  form (`name="add"`/`name="remove" value="em:3"`) so the handler rebuilds rows first, then mutates —
  no nested forms, no lost edits. Form field names are the path with `.`→`_` (e.g. `bank_bankName`).
- HR decides on the directory profile: `DirectoryController` POST `/directory/{id}/change/{cid}/{approve,reject}`;
  the pending-changes section in `employee.html` is now live (was a stub). Template `profile/my-profile.html`.
- Reminder: workspace is per-DEVICE, not per-user — to test HR approving an employee's request, sign in
  as HR on the SAME device (cookie), not a separate one.

### Phase 7 — Onboarding (DONE, verified)
- Domain `OnboardingTemplate` (+ nested `Step`, fluent builders `.auto()/.doc()/.depends()/.due()`)
  and `OnboardingCase` (+ nested `StepState`, seed helper `.done(stepId,by,at,doc)`) in
  `com.meridian.hr.domain`. State on `Workspace` (`onboardingTemplates`, `onboardingCases`,
  `onboardingConverted` = caseId→employeeId). `Seed.seedOnboarding` builds 4 role schemas
  (design/eng/sales/general — shared 6-step spine + role extras) and 3 cases (Anna mid-flight
  → badge blocked on I-9; Ravi further along; Sofia 100% → convertible).
- `com.meridian.hr.onboarding.OnboardingMeta` — static SYSTEMS catalog + OWNERS + role families
  (pure, mirrors the store's exported consts). `OnboardingService` — the **status engine**
  `resolvePlan(case)` computes each step done→waiting→blocked→in_progress (completing a step
  auto-releases dependents; `completeStep` returns the released count), plus progress/summary/
  activeSummary and `convert` (reuses `PeopleService.add`, records the link).
- `OnboardingController`: `/onboarding` (active list — `?tab`, `?q`, `?status`), `/onboarding(tab=templates)`
  + `/onboarding/templates/{id}` (READ-ONLY schema browser, HR only — full editor deferred per scope cut),
  `/onboarding/case/{caseId}` (status board), `/onboarding/new` (GET preview + POST start), and step
  actions POST `/onboarding/case/{caseId}/step/{stepId}/{complete,upload,reopen}` + `/convert`.
  Templates `onboarding/{onboarding,status,new-onboarding}.html`. New form is one GET form; role
  radios + date auto-submit (`onchange="this.form.submit()"`) to recompute the preview; Submit
  overrides to POST via `formmethod`/`formaction` (same pattern as New Leave). HR-only Start +
  convert; manager/HR do step actions; employee sees a read-only list + HR-access gate on /new.
- Curl note: POSTs redirect (302) to GET-only targets, so `curl -L` re-POSTs → 405 (a curl artifact,
  not a bug); verify resulting state with plain GETs instead.

### Phase 8 — Offboarding (DONE, verified)
- Domain `OffboardingCase` (+ nested `Task`, static `defaultChecklist()` — 7 exit tasks) in
  `com.meridian.hr.domain`; state on `Workspace.offboardingCases`; `Seed.seedOffboarding` seeds
  one in-progress resignation (Ken Ito, 3/7 done) so the board isn't empty. `Employee.ExitInfo`
  + `EmployeeStatus.INACTIVE` already existed.
- `PeopleService.recordExit(empId,type,typeLabel,reason,lastDay,by)` — sets INACTIVE, stamps
  `exit`, writes an "exit" history event. `com.meridian.hr.offboarding.OffboardingService` —
  exit-type metadata, `forActor` (HR all / manager own-reports), summary/progress, `start`
  (dedupes open cases), `toggleTask`, `complete` (guarded on all-done → `recordExit`), `cancel`,
  `candidateEmployees` (active, not already open).
- `OffboardingController` `/offboarding` (?add opens modal, ?emp prefills), POST `/offboarding/new`,
  `/offboarding/{id}/task/{taskId}` (toggle), `/{id}/complete`, `/{id}/cancel`. Template
  `offboarding/offboarding.html`: stat tiles + case cards with a checklist (each task is its own
  POST form button), complete/cancel forms, initiate modal (POST form, opened via `?add=true`,
  closed via link back to `/offboarding` — no JS). Allowed = HR|manager; employee sees a restricted
  panel. Manager scope is naturally reinforced by per-device isolation.

### Phase 9 — Job Changes (DONE, verified)
- Domain `JobChange` (changes + `fromSnapshot` maps, so the diff stays stable after apply) in
  `com.meridian.hr.domain`; state on `Workspace.jobChanges`; `Seed.seedJobChanges` seeds one of each
  status (Sarah pending promotion, Marcus scheduled comp, David applied promotion matching his record).
- `com.meridian.hr.jobchange.JobChangeMeta` — static change-type catalog (fields per type), field
  labels, select-vs-input, status pills. `PeopleService.applyJobChange(empId,changes,by)` +
  `bands()`/`WORK_MODES`/`EMPLOYMENT_TYPES`/`parseSalary`. `JobChangeService` — createRequest,
  approve (eff ≤ today → apply now via `applyJobChange`, else schedule), reject, cancel,
  **applyDueChanges** (called on every index load), diffRows, candidateEmployees, current-value helpers.
- `JobChangeController` `/job-changes` (?add opens modal; ?emp/?type recompute the field editors),
  POST `/job-changes/new`, `/{id}/{approve,reject,cancel}`. The modal is a **GET-recompute form**
  (employee/type/date `onchange="this.form.submit()"` re-renders the per-type editors; Submit overrides
  to POST) — dynamic field values ride as `f_<field>` params, read back via `@RequestParam Map`.
  Allowed = HR|manager; HR decides, a manager may raise+withdraw but not approve; employee sees a
  restricted panel. Template `jobchange/job-changes.html`.

### Phase 10 — Performance (DONE, verified)
- Domain `ReviewCycle` (+ `CompWeight`) and `Review` (self/mgr `Assessment` + `Calibration`) in
  `com.meridian.hr.domain`; state on `Workspace.reviewCycles` + `Workspace.reviews`. `Seed.seedPerformance`
  builds 3 cycles (H1 active, H2 closed, Q3 draft/Engineering) and materializes rich review instances for
  the two non-draft cycles via a **deterministic port of the fixture's `buildReview`** (mulberry32 RNG +
  FNV-1a `hashStr`, phase-override map, Marcus fixed scores) so seeded scores look real and stay stable.
- `com.meridian.hr.performance.PerformanceMeta` — competency library, cycle-type labels, status pills
  (with a 1–4 step for the stage bar), score bands, heat-cell colors. `PerformanceService` — cycle
  lifecycle (create/launch→ensureReviews blank rows/close), review mutations (submitSelf/submitManager
  seeds calibration/setCalibrated/commit/reopen), status computation, weighted averages, and roll-ups
  (completionFor/distributionFor/gapsFor/perEmployee).
- `PerformanceController` `/performance` (?tab=cycles|reviews|reports, ?cycle, ?q, ?status), cycle actions
  (POST new→designer, launch, close), `/performance/designer` (READ-ONLY schema — editable builder deferred;
  new drafts pre-fill all active employees so they're launchable), `/performance/review` (?cycle&emp) +
  POST self/manager/commit/reopen, and `/performance/export` (@ResponseBody CSV download, `attachment`
  disposition). Templates `performance/{performance,designer,review}.html`; review.html uses a
  `th:fragment="cell(v)"` heat cell. Role gating: HR = all 3 tabs + all reviews; manager = "My team"
  (own reports); employee = "My review" (own). Score params ride as `s_<compId>`.

### Phase 11 — Recruitment (DONE, verified)
- Domain `Requisition` (+ `Round`) and `Candidate` (+ `Scorecard`/`Offer`/`Note`) in
  `com.meridian.hr.domain`; state on `Workspace.requisitions` + `Workspace.candidates`.
  `Seed.seedRecruitment` builds 5 reqs + candidates via a **deterministic port of `genCandidate`**
  (reuses the mulberry32/hashStr RNG already in Seed; seeds scorecards + offers).
- `com.meridian.hr.recruitment.RecruitmentMeta` — stages, rec scale, scorecard library, sources,
  rejection reasons, status pills, dept→onboarding-role. `RecruitmentService` — req lifecycle
  (create/submit/approve/close), candidate pipeline (add/advance/move/reject/reopen), offer flow
  (make/approve/extend/**accept → hire + `OnboardingService.startOnboarding`**/decline), funnel +
  debrief + company reports roll-ups.
- `RecruitmentController` `/recruitment` (?tab=reqs|reports), req lifecycle POSTs,
  `/recruitment/req/{id}` (READ-ONLY detail — editor deferred), `/recruitment/req/{id}/pipeline`
  (stage-column board + add candidate), `/recruitment/candidate/{id}` (+ POST advance/reject/reopen
  + offer/{make,approve,extend,accept,decline}). Templates `recruitment/{recruitment,requisition,
  pipeline,candidate}.html`; interviewer scorecards are seeded read-only in the debrief. Allowed =
  HR|manager; HR opens/approves reqs + approves offers; employee sees a restricted panel.
- **Cross-domain loop verified**: accepting an offer hires the candidate AND opens an onboarding
  case (the hire then flows through the Onboarding board).

### Phase 12 — Analytics + Admin (DONE, verified — FINAL)
- All read-only, deriving from existing workspace state. `com.meridian.hr.analytics.AnalyticsController`
  `/analytics` — KPI strip + headcount-by-dept / by-status / by-work-mode / by-level bars + active-cycle
  review completion (from `PeopleService.stats` + Recruitment + Performance); allowed = HR|manager.
- `com.meridian.hr.admin.AdminController` (HR only): `/settings` (PolicyConfig: work week, leave
  allowances, thresholds, blackouts, holidays + competencies + job-change types), `/org` (departments
  with lead+count, levels, comp bands), `/roles` (employees grouped by access role + role blurbs),
  `/audit` (unified trail synthesized from every domain's event log — employee history, leave events,
  profile changes, job changes, exits — newest first, latest 80). Templates `analytics/analytics.html`,
  `admin/{settings,org,roles,audit}.html`. Editing all four admin surfaces is deferred (read-only).

## ✅ BUILD COMPLETE (2026-07-15)
Every domain in the order below is built + verified (route 200 + per-device isolation + role gating).
All 16 nav routes return 200; the left nav has zero "soon" items. Remaining work is the sample-repo
ship norms (MCP module, Dockerfile, README/LICENSE/env/railway.json) — see the section at the bottom —
and the **AI/assistant-kit integration**, both intentionally deferred per the original brief.

## ⚠️ Thymeleaf gotchas that cost real time (READ before writing templates)
1. **Never name a `th:each` variable `lt` / `gt` / `eq` / `ne` / `le` / `ge` / `or` / `and` /
   `not` / `div` / `mod`** — these are reserved operator tokens. Thymeleaf fails to PARSE the
   each with `IllegalArgumentException: Iteration variable cannot be null` (misleading message).
   Use a longer name (`ltype`, not `lt`).
2. **Template edits require a bootRun restart.** The running app serves templates from
   `build/resources/main/templates`, populated by `processResources` at build start. Editing
   `src/main/resources/...` and re-curling hits the STALE copy. Always kill + restart bootRun
   after template changes (it re-runs processResources). Java changes also need restart.
3. Run bootRun as a **harness background task** (not shell `&`) — a `&` job dies when the Bash
   tool call returns, silently leaving :3020 either free (next start fine) or half-bound.
4. The `;jsessionid=...` on the first redirect URL is cosmetic (pre-cookie); ignore it.
5. **An apostrophe inside a Thymeleaf `'...'` string literal SILENTLY TRUNCATES the response.**
   `th:text="'Applied to ' + ${x} + '''s record.'"` throws mid-render → the page is cut off at that
   point (status already 200, so it looks like a partial/blank tail, e.g. `content-after` vanishes).
   Fix: use literal substitution `th:text="|Applied to ${x}'s record.|"` (pipes handle apostrophes).
7. **`th:style` REPLACES the static `style` attribute — it does NOT merge.** `<div style="width:22px;
   border-radius:50%" th:style="'background:'+${x}">` renders as `style="background:#…"` ONLY — losing
   width/border-radius (avatar → square, pill → unpadded text, progress bar → collapsed/invisible). This
   was app-wide (156 sites, incl. the original Phase 0-6 templates) and only obvious on zoom. Fix: use
   **`th:styleappend`**, and prefix `'; '` so the separator is always valid regardless of whether the
   static style ends in `;`: `th:styleappend="'; ' + ('background:' + ${x})"`. (Same story for
   `th:class` vs `th:classappend`.) Bulk-convert with a regex on `th:style="([^"]*)"` — the value never
   contains a `"`, so it's a safe capture.
8. **`th:replace`/`th:insert` run BEFORE `th:if` on the SAME element** (precedence order 1 vs 3), so a
   guard like `<th:block th:if="${x != null}" th:replace="~{frag :: f(${x})}">` does NOT protect the
   fragment — it's always invoked, NPE-ing on null. Put the guard on an OUTER element:
   `<th:block th:if="${x != null}"><th:block th:replace="~{frag :: f(${x})}"/></th:block>`. This class
   of bug commits a 200 then errors mid-render → the page HANGS in a browser. **Always verify templates
   by reading the FULL body, never just the status code** — `-w %{http_code}` alone passes spuriously.

## Per-iteration contract (the loop follows this, ONE domain per step)
1. Pick the next unbuilt domain (order below). Read its `design/prototype/<Screen>.dc.html`
   + `<domain>-store.js`.
2. **Domain**: add entities to `com.meridian.hr.domain` (public-field classes, nest value types).
3. **State**: add the domain's collection(s) to `Workspace` and seed data in `Seed`.
4. **Service**: `com.meridian.hr.<domain>` service class operating on the request's `Workspace`
   (inject `SessionContext`), porting the store's logic + role gating (use `actor().role`).
5. **Controller** in `web` (or a domain package): map routes, set `model.addAttribute("active", "<navkey>")`.
6. **Template(s)**: `templates/<screen>.html` with `layout:decorate="~{layout}"` +
   `<div layout:fragment="content">`. Port the `.dc.html` markup; reuse `app.css` classes,
   add domain CSS as needed.
7. **Enable nav**: add the nav key(s) to `LayoutAdvice.BUILT` so the item stops rendering "soon".
8. **Verify**: `compileJava`, then bootRun + curl the route(s) for HTTP 200 + key strings, for the
   relevant role(s). Mark the task. STOP.

## Domain order (nav keys in parens)
1. ~~Leave~~ ✅ DONE (Phase 4).
2. ~~Time~~ ✅ DONE (Phase 5).
3. ~~Profile~~ ✅ DONE (Phase 6).
4. ~~Onboarding~~ ✅ DONE (Phase 7). Templates read-only; full editor deferred.
   <details><summary>original plan (kept for reference)</summary>
   **Design already reviewed** (`onboarding-store.js` + `Onboarding.dc.html`). Scoping plan for a
   tractable build: (a) DOMAIN — a `Template` (role schema: ordered `Step` defs with id/title/system/
   owner/dependsOn/requiresDoc/autoAssign/dueOffset) and a `Case` (instance: hire fields + per-step
   `{completed, completedAt, completedBy, docUploaded}` map + templateId). Seed 4 templates
   (design/eng/sales/general — a shared 6-step spine + role extras) and 3 cases (Anna/Ravi/Sofia)
   exactly as `seedTemplates()`/`seedCases()`. (b) ENGINE — `resolvePlan(case)` computes each step's
   status: done → waiting(dep not done) → blocked(requiresDoc & !docUploaded) → in_progress; plus
   `progressOf`/`caseSummary`(health complete|blocked|on_track)/`activeSummary`. (c) CONTROLLER
   `/onboarding` (active list: search + status filter), `/onboarding/{caseId}` (status view: the plan
   + complete/upload/reopen POST actions + "Add to Directory" convert via `people.add`), `/onboarding/new`
   (GET form + POST start). Toast on complete showing "released N dependent steps" (diff waiting→unblocked).
   (d) SCOPE CUT: render Templates as a READ-ONLY tab (list schemas + step counts); DEFER the full
   template editor (drag-reorder/toggles/auto-save) — note it. HR-only Start + convert; manager/HR can
   manage steps. Convert reuses `PeopleService.add` and marks the case converted (add a
   `Map<String,String> onboardingConverted` on Workspace: caseId→employeeId).
   </details>
5. ~~Offboarding~~ ✅ DONE (Phase 8). Exit checklist → `recordExit` marks INACTIVE.
6. ~~Job Changes~~ ✅ DONE (Phase 9). Effective-dated; approve applies now or schedules.
7. ~~Performance~~ ✅ DONE (Phase 10). Designer read-only; deterministic seeded scores.
8. ~~Recruitment~~ ✅ DONE (Phase 11). ATS + offer→hire→onboarding loop; designer read-only.
9. ~~Analytics + Admin (Settings, Org, Roles, Audit)~~ ✅ DONE (Phase 12, final). All read-only.

## Known nits (fix opportunistically)
- `home.html` is now unused (HomeController redirects). Delete when convenient.
- ~~Leave drawer `dr.back()` NPE~~ **FIXED (2026-07-15).** It was NOT non-reproducing — the bare
  My-Leave / Approvals list (no `?req=`, so `dr` is null) crashed **every time**, hanging the page
  (200 headers committed, then template error mid-render → browser waits forever on the body). The
  earlier "defensive guard" was on the SAME element as `th:replace`, which is useless: **`th:replace`
  has HIGHER precedence (order 1) than `th:if` (order 3)**, so the fragment is invoked before the guard
  is ever evaluated. Fix: wrap `th:replace` in an OUTER `th:if` block (see my-leave.html / approvals.html
  content-after). Lesson: a status-code-only smoke test (`-w %{http_code}` with no body read) passes
  spuriously on this class of bug — always pull the full body when verifying a template renders.
- Directory grid/list/org rebuild the full org tree per request (fine at 15–16 people; no caching).
- Employee edit is a focused edit-card sub-view (`?edit`), not the fixture's inline-in-place edit —
  a deliberate simplification to keep `<form>` boundaries sane server-side.

## Sample-repo norms to finish before ship (later)
- Java MCP module (`mcp/`, own Dockerfile.mcp, port 5174) that forwards to app REST `/api/v1/*`
  with the BYOA bearer; app mints/verifies HMAC artifact; `POST /api/diosc/bind` → hub `/api/auth/bind`.
- `Dockerfile` (app :3000 via `PORT`), `README.md`, `LICENSE` (MIT), `.env.example`, `railway.json`.
- See the cadence/plynth samples for the exact MCP relay + bind shapes.
