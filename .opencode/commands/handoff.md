---
description: Record the state of play in STATUS.md, verify, commit and push
---

Close out this session so a fresh one can pick up without being re-briefed.

Working tree and recent history:

!`git status --short && git log --oneline -3`

Do this, in order:

1. Rewrite `STATUS.md` for the **next** session, not this one. Update the date,
   keep it under about 15 lines, and cover: done, owed, decisions that must not
   be reopened, known oddities, repo and branch. Delete lines that are resolved —
   a stale status is worse than none.
2. If backend or frontend source changed this session, run
   `cd backend; .\mvnw.cmd test` and `cd frontend; npm run build`. Record the
   result instead of asserting it.
3. Add anything the next session would otherwise have to ask about: a decision
   and its reason, a workaround, a task deliberately left undone, data that looks
   wrong.
4. Commit `STATUS.md` plus any outstanding source changes in one commit whose
   message says what changed, then push to `origin main`.
5. Reply in three lines: what was recorded, what was verified, what is next.

Do not paste conversation history into `STATUS.md`, and never commit logs,
`target/`, `node_modules/` or `dist/`.
