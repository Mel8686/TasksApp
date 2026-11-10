# Status

Loaded as an instruction by `opencode.json`, so it is in context from the first
message of every session. Rewrite it at the end of a session, delete lines that
are resolved, and keep it under about 15 lines.

**Last updated: 2026-11-10**

## Where things stand

- **Done, for the agreed scope:** CRUD, search + status filter + pagination, CSV
  export/import, optimistic concurrency, archive + restore, tags (`?tag=`, chips,
  CSV as `a|b`), bulk archive/restore/status (≤ 100 ids, one transaction). All on
  `main`, pushed at `5484b2f`; `feature/tags-and-bulk` is local-only and identical.
- **Next, not yet agreed:** a sort control (`sort=title|-dueDate`) — small, and the
  best remaining security surface: a whitelist against sort injection.
- **Owed:** the written verdict on the three skills. React is settled as far as a
  recall test can settle it — accurate on its own rules, not separable from a
  reviewer without it (tests A and B in `SKILLS.md`); the other two still wait.
- **Open decisions, do not change unasked:** no auth or ownership, no DTO layer,
  no CI gate, no new dependencies. Bulk takes no `If-Match`, documented in
  `AGENTS.md`.
- **Known oddity:** the dev database has 21 rows; ids 24–37 duplicate 11–17 and
  were inserted outside this repo on 2026-11-03. Left in place — ask before deleting.
- **Repo:** `github.com/Mel8686/TasksApp`; `main` is the line, `feature/tags-and-bulk`
  is a local remnant of it.
