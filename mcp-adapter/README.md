# Meridian HR — Front-Door MCP Adapter

An MCP server that lets a DioscHub assistant operate **Meridian HR**, a server-rendered
Thymeleaf app with **no REST API** — the common shape of real legacy line-of-business
systems. It's an **Open/Closed extension**: the app stays untouched; the adapter drives
its existing HTML interface from the outside, the same way a browser does.

## How it works

```
Hub ──MCP (tools/call)──►  this adapter  ──HTTP + HTML──►  Meridian
                           (the front door)
```

Per tool call the adapter:

1. **Session** — logs in as a persona and carries the `meridian_device` cookie in a jar.
   The cookie is never serialized into a tool result, so the model never sees it
   (credential-blind). In production the hub forwards the user's *real* session; the
   adapter holds no credentials of its own.
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
npm run build && npm start          # stdio MCP server
npm run verify                      # end-to-end check against staging
```

Env: `MERIDIAN_PERSONA` (default `priya.nair`), `MERIDIAN_BASE_URL` (default staging).

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
