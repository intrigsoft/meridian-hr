# Meridian HR — Front-Door MCP Adapter

An MCP server that lets a DioscHub assistant operate **Meridian HR**, a server-rendered
Thymeleaf app with **no REST API** — the common shape of real legacy line-of-business
systems. It's an **Open/Closed extension**: the app stays untouched; the adapter drives
its existing HTML interface from the outside, the same way a browser does.

## How it works

```
Meridian ──POST /auth/bind (identity + artifact)──►  this adapter  ──mints JWT──►  Hub
Hub ──MCP over Streamable HTTP (Bearer JWT)──────►  this adapter  ──HTTP + HTML──►  Meridian
   (the token broker)                                (the front door)
```

**Token broker (MCP authorization spec 2025-11-25).** The adapter is an OAuth-style
resource server that accepts only tokens issued *for itself* — and it is also the issuer.
At bind time Meridian POSTs the visitor's identity + artifact (`session:<meridian_device>`)
to the adapter's `/auth/bind`; the adapter **caches the artifact**, mints a short-lived
audience-bound **HS256 JWT** referencing it, and registers that JWT with the hub. The hub
holds only the JWT (never the cookie). On each tool call the hub presents the JWT; the HTTP
layer validates it and **swaps in the cached artifact** before the transport runs — no token
passthrough upstream. Per tool call the adapter then:

1. **Session** — resolves the caller's identity from the exchanged artifact,
   with no shared/singleton session, so two users never cross wires. Two artifact shapes:
   - `session:<meridian_device>` — **pass-through** (production): the forwarded value is the
     visitor's own signed-in device cookie; the adapter acts as them holding **no credentials
     of its own**. The cookie is never serialized into a result, so the model never sees it.
   - `persona:<userId>` — **dev exchange**: Meridian's login is passwordless, so the adapter
     mints a signed-in session via `POST /login`. Local/demo convenience only.
   An invalid/expired artifact makes Meridian bounce to `/login`; the adapter returns a
   `401 unauthorized` error the hub classifies as **AUTH** → a mid-turn re-auth prompt.
2. **Read** — GETs the page, scopes to the relevant region by a stable anchor, and
   **sanitizes it to visible text** (`summary`, fuzzy — survives a re-skin). The exact
   record **`id`** is lifted separately from an attribute (the write handle).
3. **Write** — POSTs the same form the browser submits. The model supplies intent +
   values; the adapter owns the exact path, hidden fields, and (for real legacy apps) the
   harvested CSRF token. A post-write check confirms the change took, or fails loud.

**RBAC for free:** because it goes through the front door as a logged-in user, Meridian's
own authorization gates every action — a write handle only appears in the HTML when the
session is allowed to act. There is no side door to bypass.

## Design line

- **Reading is fuzzy** (sanitized text the model reasons over) — robust to markup drift.
- **The write handle and form body are exact** (engine-owned) — no LLM-authored POST bodies.
- **Selectors scope, they don't field-scrape** — one stable anchor per region, not `td[4]`.
- **Empty scope fails loud** — a login redirect or re-skin throws, never a hallucinated result.

## Run

```bash
npm install
npm run build
npm run mcp:http                    # Streamable HTTP server on :5175/mcp — what the hub connects to
npm run mcp                         # stdio server (MCP Inspector / local drives)

npm run verify                      # direct-engine domain-slice suite vs staging
npm run verify:hub                  # transport + BYOA seam: real MCP client, per-call Authorization
```

Env: `MERIDIAN_BASE_URL` (default staging), `PORT`/`MERIDIAN_MCP_PORT` (HTTP, default 5175).
For stdio (no per-call header) identity comes from `MERIDIAN_ARTIFACT` (`persona:…` / `session:…`)
or the `MERIDIAN_PERSONA` shorthand (default `priya.nair`).

## Register in DioscHub

Attach as an MCP instance: transport **http**, serverUrl **`http://<host>:5175/mcp`**, no static
auth. The hub forwards each session's bound artifact — the adapter's own JWT — as the
`Authorization` header on every call; the adapter exchanges it for the cached session, and
Meridian's own RBAC gates each action. The adapter performs the hub bind, so `DIOSC_HUB_URL` +
an admin `auth:bind` key and the signing/bind secrets (`MCP_JWT_SECRET`, `MCP_BIND_SECRET`) live
here (see `.env.example`).

