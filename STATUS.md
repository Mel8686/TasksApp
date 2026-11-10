# Status

Loaded as an instruction by `opencode.json`, so it is in context from the first
message of every session. Rewrite it at the end of a session, delete lines that
are resolved, and keep it under about 15 lines.

**Last updated: 2026-11-10**

## Where things stand

- **Done, for the agreed scope:** CRUD, search + status filter + pagination, CSV
  export/import, optimistic concurrency, archive + restore, tags (`?tag=`, chips,
  CSV as `a|b`), bulk archive/restore/status (≤ 100 ids, one transaction).
- **Next, not yet agreed:** a sort control (`sort=title|-dueDate`) — small, and the
  best remaining security surface: a whitelist against sort injection.
- **Owed:** the written assessment of the three skills. `SKILLS.md` now has a row
  per skill per feature across six features; the verdict still waits.
- **Open decisions, do not change unasked:** no auth or ownership, no DTO layer,
  no CI gate, no new dependencies. Bulk takes no `If-Match`, documented in
  `AGENTS.md`.
- **Known oddity:** the dev database has 21 rows; ids 24–37 duplicate 11–17 and
  were inserted outside this repo on 2026-11-03. Left in place — ask before deleting.
- **Repo:** `github.com/Mel8686/TasksApp`; tags + bulk are on `feature/tags-and-bulk`.
