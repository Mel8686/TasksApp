# Status

Loaded as an instruction by `opencode.json`, so it is in context from the first
message of every session. Rewrite it at the end of a session, delete lines that
are resolved, and keep it under about 15 lines.

**Last updated: 2026-11-03**

## Where things stand

- **Done, for the agreed scope:** CRUD, search + status filter + pagination, CSV
  export/import, optimistic concurrency (`ETag`/`If-Match`; 412 stale, 428
  missing, `*` forces).
- **Owed:** the written assessment of the three skills. Evidence is captured per
  feature in `SKILLS.md` from now on, so the verdict waits for a few more
  features; nothing is half-finished.
- **Open decisions, do not change unasked:** no auth or ownership, no DTO layer,
  no CI gate, no new dependencies.
- **Known oddity:** the dev database has 14 rows; ids 24–30 duplicate 11–17 and
  were inserted by something outside this repo on 2026-11-03 at 17:29 local. Left
  in place deliberately — ask before deleting.
- **Repo:** `github.com/Mel8686/TasksApp`, `main` tracks `origin/main`.
