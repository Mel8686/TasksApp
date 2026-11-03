# TasksApp

A small task tracker: React 19 + Vite 8 + TypeScript, Spring Boot 4.0.0 on Java 21,
PostgreSQL via Flyway. No authentication, by design.

## For AI agents

Rules live in `AGENTS.md`, plus `backend/AGENTS.md` and `frontend/AGENTS.md`, which
are injected when a file in those directories is touched. Load the skill the task
calls for — see [Skills](#skills); the security skill runs *after* the change, not
before it.

Do not add auth, a DTO layer, CI or a dependency without being asked
([Not built yet](#not-built-yet)). A green build is not a working app (`FRICTION.md`
F-10).

## Run

| Piece | Command | Notes |
|---|---|---|
| Database | — | PostgreSQL, database `tasks`; Flyway creates the schema on boot |
| Backend | `cd backend; .\mvnw.cmd spring-boot:run` | `:8080` |
| Frontend | `cd frontend; npm install; npm run dev` | `:5173`, proxies `/api` to `:8080` |

Config is environment-first: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, with local
fallbacks in `application.properties`. No `.env`, no secrets in the repo.

## Verify

```powershell
cd backend;  .\mvnw.cmd test     # context loads, Flyway runs, Hibernate validate agrees
cd frontend; npm run build       # tsc -b, then vite build
```

Neither command calls the API. After an endpoint change, call it too — a `PUT`
needs `If-Match: "<version>"`, per [API](#api).

## API

| Endpoint | Notes |
|---|---|
| `GET /api/tasks?q=&status=&page=&size=` | paged list, `PageResponse` |
| `POST /api/tasks` | create, `201` |
| `GET` / `PUT` / `DELETE /api/tasks/{id}` | read, update, delete; `404` on a missing id |
| `GET /api/tasks/export` | CSV of the current filter |
| `POST /api/tasks/import` | multipart `file`, all-or-nothing |

- `PUT` requires `If-Match: "<version>"`. Stale → `412` with the current row as
  `current`; missing → `428`; unparseable → `400`; `*` overwrites unchecked. `GET`
  and `PUT` return the version as `ETag`.
- Errors are RFC 9457 `ProblemDetail`; a 5xx carries an `errorId` that also appears
  in the log.
- Bounds: `size` 1–50, `page` 0–10000, `q` ≤ 100, `title` ≤ 200, imports ≤ 1 MiB
  and ≤ 1000 rows. Violations are `400`.

## Data model

`task(id, title, status, due_date, created_at, version)`. `status` is
`TODO|DOING|DONE`, constrained in the database. `version` is the concurrency token,
bumped by every write. Migrations `V1`–`V3` are append-only.

## Not built yet

No auth or ownership · no DTO layer, so new entity fields are exposed automatically ·
no frontend tests · no CI · no rate limiting · no OpenAPI. Scope cuts, not gaps.

## Skills

Three skills are vendored under `.opencode/skills/`. The last two columns are the
evidence record for whether they earn their place.

| Skill | Why this one | Key moves | Invoked | Without it | Evidence so far |
|---|---|---|---|---|---|
| `java-springboot`<br>GitHub, MIT | Written for an **existing** Boot 3/4 app, and names the Boot 3 vs 4 deltas this repo straddles | Detect versions → route to one of five references → rules for DTOs, `ProblemDetail`, pagination, test slices | Before any backend edit, via `backend/AGENTS.md` | v0 shipped entity-as-payload (F-03), free-text status (F-04), `204` on a missing id (F-05) | Silent on JPA 4's `findAll(null)` (F-11) and on advice ordering (F-12); both found by running the app |
| `vercel-react-best-practices`<br>Vercel, MIT | Vendor-authored, 70 impact-ranked rules; the SPA subset applies | Eight categories, each with a wrong/right pair: waterfalls, bundle, fetch, re-render | Before any frontend edit, via `frontend/AGENTS.md` | No observed defect; honoured aborting stale searches, debounce, functional `setState`, ternary over `&&` | Nothing attributable — `App.tsx` is too small for its perf rules, and it has no rule about that |
| `owasp-security`<br>MIT, Top 10:2025 + ASVS 5.0 | The only one that is a **review procedure** (5 steps, triage rubric, report format), not a rule list | Map entry points → load needed refs → sweep checklist → triage → report severity, path, confidence | After the last edit, over the changed files, via `## Security` in both `AGENTS.md` files | Two defects would have shipped: an unbounded `page` (deep `OFFSET`), and an export that claimed to stream but loaded every row | 3 passes, 2 Low findings, both fixed, 0 false positives; nothing enforces the trigger |

Frontend and backend skills are read before coding; security runs after, as its own
pass. Nothing runs on `git commit` — the only gates are the two commands above.

## Layout

| Path | Contents |
|---|---|
| `backend/src/main/java/com/dsk/tasks/` | controller, entity, import/export, error advice, security headers |
| `backend/src/main/resources/db/migration/` | `V1`–`V3`, append-only |
| `frontend/src/App.tsx` | all UI state: fetch, filters, edit, import |
| `.opencode/skills/` | the three skills above |
| `AGENTS.md`, `*/AGENTS.md` | rules, injected per directory |
| `FRICTION.md` | build log, F-01–F-14, the source for the evidence column |
