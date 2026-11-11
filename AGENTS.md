# Project instructions

Tasks CRUD app. React + Vite frontend in `frontend/`, Spring Boot 4 backend in
`backend/`, PostgreSQL via Flyway.

## Skills

Load these with the skill tool before starting the work, not after.

| Work | Skill | Origin |
|---|---|---|
| Any frontend change | `vercel-react-best-practices` | Vercel, MIT |
| Any backend change | `java-springboot` | GitHub, MIT |
| Security review, or any change touching auth, input handling, config, dependencies or error responses | `owasp-security` | MIT, OWASP Top 10:2025 + ASVS 5.0 |

Per-directory detail lives in `frontend/AGENTS.md` and `backend/AGENTS.md`.

A feature is not done until it has a row per skill in `SKILLS.md`: what the skill
caught, what it missed, what it cost. Write it when the feature is done, from what
happened, not from what the skill promises. The `README.md` table is the summary;
`SKILLS.md` is the record it summarises.

## API contract today

- `POST`/`PUT` take `TaskRequest`: `title` not blank and at most 200 chars,
  `status` one of `TODO|DOING|DONE` (defaults to `TODO`), optional `dueDate`, and
  `tags` — at most 5, each ≤ 30 chars, lowercased and de-duplicated, no `|`.
  `tags` is a full replacement: omitting it clears the tags. Violations return 400.
- `GET /api/tasks?q=&status=&tag=&archived=&sort=&page=&size=` returns `PageResponse`
  (`content/page/size/total/totalPages`). `size` is 1–50 (default 5), `page` is
  0–10000, `q` ≤ 100 chars, `tag` ≤ 30 chars, `archived` is a boolean (default
  false); violations return 400. `sort` is a whitelist token —
  `newest|title|-title|dueDate|-dueDate`, default `newest` — never a field name,
  so nothing client-controlled reaches a property or an expression; an unknown
  token is a 400. Every order ends with `id` so paging is stable, and `dueDate`
  keeps empty dates last in both directions.
- Every error is an RFC 9457 `ProblemDetail`; 5xx carries an `errorId` that also
  appears in the log, and every 4xx the API raises on purpose is logged.
- A missing id returns 404 for `GET`, `PUT` and `DELETE`.
- `DELETE` is a soft delete: it archives the row (204). Archived rows are 404 for
  `GET`/`PUT`/`DELETE`, and list/export exclude them unless `archived=true`.
  `POST /api/tasks/{id}/restore` returns 200, or 409 when the row is not archived;
  it needs no `If-Match` because it changes no user data.
- `POST /api/tasks/bulk` takes `{ids, operation, status}` — `operation` is
  `ARCHIVE|RESTORE|STATUS`, `ids` is 1–100 distinct-seeming ids, `status` is
  required for `STATUS`. One transaction: any failing id returns 404 (archive /
  status / unknown) or 409 (restore of an active row) and nothing is written.
  Returns `{updated}`. It takes no `If-Match` on purpose: one stale row would
  abort the batch, so the operation wins.
- `GET /api/tasks/export` streams the current filter and order as CSV, including
  `archivedAt` and `tags` (`a|b`); `POST /api/tasks/import` takes a multipart
  `file` (≤ 1 MiB, ≤ 1000 rows) and is all-or-nothing: any bad row rejects the
  whole batch with 400. Import ignores `archivedAt` and creates active rows.
- Every task carries a `version` that increments on each write. `GET
  /api/tasks/{id}` and `PUT /api/tasks/{id}` expose it as a quoted `ETag`.
- `PUT` requires `If-Match`. A stale version returns 412 with the current row as
  `current`; a missing header returns 428; an unparseable one returns 400.
  `If-Match: *` skips the check and overwrites.

Backend is still ahead of the frontend in one place: there is no DTO layer, so
the `Task` type is hand-copied from `Task.java`. Fix it when a feature needs it,
not before. Use cases live in `TaskService`; the controller keeps HTTP only.

## Scope

There is no authentication by design. A security review will therefore flag
A01 Broken Access Control and A07 Authentication Failures on nearly every
endpoint. That is the current scope, not an oversight — raise it as a design
decision before implementing anything.
