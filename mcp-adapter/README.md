# Meridian HR — Front-Door MCP Adapter

An MCP server that lets a DioscHub assistant operate **Meridian HR**, a server-rendered
Thymeleaf app with **no REST API** — the common shape of real legacy line-of-business
systems. It's an **Open/Closed extension**: the app stays untouched; the adapter drives
its existing HTML interface from the outside, the same way a browser does.

## How it works

```
Hub ──MCP over Streamable HTTP──►  this adapter  ──HTTP + HTML──►  Meridian
   (Authorization: Bearer <artifact>)  (the front door)
```

The hub connects **per call** and injects the visitor's bound BYOA auth as that request's
`Authorization` header (MCP spec 2025-11-25). Per tool call the adapter:

1. **Session** — resolves the caller's identity from the per-call `Authorization` header,
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

npm run verify                      # 28-tool direct-engine suite vs staging
npm run verify:hub                  # transport + BYOA seam: real MCP client, per-call Authorization
```

Env: `MERIDIAN_BASE_URL` (default staging), `PORT`/`MERIDIAN_MCP_PORT` (HTTP, default 5175).
For stdio (no per-call header) identity comes from `MERIDIAN_ARTIFACT` (`persona:…` / `session:…`)
or the `MERIDIAN_PERSONA` shorthand (default `priya.nair`).

## Register in DioscHub

Attach as an MCP instance: transport **http**, serverUrl **`http://<host>:5175/mcp`**. The hub
forwards the visitor's bound session as `Authorization: Bearer session:<meridian_device>` on
every call — no per-instance credentials, and Meridian's own RBAC gates each action.

## Tools

**28 tools (18 reads · 10 writes) across 11 domains**, all verified end-to-end against staging
(`npm run verify`). The catalog lives in `src/config.ts` — adding a domain is adding data, not code.

| Domain | Reads | Writes |
|--------|-------|--------|
| leave | `list_pending_approvals` | `approve_leave`, `reject_leave` |
| time | `list_time_approvals` | `approve_timesheet` |
| jobchange | `list_job_changes` | `approve_job_change`, `reject_job_change` |
| recruitment | `list_requisition_approvals` | `approve_requisition` |
| directory | `search_directory`, `get_employee` | — |
| onboarding | `list_onboarding_cases`, `list_onboarding_steps` | `complete_onboarding_step` |
| offboarding | `list_offboarding`, `list_offboarding_tasks` | `toggle_offboarding_task` |
| profile | `list_profile_change_approvals` | `submit_legal_name_change`, `approve_profile_change` |
| performance | `list_cycles`, `list_reviews` | — |
| analytics | `get_analytics` | — |
| admin | `get_admin_settings`, `get_org_structure`, `get_roles_and_access`, `get_audit_log` | — |

Deferred (stateful multi-field forms and the recruitment candidate pipeline) are listed in `PROGRESS.md`.
