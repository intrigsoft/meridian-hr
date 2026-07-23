# Tutorial material

Bulk copy-paste files for the DioscHub tutorial — code you should **copy, not
type**. The tutorial itself lives in the DioscHub docs; it tells you when to copy
each file and where to put it. Small, conceptual code (the bind controller body,
the broker) is written by hand in the tutorial text — only the long mechanical
files live here.

| Directory | Contents | Copied to |
| --- | --- | --- |
| `step-2-embed/` | `assistant-rail.html` (the app-shell rail fragment that mounts `<diosc-chat>` via the hub loader) and `layout-wiring.md` (the two `LayoutAdvice` + `layout.html` edits that mount it) | `src/main/resources/templates/fragments/` (+ the wiring edits) |
| `step-3-bind/` | `DioscBindController.java` + `DioscProperties.java` — the host bind endpoint that hands sessions to the adapter | `src/main/java/com/meridian/hr/diosc/` |
| `step-4-mcp/` | `mcp-adapter/` — the full front-door MCP adapter incl. `auth-broker.ts` (the adapter as audience-bound JWT issuer, MCP spec 2025-11-25) and the 114-tool catalog | repo root (`mcp-adapter/`) |
| `step-5-hub/` | `HUB-WIRING.md` — register the adapter, attach the assistant, roles + approval gating | (hub configuration, not a repo file) |

This directory is excluded from the app build. The authoritative, running version
of every file here is on the [`production`](../../tree/production) branch — if the
two ever disagree, `production` wins.
