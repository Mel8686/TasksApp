# Status

Loaded as an instruction by `opencode.json`, so it is in context from the first
message of every session. Rewrite it at the end of a session, delete lines that
are resolved, and keep it under about 15 lines.

**Last updated: 2026-11-05**

## Where things stand

- **Done, for the agreed scope:** CRUD, search + status filter + pagination, CSV
  export/import, optimistic concurrency, archive + restore (`DELETE` archives;
  `?archived=true`; `POST /{id}/restore`, 409 if not archived).
- **Next, already agreed:** tags — `tag` + join table, max 5 of ≤ 30 chars,
  `?tag=` filter, CSV as `a|b`.
- **Owed:** the written assessment of the three skills. Evidence is captured per
  feature in `SKILLS.md`; the verdict waits for a few more features.
- **Open decisions, do not change unasked:** no auth or ownership, no DTO layer,
  no CI gate, no new dependencies.
- **Known oddity:** the dev database has 21 rows; ids 24–30 and 31–37 duplicate
  11–17 and were inserted outside this repo on 2026-11-03 at 17:29 and 17:38.
  Left in place deliberately — ask before deleting.
- **Repo:** `github.com/Mel8686/TasksApp`, `main` tracks `origin/main`.
