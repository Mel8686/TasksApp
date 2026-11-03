# TasksApp

A small task tracker, built to exercise an enterprise-shaped CRUD app end to end.

**Stack:** React 19 + Vite 8 + TypeScript 7 (oxlint) · Spring Boot 4.0.0 / Java 21 /
Spring Data JPA 4 · PostgreSQL via Flyway · no authentication, by design.

## For an AI assistant

1. Read `AGENTS.md` next. It holds the API contract; per-directory rules live in
   `backend/AGENTS.md` and `frontend/AGENTS.md` and are injected when you work there.
2. Load the skill the task calls for — see [Skills](#skills). The security skill is
   a **post-change review**, not a pre-coding read.
3. Do not add authentication, a DTO layer, CI, or a dependency without being asked.
   Those are decisions, not missing pieces — see [Not built yet](#not-built-yet).
4. Prove the change with the commands in [Verify](#verify-a-change). A green build
   is not the same as a working app (`FRICTION.md` F-10).

## Run

| Piece | Command | Notes |
|---|---|---|
| Database | — | PostgreSQL, database `tasks`; Flyway creates the schema on boot |
| Backend | `cd backend; .\mvnw.cmd spring-boot:run` | `:8080` |
| Frontend | `cd frontend; npm install; npm run dev` | `:5173`, proxies `/api` to `:8080` |

Config is environment-first: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, with local
fallbacks in `backend/src/main/resources/application.properties`. No `.env` file,
no secrets in the repo.

## Verify a change

```powershell
cd backend;  .\mvnw.cmd test     # context loads, Flyway runs, Hibernate validate agrees
cd frontend; npm run build       # tsc -b, then vite build
```

For an endpoint change, also call it. A `PUT` needs the current version:

```powershell
$v = (Invoke-RestMethod http://localhost:8080/api/tasks/17).version
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/tasks/17 `
  -Headers @{ 'If-Match' = '"' + $v + '"' } `
  -ContentType 'application/json' -Body '{"title":"edited","status":"TODO"}'
```

## API

| Endpoint | Notes |
|---|---|
| `GET /api/tasks?q=&status=&page=&size=` | paged list, `PageResponse` |
| `POST /api/tasks` | create, `201` |
| `GET` / `PUT` / `DELETE /api/tasks/{id}` | read, update, delete; `404` on a missing id |
| `GET /api/tasks/export` | CSV of the current filter |
| `POST /api/tasks/import` | multipart `file`, all-or-nothing |

- `PUT` requires `If-Match: "<version>"`. Stale → `412` with the current row as
  `current`; missing → `428`; unparseable → `400`; `*` overwrites unchecked.
  `GET` and `PUT` return the version as `ETag`.
- Every error is RFC 9457 `ProblemDetail`; a 5xx carries an `errorId` that also
  appears in the log.
- Bounds: `size` 1–50, `page` 0–10000, `q` ≤ 100 chars, `title` ≤ 200 chars,
  imports ≤ 1 MiB and ≤ 1000 rows. Violations are `400`.

## Data model

`task(id, title, status, due_date, created_at, version)`. `status` is
`TODO|DOING|DONE`, constrained in the database. `version` is the concurrency
token: `@Version` on the entity, bumped by every write. Migrations are
`V1`–`V3` and append-only — never edit one that has run.

## Not built yet

No auth or per-user ownership · no DTO layer (the entity is the payload, so new
fields are exposed automatically) · no frontend tests · no CI · no rate limiting ·
no OpenAPI. Each is a deliberate scope cut, recorded in `AGENTS.md`.

## Skills

Three skills are vendored under `.opencode/skills/`. This table is the evidence
record for whether they earn their place — it is why the last two columns matter
more than the first four.

| Skill | Why this one | Key moves | Invoked | Without it | Evidence so far |
|---|---|---|---|---|---|
| **`java-springboot`**<br>GitHub, MIT | Written for an **existing** Boot 3/4 app rather than scaffolding, and it spells out Boot 3 vs 4 deltas — this repo is Boot 4 + Framework 7 | Detects versions and starters before writing code. Routes to five references (data, rest-api, messaging-caching, configuration, aot). Rules for DTOs, `ProblemDetail`, request-param validation, pagination, transactional services. End checklist | `backend/AGENTS.md`: load **before** writing, reviewing or refactoring any backend file; root `AGENTS.md` requires it | The classes of defect it exists to prevent are all in the v0 log: entity-as-payload with no validation (F-03), status as free text (F-04), `204` on a missing id (F-05) | Silent on `findAll(null, pageable)` throwing in JPA 4 (F-11) and on advice ordering (F-12). Both found by running the app, not by reading the skill |
| **`vercel-react-best-practices`**<br>Vercel, MIT v1.0.0 | Vendor-authored, 70 rules ranked by impact, one file per rule. The SPA subset (`async-`, `bundle-`, `client-`, `rerender-`, `rendering-`, `js-`) applies; `server-*`, RSC and hydration do not | Eight categories with priority order. Each rule has an incorrect/correct pair. Covers waterfalls, bundle size, fetch dedup, state shape, re-render cost | `frontend/AGENTS.md`: load **before** any change in `frontend/` | No observed defect. The rules it does cover were honoured: aborting superseded searches, debounced input, functional `setState`, ternary instead of `&&` | Nothing attributable yet. `App.tsx` is ~350 lines in one component, below the size where its performance rules bite — and it has no rule about that |
| **`owasp-security`**<br>MIT, OWASP Top 10:2025 + ASVS 5.0 | The only one that ships a **review procedure** (5 steps, triage rubric, fixed report format) instead of a rule list, and it tracks the 2025/2026 editions | Map entry points and trust boundaries → load only the language/config references needed → sweep the checklist → triage every candidate against a false-positive rubric → report severity, location, path, confidence | **After** the last edit, over the files just changed: `## Security` in `backend/AGENTS.md` and `frontend/AGENTS.md`. Domain overlaps with the other two on purpose — a reviewer that agrees with them is doing its job | Two live defects would have shipped: an unbounded `page` that forces a deep `OFFSET` scan, and an export that claimed to stream while loading the whole table | 3 passes over 3 features, 2 Low findings, both fixed; 0 false positives. Weakness: the trigger is a prompt, so nothing enforces it — no CI gate |

**How the trigger works.** Root `AGENTS.md` maps work type to skill. Each
directory's `AGENTS.md` is injected when files there are edited, so the rule
arrives at the moment of work. Frontend and backend skills are read *before*
coding; the security skill runs *after*, as its own pass. Nothing runs on
`git commit` — the gates are the two commands above.

## Layout

| Path | What lives there |
|---|---|
| `backend/src/main/java/com/dsk/tasks/` | controller, entity, import/export, error advice, security headers |
| `backend/src/main/resources/db/migration/` | `V1`–`V3`, append-only |
| `frontend/src/App.tsx` | all UI state: fetch, filters, edit, import |
| `.opencode/skills/` | the three vendored skills above |
| `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md` | rules, injected per directory |
| `FRICTION.md` | build log, F-01–F-14, the raw material for the table above |
