# Meridian HR

> **You are on `main` — the tutorial starting point.** This branch is a complete,
> working HR application with **no AI integration**. The DioscHub tutorial walks
> you from here to an embedded assistant that operates the app through its own
> HTML front door, under the signed-in user's exact permissions.
>
> - **Finished result**: the [`production`](../../tree/production) branch
>   (assistant rail, the BYOA bind endpoint, and the front-door MCP adapter).
> - **Tutorial**: DioscHub docs → *Assistant-enable a legacy app* (link TBD).
> - Bulk copy-paste files for the tutorial steps live in [`tutorial/`](./tutorial/).

Meridian HR is a server-rendered **Spring Boot + Thymeleaf** HR platform — directory,
leave, approvals, time, onboarding/offboarding, job changes, performance,
recruitment, analytics, and admin — with **role-based access control** enforced
server-side on every action and a real audit trail. It is deliberately shaped like
a **legacy line-of-business app: no REST API**, just server-rendered HTML and form
posts. That shape is the point of the tutorial — it shows how a DioscHub assistant
can drive an app that was never built with an API, through the same front door a
browser uses.

## How data works (no database)

State is held in memory, isolated **per device**: each browser gets an opaque
`meridian_device` cookie mapped to its own deep-clone of the seed workspace. A
restart resets every sandbox to seed — intentional for a public demo.

## Stack

- **Spring Boot 3.3** (Java 21) + **Thymeleaf** server-rendered views
- In-memory per-device workspace (the demo "database")
- Plain CSS design system ported from the design handoff

## Quickstart

```bash
./gradlew bootRun          # http://localhost:8080
```

Sign in as one of the sample personas (passwordless demo login) — note how the left
nav and every page adapt to that role's permissions.

```bash
./gradlew build            # compile + tests
```

## Project layout

```
src/main/java/com/meridian/hr/
  session/          device-cookie filter + SessionContext (the auth chokepoint)
  security/         AccessPolicy + Permission catalog (RBAC)
  domain/           the HR domain model
  web/              LayoutAdvice (app-shell model) + MVC controllers
  <feature>/        directory, leave, approvals, onboarding, … per-feature slices
  workspace/        per-device in-memory store + seed
src/main/resources/templates/   Thymeleaf views + fragments
tutorial/                        copy-paste material for the DioscHub tutorial
```

## What the tutorial adds

Starting from this branch, the DioscHub tutorial builds (in order): the embedded
**assistant rail** in the app shell, a **BYOA bind endpoint** (`/api/diosc/bind`),
and a **front-door MCP adapter** (`mcp-adapter/`) that drives Meridian's HTML the
way a browser does — reading pages, submitting the same forms — as a **token
broker** that issues audience-bound JWTs per the MCP authorization spec
(2025-11-25). The finished version is on the
[`production`](../../tree/production) branch; `git diff main..production` is
exactly the integration.

## License

MIT © IntrigSoft
