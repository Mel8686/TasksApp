# TasksApp

A small task tracker: React 19 + Vite 8 + TypeScript, Spring Boot 4.0.0 on Java 21,
PostgreSQL via Flyway. No authentication, by design.

## For AI agents

Rules live in `AGENTS.md`, plus `backend/AGENTS.md` and `frontend/AGENTS.md`, which
are injected when a file in those directories is touched. Load the skill the task
calls for — see [Skills](#skills); the security skill runs *after* the change, not
before it.

Do not add auth, a DTO layer, CI or a dependency without being asked. A green build
is not a working app (`FRICTION.md` F-10).

## Run

| Piece | Command | Notes |
|---|---|---|
| Database | — | PostgreSQL, database `tasks`; Flyway creates the schema on boot |
| Backend | `cd backend; .\mvnw.cmd spring-boot:run` | `:8080` |
| Frontend | `cd frontend; npm install; npm run dev` | `:5173`, proxies `/api` to `:8080` |

Config is environment-first: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, with local
fallbacks in `application.properties`. No `.env`, no secrets in the repo.

## API

| Endpoint | Notes |
|---|---|
| `GET /api/tasks?q=&status=&tag=&archived=&page=&size=` | paged list, `PageResponse` |
| `POST /api/tasks` | create, `201` |
| `GET` / `PUT /api/tasks/{id}` | read, update; `404` on a missing id |
| `DELETE /api/tasks/{id}` | archive (soft delete), `204` |
| `POST /api/tasks/{id}/restore` | un-archive, `200`; `409` if not archived |
| `POST /api/tasks/bulk` | archive / restore / set status for 1–100 ids, one transaction |
| `GET /api/tasks/export` | CSV of the current filter, with `archivedAt` and `tags` |
| `POST /api/tasks/import` | multipart `file`, all-or-nothing |

- `PUT` requires `If-Match: "<version>"`. Stale → `412` with the current row as
  `current`; missing → `428`; unparseable → `400`; `*` overwrites unchecked. `GET`
  and `PUT` return the version as `ETag`.
- Errors are RFC 9457 `ProblemDetail`; a 5xx carries an `errorId` that also appears
  in the log.
- Bounds: `size` 1–50, `page` 0–10000, `q` ≤ 100, `tag` ≤ 30, `title` ≤ 200,
  `tags` ≤ 5 × 30, bulk ≤ 100 ids, imports ≤ 1 MiB and ≤ 1000 rows. Violations are
  `400`.
- Archived rows are hidden from `GET`/`PUT`/`DELETE` and from list/export unless
  `archived=true`. Import always creates active rows.

## Data model

`task(id, title, status, due_date, created_at, archived_at, version)`. `status` is
`TODO|DOING|DONE`, constrained in the database. `archived_at` is the soft delete:
null means active. `version` is the concurrency token, bumped by every write.
`tag(id, name)` holds one row per distinct lowercase name; `task_tag(task_id,
tag_id)` joins them. Migrations `V1`–`V5` are append-only.

## Skills

Three, vendored under `.opencode/skills/` as reviewed, pinned copies — nothing is
fetched at run time, and no skill runs a script here. Per-feature evidence: `SKILLS.md`.

| Skill | Popularity & Why this skill? | What it does (key features) | Invoked | Evidence so far |
|---|---|---|---|---|
| `vercel-react-best-practices` | **The reference standard.** Vercel Labs, first-party (`vercel-labs/agent-skills`) — ~685k installs, 30k+★, the next React-guidance skill trails at ~13k. Optimises frontend performance by preventing the rendering traps, state leaks and bloated bundles typical of raw LLM code | • Eliminates async data waterfalls through component-level fetching boundaries • Fixes rendering inefficiencies — conditional UI flicker, unnecessary re-renders • Keeps bundles small: dynamic imports, barrel-file avoidance, analyzable paths | Before any frontend edit, via `frontend/AGENTS.md` | 0 defects caught in 6 features — this app has almost no performance surface to test it on: one 588-line screen, 5 components, 3 effects, ≤ 50 rows, 2 source files. It endorsed the fetch path we already had (abort + debounce, derived state, `Set` selection) and vetoed memoising a 5-row table. Blind recall test: 6/6 of its own rules, 5/6 without it. Re-test when the UI is heavy enough to need it |
| `java-springboot` | **Enterprise Open Baseline.** Community-backed Spring setups (MIT licensed), used as baseline architecture instructions across automated Java pipelines. Prevents architectural decay: stops an LLM leaking business logic into the web layer, or reaching for a deprecated dependency | • Pushes logic out of controllers — services own the transaction • Protects persistence: transaction boundaries, lazy-loading and N+1 traps • Routes to one of five references instead of one wall of rules | Before any backend edit, via `backend/AGENTS.md` | Caught three real defects v0 shipped without it — entity-as-payload, free-text status, a `204` on a miss (F-03–F-05) — and delivered one measurable save on tags: `@BatchSize(50)` plus an explicit touch instead of `@EntityGraph` on a `Page`, so the list never pages in memory. It also confirmed the transaction boundary `TaskService` needed for bulk |
| `owasp-security` | **The Global Enterprise Mandate.** Aligned with the OWASP Agentic Skills Top 10 (AST10) and maintained under the official OWASP Foundation repository. Turns a code pass into a defensive audit, catching vulnerabilities before the files hit a repository scanner | • Hardens surfaces against injection and broken access control (A05, A01 — BOLA included) • Five steps: entry points → references → checklist → triage → report • Reports severity, path and confidence | After the last edit, over the changed files, via `## Security` in both `AGENTS.md` files | 6 passes, 3 findings, all real, all fixed, 0 false positives: an unbounded page (deep `OFFSET`), an export that loaded every row while claiming to stream, and hand-raised 4xx that logged nothing (A09). It also proved the tag and bulk inputs bounded at the boundary, with no mass assignment |


