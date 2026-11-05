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
| 2026-11-05 | archive + restore | the write path: archive is another `@Version`-bumped save, so the restore-vs-PUT race closes itself | soft delete entirely: `data.md` has no `@SQLRestriction`, `@Where`, `@Filter`, explicit-predicate or partial-index guidance (grep: 0 matches) | one grep to prove the gap; the design (named predicate over an annotation) made unaided |

## `vercel-react-best-practices`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | nothing new; aborting superseded fetches and debounce are rules it endorses | nothing | one skill read |
| 2026-11-03 | CSV export/import | nothing | `FormData` must not be sent with a JSON content type — a real bug it has no rule for | 0 |
| 2026-11-03 | optimistic concurrency | nothing | stale-closure capture (`const target = editing`), conflict-state branching — no rule covers either | 0 |
| 2026-11-05 | archive + restore | nothing new; kept the abort/debounce effect and derived the view from state, no effect added | state coherence across a view switch: resetting page and closing the editor when the row set changes is not in its rules; it is a perf skill, and this was correctness | 0 |

## `owasp-security`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | unbounded `page` → deep `OFFSET` (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | CSV export/import | export claimed to stream but loaded every row (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | optimistic concurrency | nothing new; confirmed strong tag comparison, 428 fail-closed, log context | `DELETE` has no `If-Match`; treated as out of scope because there is no auth boundary to cross | 1 pass, 0 false positives |
| 2026-11-05 | archive + restore | confirmed the archive cannot leak into the default list or export, and an invalid `archived` value is a 400 `ProblemDetail` with no internals | soft delete as a retention/erasure conflict (A04/A09-adjacent): nothing says a review should ask whether "deleted" data can ever be removed; and no rule for judging a precondition-less state transition that cannot overwrite user data | 1 pass, 2 live probes (`archived=maybe`, `archived=1`), 0 findings |

## Running tally

- 4 features, 4 security passes, 2 Low findings, both fixed, 0 false positives.
- 2 defects that predate the skills and that their rules exist to prevent:
  entity-as-payload (F-03), free-text status (F-04).
- 6 gaps recorded above. The pattern: skills are strongest on conventions they
  name explicitly, and silent on the seam between two technologies (JPA version
  ↔ HTTP precondition, React state ↔ fetch lifecycle), and on a whole feature
  the vocabulary predates (soft delete).