## Tools

**114 tools (29 reads · 85 writes) across 11 domains**, all verified end-to-end against a live
instance. The catalog lives in `src/config.ts` — adding a domain is adding data, not code.

Two engine affordances the catalog uses beyond plain reads/writes:

- **`returns.fromLocation`** — creates that 302 to the new entity's page lift the new id out of the
  redirect `Location` and return it as `created` (leave requests, employees, onboarding cases,
  templates, cycles, requisitions).
- **`expandJson`** — one arg carries a JSON object the engine expands into the app's dynamic form
  fields (review scores `s_<compId>`, scorecard ratings `rating_<attrId>`, allowances `allow_<typeId>`).

| Domain | Reads | Writes |
|--------|-------|--------|
| leave | `list_pending_approvals`, `list_my_leave`, `list_leave_balances` | `approve_leave`, `reject_leave`, `submit_leave_request`, `withdraw_leave_request`, `request_leave_info`, `undo_leave_decision` |
| time | `list_time_approvals`, `get_my_timesheet` | `approve_timesheet`, `save_timesheet`, `submit_timesheet` |
| jobchange | `list_job_changes` | `approve_job_change`, `reject_job_change`, `request_job_change`, `cancel_job_change` |
| recruitment | `list_requisition_approvals`, `list_requisitions`, `get_requisition`, `get_pipeline`, `get_candidate` | `approve_requisition`, `create_requisition`, `update_requisition_details`, `toggle_panel_member`, `submit_requisition`, `reject_requisition`, `delete_requisition`, `close_requisition`, `add_candidate`, `submit_scorecard`, `add_candidate_note`, `advance_candidate`, `reject_candidate`, `reopen_candidate`, `make_offer`, `approve_offer`, `extend_offer`, `accept_offer`, `decline_offer` |
| directory | `search_directory`, `get_employee` | `add_employee`, `edit_employee`, `set_employee_status` |
| onboarding | `list_onboarding_cases`, `list_onboarding_steps`, `list_onboarding_templates`, `get_onboarding_template` | `complete_onboarding_step`, `start_onboarding`, `upload_onboarding_doc`, `reopen_onboarding_step`, `convert_onboarding_to_employee`, `create_onboarding_template`, `update_onboarding_template`, `delete_onboarding_template`, `add_template_step`, `edit_template_step` |
| offboarding | `list_offboarding`, `list_offboarding_tasks` | `toggle_offboarding_task`, `start_offboarding`, `complete_offboarding`, `cancel_offboarding` |
| profile | `list_profile_change_approvals`, `get_my_profile` | `submit_legal_name_change`, `submit_bank_change`, `approve_profile_change`, `reject_profile_change` |
| performance | `list_cycles`, `list_reviews`, `get_review` | `create_cycle`, `launch_cycle`, `close_cycle`, `delete_cycle`, `rename_cycle`, `set_cycle_timeline`, `set_competency_weight`, `add_cycle_competency`, `remove_cycle_competency`, `balance_cycle_weights`, `submit_self_review`, `submit_manager_review`, `commit_calibration`, `reopen_calibration` |
| analytics | `get_analytics` | — |
| admin | `get_admin_settings`, `get_org_structure`, `get_roles_and_access`, `get_audit_log` | `set_work_week_target`, `set_leave_allowances`, `set_leave_rules`, `add_blackout`, `remove_blackout`, `add_holiday`, `remove_holiday`, `add_competency`, `update_competency`, `remove_competency`, `add_department`, `remove_department`, `add_level`, `remove_level`, `add_comp_band`, `update_comp_band`, `remove_comp_band`, `set_access_role` |

Deliberately not exposed: `/time/clock`, `/profile/lists`, `/settings/workweek/day`,
`/settings/change-types/toggle`, `/performance/designer/dept`, `/recruitment/req/{id}/scorecard/toggle`,
`/org/levels/move`, auth routes, and the CSV export.
