# Meridian HR — UI implementation gaps (confirmation pass, 2026-07-16)

> **STATUS UPDATE (same day): ALL GAPS FIXED & VERIFIED.** Gaps 1–5 implemented from the
> design specs (`design/meridian-hr` mockups + stores); gap 6 minor defects fixed. 30-point
> browser verification: 26 first-pass PASS; the 4 flagged items closed — step-reorder no-op
> (renumber() re-sorted by stale order values before reassigning; fixed by swapping order
> values), template delete-guard bypass (COW snapshots hid active cases from the family
> check; fixed with `<id>-v` prefix matching), and two false alarms (toasts render fine —
> the new 8s auto-dismiss beat the verifier's snapshot timing). Bonus integration: Settings
> competency renames now flow into the performance designer (`PerformanceMeta.catalog`);
> audit log gained category chips + search (filtered before the 80-row cap).

Method: pretend-user walkthrough as HR executive (priya.nair) with on-screen verification +
diff of `design/meridian-hr` (`*.dc.html` mockups + `*-store.js` data layers) against the
implementation. Every item below is confirmed either on screen (📷 = screenshot exists in the
Playwright output dir) or in the design source (file refs given).

## Ranked gaps

### 1. Performance cycles — create-only lifecycle (design has the full designer) 🔴
- "New cycle" instantly mints a draft and lands on a **read-only** designer; no name/type/dates/
  weights/participants editing, no rename, no delete. 📷
- Draft buttons: Design + Launch only. Active/Closed: "View reviews" only. **No close action
  anywhere** (impl endpoint `POST /performance/cycle/{id}/close` exists but has no button —
  faithful to the design, whose store has `closeCycle`/`deleteCycle` with no buttons either).
- Live proof of the damage: launching the accidental draft produced a **permanent second Active
  cycle** ("New review cycle", overlapping H1 2026) with no way out. 📷 Cycle count is monotonic
  garbage growth.
- Design spec exists and is complete: `Cycle Designer.dc.html:33-296` + `performance-store.js` —
  auto-saving draft designer (name, type select, 3 timeline dates, competency rows from shared
  library w/ weight clamp + "Balance evenly" + total-must-be-100 badge, per-department participant
  toggles), Launch gated on (name ≠ "" ∧ weights = 100 ∧ participants ≥ 1), designer locked
  read-only after launch. `calibrationDate` is in the model/cards but has no designer input —
  decide: add 4th date or derive.

### 2. Recruitment requisition — same create-only pattern 🔴
- "New requisition" creates REQ with defaults (title "New requisition", Engineering/Mid/Remote),
  no form; detail banner: "read-only in the sample". Title/dept/level never editable; no delete
  draft. 📷
- Lifecycle holes: pending-approval req has **Approve but no Reject**; an Open req's only exit is
  "Mark filled" (no cancel for a role you abandon).
- Design: `New Requisition.dc.html:38-125,195` — editable designer (title, dept, level, headcount,
  location, owner, scorecard-attribute toggles, interview-panel toggles), submit-for-approval gated
  on validity, fields lock after draft.

### 3. Admin write surface missing entirely (Settings / Org / Roles) 🔴
- `/settings` read-only ("Inline editing is deferred") 📷; design (`Settings.dc.html`) specifies:
  workweek target hours + working-day chips, per-type leave allowances + noticeDays/ceilingDays/
  sickCertDays rules, blackout CRUD, holiday CRUD, job-change type field config, competency
  library editor (feeds the cycle designer → compounds gap #1).
- Knock-on: impl **hard-codes** policy values the design intends to be HR-configurable and to
  drive leave validation, the wizard's policy preview, and timesheet targets/holiday autofill.
- `/org` read-only with no "deferred" notice 📷; design: departments add/remove (blocked while
  headcount > 0), levels add/remove/reorder, comp-band editor per track (min/max validation,
  remove blocked if in use).
- `/roles` read-only ("reassigning roles is deferred") 📷; design: per-employee access-role select.

### 4. Onboarding templates — read-only viewer + broken detail route 🟠
- No template create/edit/delete; step panel labeled "Read-only". 📷 Design
  (`Onboarding.dc.html:424-448,595-664`): full editor — template CRUD, step add/delete/reorder,
  title/system/owner/dueOffset, autoAssign toggle ("Dioschub executes automatically"),
  requiresDoc + doc name.
- **Bug:** `/onboarding/templates/{id}` (linked from the tab) renders a blank page — heading only,
  no content/back link. 📷

### 5. Recruitment interviewer flow — scorecards & notes designed, absent 🟠
- Candidate detail renders scorecards but there is **no submit endpoint** (verified: no such
  `@PostMapping`). Design (`Candidate.dc.html:307-317`): per-stage 1–5 ratings per requisition
  scorecard attribute + recommendation + comment, debrief roll-up.
- Candidate free-text note timeline (`Candidate.dc.html:318`) — no impl endpoint.

### 6. Minor defects 🟡
- **Success toast never auto-dismisses and intercepts clicks** — after leave submit it sits over
  the drawer's "Withdraw request" button indefinitely (verified via elementFromPoint; reload to
  clear). 📷
- `;jsessionid=…` leaks into the visible URL after the leave-submit redirect.
- Audit log: no filters/search over 80-event window.
- Notification bell accessible label alternates between count and "Notifications" across pages.

## Confirmed clean (full lifecycles, no gaps found)
Dashboard · Leave (request→withdraw round-trip verified) · Approvals (approve/reject/info/undo) ·
Time (save/submit/clock/approve) · Profile (info/bank/lists) · Directory (add/edit/status/360) ·
Job changes (raise w/ full field modal, approve/reject/withdraw) · Onboarding cases (start/steps/
upload/reopen) · Offboarding (start/toggle/complete/cancel) · Candidate pipeline + offer flow ·
Analytics · Audit (correctly logged the test's actions).

## Notes
- The design repo's own stores contain `deleteCycle`, `closeCycle`, `deleteReq`, `moveCandidate`,
  `deleteCase` with no design-UI buttons — data-layer intent without screens; pick UI spots when
  implementing (suggested: delete on draft cards only, close on active cycle cards).
- Fix strategy decision per gap: complete the lifecycle (per design spec above) vs. retract the
  promise (hide the create button / label viewers read-only). #1 and #2 warrant completion; #3
  unlocks config-driven policy behavior across leave/time.
