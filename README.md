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
| `GET /api/tasks?q=&status=&archived=&page=&size=` | paged list, `PageResponse` |
| `POST /api/tasks` | create, `201` |
| `GET` / `PUT /api/tasks/{id}` | read, update; `404` on a missing id |
| `DELETE /api/tasks/{id}` | archive (soft delete), `204` |
| `POST /api/tasks/{id}/restore` | un-archive, `200`; `409` if not archived |
| `GET /api/tasks/export` | CSV of the current filter, with `archivedAt` |
| `POST /api/tasks/import` | multipart `file`, all-or-nothing |

- `PUT` requires `If-Match: "<version>"`. Stale → `412` with the current row as
  `current`; missing → `428`; unparseable → `400`; `*` overwrites unchecked. `GET`
  and `PUT` return the version as `ETag`.
- Errors are RFC 9457 `ProblemDetail`; a 5xx carries an `errorId` that also appears
  in the log.
- Bounds: `size` 1–50, `page` 0–10000, `q` ≤ 100, `title` ≤ 200, imports ≤ 1 MiB
  and ≤ 1000 rows. Violations are `400`.
- Archived rows are hidden from `GET`/`PUT`/`DELETE` and from list/export unless
  `archived=true`. Import always creates active rows.

## Data model

`task(id, title, status, due_date, created_at, archived_at, version)`. `status` is
`TODO|DOING|DONE`, constrained in the database. `archived_at` is the soft delete:
null means active. `version` is the concurrency token, bumped by every write.
Migrations `V1`–`V4` are append-only.

## Not built yet

No auth or ownership · no DTO layer, so new entity fields are exposed automatically ·
no frontend tests · no CI · no rate limiting · no OpenAPI. Scope cuts, not gaps.

## Skills

Three, vendored under `.opencode/skills/` as reviewed, pinned copies — nothing is
fetched at run time, and no skill runs a script here.

| Skill | Popularity & ecosystem context | Why this skill? | What it does (key features) | Invoked | Evidence so far |
|---|---|---|---|---|---|
| `vercel-react-best-practices` | **The reference standard.** Developed and maintained by Vercel Labs on GitHub (`vercel-labs/agent-skills`). ~685k installs, 30k+★; the next React-guidance skill trails at ~13k | Optimises frontend performance by preventing the rendering traps, state leaks and bloated bundles typical of raw LLM code | • Eliminates async data waterfalls through component-level fetching boundaries • Fixes rendering inefficiencies — conditional UI flicker, unnecessary re-renders • Keeps bundles small: dynamic imports, barrel-file avoidance, analyzable paths | Before any frontend edit, via `frontend/AGENTS.md` | Nothing attributable yet — `App.tsx` is too small for its perf rules, and it has no rule for the bug class that bit: state across a view switch |
| `java-springboot` | **Fragmented field** — no first-party Spring skill exists. Sourced from the community, unaffiliated with Spring, and **not** the `awesome-copilot` file of the same name (~20k, no code samples) | Prevents architectural decay: stops an LLM leaking business logic into the web layer, or reaching for a deprecated dependency | • Pushes logic out of controllers — services own the transaction • Protects persistence: transaction boundaries, lazy-loading and N+1 traps • Routes to one of five references instead of one wall of rules | Before any backend edit, via `backend/AGENTS.md` | Without it, v0 shipped entity-as-payload, free-text status, and a `204` on a miss (F-03–F-05). Silent on `findAll(null)` (F-11) and advice ordering (F-12) |
| `owasp-security` | **OWASP-derived, not OWASP-published.** Aligned with the OWASP Agentic Skills Top 10 (ASI01–ASI10), but the copy is a single 14KB file with no author, licence or version in it | Turns a code pass into a defensive audit, catching vulnerabilities before the files hit a repository scanner | • Hardens surfaces against injection and broken access control (A05, A01 — BOLA included) • Five steps: entry points → references → checklist → triage → report • Reports severity, path and confidence | After the last edit, over the changed files, via `## Security` in both `AGENTS.md` files | 3 passes, 2 Low findings, both fixed, 0 false positives. Caught an unbounded `page` (deep `OFFSET`) and an export that claimed to stream but loaded every row |

**Rejected:** `awesome-copilot`'s `java-springboot` — same name, not the same file
(~850 tokens, no code) · Jeffallan's Spring/React pair — layer-by-layer DTO and
Next.js/RSC assumptions · rrezart's narrow set — closest fit, but 3–5 skills from a
single maintainer · `react-doctor` — a CLI gate that needs a dependency.

**Known gaps:** no skill here covers `If-Match`/`ETag` preconditions or soft-delete
retention; both were written from scratch. Nothing enforces the trigger either — the
rule lists are read because `AGENTS.md` says so, and the only gates are the two commands
above. Per-feature evidence: `SKILLS.md`. Build history: `FRICTION.md`.

## Layout

| Path | Contents |
|---|---|
| `backend/src/main/java/com/dsk/tasks/` | controller, entity, import/export, error advice, security headers |
| `backend/src/main/resources/db/migration/` | `V1`–`V4`, append-only |
| `frontend/src/App.tsx` | all UI state: fetch, filters, edit, archive view, import |
| `.opencode/skills/` | the three skills above |
| `.opencode/commands/handoff.md` | `/handoff` — update `STATUS.md`, verify, commit, push |
| `AGENTS.md`, `*/AGENTS.md` | rules, injected per directory |
| `STATUS.md` | state of play, loaded as an instruction via `opencode.json` |
| `SKILLS.md` | per-feature evidence behind the skills table |
| `FRICTION.md` | build log, F-01–F-14, the source for the evidence column |
