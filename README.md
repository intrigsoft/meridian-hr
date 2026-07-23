# Meridian HR — with DioscHub assistant

> **You are on `production` — the finished integration.** This branch is the
> complete product: the HR app + an embedded DioscHub assistant that drives it
> through its own HTML front door + the token-broker MCP adapter. CI runs from
> here. The tutorial *starting point* (the same app with no AI integration) is
> the [`main`](../../tree/main) branch.
>
> Requires DioscHub ≥ 0.1.1-rc.23.

Meridian HR is a server-rendered **Spring Boot + Thymeleaf** HR platform with
server-enforced RBAC and an audit trail, deliberately shaped like a **legacy
line-of-business app: no REST API**. The DioscHub integration shows how an
assistant can operate exactly that kind of app — through the same HTML front door
a browser uses, under the signed-in user's real permissions.

## Integration surface

The **entire** DioscHub integration is the delta between this branch and
[`main`](../../tree/main) — verify with `git diff main..production --stat`:

| What | Files |
| --- | --- |
| Assistant rail (kit embed via hub loader) | `templates/fragments/assistant-rail.html` + its `LayoutAdvice` model attribute and `layout.html` mount |
| BYOA bind route (app → adapter broker) | `src/main/java/com/meridian/hr/diosc/DioscBindController.java` + `DioscProperties.java` |
| Front-door MCP adapter + token broker | `mcp-adapter/` — drives Meridian's HTML; `auth-broker.ts` issues audience-bound JWTs and exchanges them per tool call (MCP spec 2025-11-25) |
| Config | `meridian.diosc.*` in `application.yml` |

Nothing else in the app changes.

## Run

```bash
./gradlew bootRun                                  # the HR app on :8080
cd mcp-adapter && npm install && npm run mcp:http  # the adapter on :5175
```

See [`mcp-adapter/README.md`](./mcp-adapter/README.md) for the broker flow, the
114-tool catalog, and how to register the adapter in the DioscHub admin portal.

## License

MIT © IntrigSoft
