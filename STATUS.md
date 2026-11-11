# Status

Loaded as an instruction by `opencode.json`, so it is in context from the first
message of every session. Rewrite it at the end of a session, delete lines that
are resolved, and keep it under about 15 lines.

**Last updated: 2026-11-11**

## Where things stand

- **Done, for the agreed scope:** CRUD, search + status filter + pagination, CSV
  export/import, optimistic concurrency, archive + restore, tags, bulk, sort
  (whitelist token, `id` tie-breaker), inline title edit (`PATCH`, optimistic,
  rollback on 412). On `main`, **not pushed**: `3b7253f`, `7e4e734`.
- **Next, not yet agreed:** a heavier frontend view. It is the only thing that
  gives `vercel-react-best-practices` a real surface — the app is one component.
- **Owed:** the written verdict on the three skills.
- **Open decisions, do not change unasked:** no auth or ownership, no DTO layer,
  no CI gate, no new dependencies; bulk takes no `If-Match`; `PATCH` is title-only
  by design (a general patch needs absent-vs-null, which nothing here supplies).
- **Known oddity:** the dev database has 21 rows; ids 24–37 duplicate 11–17 and
  were inserted outside this repo on 2026-11-03. Left in place — ask before deleting.
