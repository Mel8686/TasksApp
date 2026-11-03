# Skill evidence

The raw record behind the summary table in `README.md`. One row per skill per
feature, written when the feature is done — facts only: what it caught, what it
missed, what it cost. Judge the skills every few features; do not try to judge a
skill from one line.

A row that only says "helpful" is noise. The `Missed` and `Cost` columns are what
make the table worth reading, and they are the columns a skill cannot write about
itself.

## `java-springboot`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | nothing new; supplied the `PageResponse`/`Specification` shape | `findAll(null, Pageable)` throws in Data JPA 4 (F-11); advice ordering (F-12) | one 500 and one error-shape drift, found by smoke test |
| 2026-11-03 | CSV export/import | the validation + `ProblemDetail` patterns that the import errors reuse | nothing | one reference read |
| 2026-11-03 | optimistic concurrency | the JPA half: `@Version`, migration discipline | the HTTP half: no `ETag`/`If-Match`/412 guidance anywhere in `rest-api.md` | a missing import, one compile round |

## `vercel-react-best-practices`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | nothing new; aborting superseded fetches and debounce are rules it endorses | nothing | one skill read |
| 2026-11-03 | CSV export/import | nothing | `FormData` must not be sent with a JSON content type — a real bug it has no rule for | 0 |
| 2026-11-03 | optimistic concurrency | nothing | stale-closure capture (`const target = editing`), conflict-state branching — no rule covers either | 0 |

## `owasp-security`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | unbounded `page` → deep `OFFSET` (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | CSV export/import | export claimed to stream but loaded every row (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | optimistic concurrency | nothing new; confirmed strong tag comparison, 428 fail-closed, log context | `DELETE` has no `If-Match`; treated as out of scope because there is no auth boundary to cross | 1 pass, 0 false positives |

## Running tally

- 3 features, 3 security passes, 2 Low findings, both fixed, 0 false positives.
- 2 defects that predate the skills and that their rules exist to prevent:
  entity-as-payload (F-03), free-text status (F-04).
- 4 gaps recorded above. The pattern: skills are strongest on conventions they
  name explicitly, and silent on the seam between two technologies (JPA version
  ↔ HTTP precondition, React state ↔ fetch lifecycle).
