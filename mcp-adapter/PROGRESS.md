# Meridian HR — Front-Door MCP Adapter · Build Progress

> Working doc for the overnight loop. Every iteration updates this file.
> Target for verification: **staging** (`https://meridian-hr-staging.up.railway.app`).
> The adapter starts with an empty cookie jar → its own isolated per-device workspace,
> so writes never disturb a human reviewer.

## What this is

A generic **HTML front-door** MCP server that drives Meridian's server-rendered UI as a
legacy app with **no REST API**. It logs in as a persona, GETs pages, scopes + sanitizes
the region to text (fuzzy, for the model), lifts the write handle from an attribute
(exact), and POSTs the same form the browser would. Meridian's own RBAC gates every
action — the write handle only exists in the HTML when the session is allowed to act.

- Engine: `src/engine.ts` (generic) · Catalog: `src/config.ts` (data) · Server: `src/server.ts` (stdio MCP)
- Reads are **fuzzy** (sanitized text); write handles + form bodies are **exact** (engine-owned).

## Per-domain contract (one domain per loop iteration)

1. Read the domain controller in `../src/main/java/com/meridian/hr/<domain>/` for routes,
   `@GetMapping`/`@PostMapping`, and form param names. Read the matching template(s) for
   the row anchor (prefer a semantic hook: a form `action`, a link `href`) and empty markers.
2. Add read/write tool entries to `src/config.ts`.
3. Add a domain block to `scripts/verify.ts` proving **read** (id + summary populated) and,
   where it has writes, **write** (the change took) — using the right persona for RBAC.
4. `npx tsc && npx tsx scripts/verify.ts`. If green → commit `feat(mcp-adapter): <domain> tools`,
   tick the backlog below, append a one-line note.
5. **Blocked?** (ambiguous contract, write needs un-seedable data, staging down) → log under
   "Blockers", move to the next domain. Do **not** thrash or guess.

Stop the loop when the backlog is clear or a blocker repeats across iterations; leave a summary here.

## Backlog

| # | Domain | Controller | Planned tools | Status |
|---|--------|-----------|---------------|--------|
| 0 | leave | `leave/LeaveController` | list_pending_approvals, approve_leave, reject_leave | ✅ done + verified |
| 1 | time | `time/TimeController` | list_time_approvals, approve_timesheet | ✅ done + verified |
| 2 | jobchange | `jobchange/JobChangeController` | list_job_changes, approve_job_change, reject_job_change | ✅ done + verified |
| 3 | recruitment | `recruitment/RecruitmentController` | list_requisitions, list_candidates, advance_candidate | ⬜ next |
| 4 | directory | `people/DirectoryController` | search_directory, get_employee | ⬜ |
| 5 | onboarding | `onboarding/OnboardingController` | list_onboarding_cases, complete_onboarding_step | ⬜ |
| 6 | offboarding | `offboarding/OffboardingController` | list_offboarding, complete_offboarding_step | ⬜ |
| 7 | profile | `profile/ProfileController` | get_my_profile, submit_profile_change, approve_profile_change | ⬜ |
| 8 | performance | `performance/PerformanceController` | list_reviews, list_cycles | ⬜ |
| 9 | analytics | `analytics/AnalyticsController` | get_analytics (read-only) | ⬜ |
| 10 | admin | `admin/AdminController` | get_admin_settings, get_audit_log (read-only) | ⬜ |

## Log

- **2026-07-15** — Foundation: generic engine (session/cookie-jar → GET+scope+sanitize read →
  form-POST write → post-write verify → fail-loud on empty scope), leave slice, stdio MCP server.
  Verified on staging: HR queue read (R-1048/R-1049 w/ id+summary), approve_leave 2→1, employee
  queue empty (RBAC-for-free). `tools/list` returns all 3 tools. Build clean.
- **2026-07-15** — time: list_time_approvals + approve_timesheet. **No reject route exists** for
  timesheets (controller only save/submit/approve/clock) — dropped reject_timesheet, did not invent it.
  Drove a **coherent engine generalization**: reads now extract multi-field composite handles
  (time's write key is `empId`+`week` in hidden inputs, not a path id), and post-write verify now
  re-runs the read and asserts the handle is gone (replacing the leave-only string-includes check).
  Leave migrated to the same shape. Verified on staging: HR read 2 pending timesheets, approve 2→1,
  employee empty. Empty-detection uses "Recent weeks" as the page-loaded sanity marker.
- **2026-07-15** — jobchange: list_job_changes + approve_job_change + reject_job_change (both HR-only,
  path-id, mirror leave). Skipped the create route (`/job-changes/new` is a GET-recompute multi-field
  modal — out of scope for a tool) and cancel/withdraw. Rich diff summary flows through as text
  (title/level/salary/band before→after). Sanity marker = the "Pending approval" stats-card label so a
  loaded-but-no-pending page reads empty, not fail-loud; "managed by managers" covers non-approvers.
  Verified on staging: HR read 1 pending, approve 1→0, reject (fresh workspace) gone, employee restricted.

## Blockers

_(none yet)_
