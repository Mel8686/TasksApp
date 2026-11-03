# TasksApp

A small task tracker. React + Vite frontend, Spring Boot 4 backend, PostgreSQL
with Flyway migrations.

## Run it

Prerequisites: Java 21, Node 20+, and a PostgreSQL database named `tasks`.

```powershell
# backend on :8080
cd backend
.\mvnw.cmd spring-boot:run
```

```powershell
# frontend on :5173, proxying /api to the backend
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Flyway creates the schema on first boot; the
database URL and credentials default to `jdbc:postgresql://localhost:5432/tasks`
with `tasks`/`tasks`, and are overridden by `DB_URL`, `DB_USERNAME` and
`DB_PASSWORD`.

## API

| Endpoint | Notes |
|---|---|
| `GET /api/tasks?q=&status=&page=&size=` | paged list, `PageResponse` |
| `POST /api/tasks` | create |
| `GET` / `PUT` / `DELETE /api/tasks/{id}` | read, update, delete |
| `GET /api/tasks/export` | CSV of the current filter |
| `POST /api/tasks/import` | multipart CSV, all-or-nothing |

- `PUT` needs `If-Match: "<version>"` (or `*` to overwrite); a stale version is
  `412`, a missing header `428`.
- Errors are RFC 9457 `ProblemDetail`.
- Validation bounds: `size` 1–50, `page` 0–10000, `q` ≤ 100 chars, `title` ≤ 200,
  imports ≤ 1 MiB and ≤ 1000 rows.

## Checks

```powershell
cd backend;  .\mvnw.cmd test
cd frontend; npm run build
```

## Notes

- There is no authentication. That is the current scope, not an oversight.
- `AGENTS.md` (and `backend/AGENTS.md`, `frontend/AGENTS.md`) holds the working
  conventions; `FRICTION.md` is the build log.
- `.opencode/skills/` vendors the three skills those files reference, MIT
  licensed from their respective publishers.
