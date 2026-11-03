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

## API contract today

- `POST`/`PUT` take `TaskRequest`: `title` not blank and at most 200 chars,
  `status` one of `TODO|DOING|DONE` (defaults to `TODO`), optional `dueDate`.
  Violations return 400.
- `GET /api/tasks?q=&status=&page=&size=` returns `PageResponse`
  (`content/page/size/total/totalPages`). `size` is 1–50 (default 5), `page` is
  0–10000, `q` ≤ 100 chars; violations return 400.
- Every error is an RFC 9457 `ProblemDetail`; 5xx carries an `errorId` that also
  appears in the log.
- A missing id returns 404 for both `GET` and `DELETE`.
- `GET /api/tasks/export` streams the current filter as CSV; `POST
  /api/tasks/import` takes a multipart `file` (≤ 1 MiB, ≤ 1000 rows) and is
  all-or-nothing: any bad row rejects the whole batch with 400.
- Every task carries a `version` that increments on each write. `GET
  /api/tasks/{id}` and `PUT /api/tasks/{id}` expose it as a quoted `ETag`.
- `PUT` requires `If-Match`. A stale version returns 412 with the current row as
  `current`; a missing header returns 428; an unparseable one returns 400.
  `If-Match: *` skips the check and overwrites.

Backend is still ahead of the frontend in one place: there is no DTO layer, so
the `Task` type is hand-copied from `Task.java`. Fix it when a feature needs it,
not before.

## Scope

There is no authentication by design. A security review will therefore flag
A01 Broken Access Control and A07 Authentication Failures on nearly every
endpoint. That is the current scope, not an oversight — raise it as a design
decision before implementing anything.
