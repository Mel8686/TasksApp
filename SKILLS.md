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
| 2026-11-10 | tags | the shape of the read path: `data.md` says never paginate a collection `JOIN FETCH` (in-memory paging), and `open-in-view=false` means load before the method returns — so `@BatchSize(50)` plus an explicit touch, one extra query per page, instead of `@EntityGraph` with `Page` | find-or-create with a unique index: nothing says how to resolve a name to a row without a race, and two concurrent creates of the same tag are a 500 on the constraint; also nothing about validation that spans a whole collection | one `data.md` read, one compile/test round |
| 2026-11-10 | bulk actions | confirmed the transaction boundary the feature was chosen to force: `@Transactional` on the use-case boundary, so 100 ids commit or roll back together — the gap the four archive reviewers flagged, though the plan to add a service layer predated the read | bulk semantics are all ours: all-or-nothing, per-id 404 vs 409, and dropping `If-Match` for a batch. It also says "services return DTOs", which this repo forbids, so the entities are initialized in the transaction and returned anyway | 1 service class, 0 defects, 0 queries wasted |

## `vercel-react-best-practices`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | nothing new; aborting superseded fetches and debounce are rules it endorses | nothing | one skill read |
| 2026-11-03 | CSV export/import | nothing | `FormData` must not be sent with a JSON content type — a real bug it has no rule for | 0 |
| 2026-11-03 | optimistic concurrency | nothing | stale-closure capture (`const target = editing`), conflict-state branching — no rule covers either | 0 |
| 2026-11-05 | archive + restore | nothing new; kept the abort/debounce effect and derived the view from state, no effect added | state coherence across a view switch: resetting page and closing the editor when the row set changes is not in its rules; it is a perf skill, and this was correctness | 0 |
| 2026-11-10 | tags | endorsed reusing the debounced + aborted fetch effect for the new `tag` parameter, and `rerender-derived-state` / `rendering-conditional-render` shaped the chips and the empty state (ternaries, nothing stored twice) | chip-click-as-filter is UI state design it has no rule for; also silent that a selection must be cleared when its row leaves the view — correctness again, not performance | 0 |
| 2026-11-10 | bulk actions | `js-set-map-lookups` (the selection is a `Set`, O(1) per row) and `rerender-functional-setstate` on every selection write, which is what keeps the page checkbox honest | the "did every selected row just leave this page?" step-back is the same bug class as archive's; no rule. And `rerender-memo` had to be declined per `rerender-simple-expression-in-memo` — the skill working as intended, by saying no | 0 |

## `owasp-security`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | unbounded `page` → deep `OFFSET` (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | CSV export/import | export claimed to stream but loaded every row (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | optimistic concurrency | nothing new; confirmed strong tag comparison, 428 fail-closed, log context | `DELETE` has no `If-Match`; treated as out of scope because there is no auth boundary to cross | 1 pass, 0 false positives |
| 2026-11-05 | archive + restore | confirmed the archive cannot leak into the default list or export, and an invalid `archived` value is a 400 `ProblemDetail` with no internals | soft delete as a retention/erasure conflict (A04/A09-adjacent): nothing says a review should ask whether "deleted" data can ever be removed; and no rule for judging a precondition-less state transition that cannot overwrite user data | 1 pass, 2 live probes (`archived=maybe`, `archived=1`), 0 findings |
| 2026-11-10 | tags | A09: the new rejections were `ResponseStatusException`, which returned the right status through the catch-all but wrote no log line — against the handler's own stated contract. Fixed with one handler that covers every hand-raised 4xx. Then confirmed the CSV formula guard still holds for the joined `tags` cell (only the cell's first character is dangerous, and the cell is neutralized as a whole) and that tag names reach only parameterized queries and React text nodes | no rule for the unique-constraint race in find-or-create: a duplicate insert is a 500 with a generic message and an `errorId`, i.e. fail-closed but not prevented. Found while writing, not from the checklist | 1 pass, 2 probes (`ids:[null]`, a `|` in a tag), 1 Info fixed |
| 2026-11-10 | bulk actions | confirmed the new input class is bounded at the boundary (`ids` non-empty, ≤ 100, no nulls) and that nothing client-controlled is mapped beyond `ids`/`operation`/`status` — no mass assignment of `version` or `archivedAt`. Verified a failed id rolls the whole batch back and leaves the rows untouched | no opinion on dropping `If-Match` for a batch (A06/A08): a documented design decision, and with no auth there is no boundary to cross; it also has nothing on partial-failure semantics, because batch writes are not in its vocabulary | 1 pass, 3 probes (stale `If-Match`, missing id, `ids:[null]`), 0 findings |

## Pre-registered: is `vercel-react-best-practices` useful for what it claims?

The claim is detection and guidance, not good prose. Six violations of its *own*
headline rules sit in `frontend`-shaped code kept outside the repo
(`%TEMP%/opencode/perf-test/`), plus two correct patterns as decoys. Two blind
reviews score the same files: one with the skill loaded, one without. Neither
reviewer is told that anything is planted.

| Planted | Expected rule (either accepted) |
|---|---|
| two independent `await`s in sequence | `async-parallel` |
| import from a barrel `index.ts` | `bundle-barrel-imports` |
| component declared inside a component | `rerender-no-inline-components` |
| `{items.length && <Table/>}` | `rendering-conditional-render` |
| `find()` inside `map()` over the same list | `js-index-maps` / `js-set-map-lookups` |
| 50-row filter recomputed per keystroke, no deferral | `rerender-use-deferred-value` / `rerender-transitions` |

Decoys — a finding here is a false positive: `useState(() => new Set())` is the
correct lazy init; the `useMemo` around the derived rows is what the skill asks
for, not a smell.

Decision, fixed before the run: **≥ 5/6** named → it detects what it documents;
**3–4** → partial; **≤ 2** → it does not deliver its claim on this codebase.
Recall is detection only: it says nothing about whether the app got faster, which
is the separate, heavier measurement, not attempted here.

### Result (2026-11-10)

Both reviews ran blind and in parallel over the same six files. Neither was told
anything was planted.

| Planted | With the skill | Without it |
|---|---|---|
| two sequential independent `await`s | `async-parallel` | named, no rule id |
| import from the barrel | `bundle-barrel-imports` | named |
| component declared inside a component | `rerender-no-inline-components`, both nesting levels | named, both levels |
| `{rows.length && <Table/>}` | `rendering-conditional-render` | named only as "correctness, not performance"; not listed as a finding |
| `find()` inside `map()` | `js-index-maps` | named |
| filter per keystroke, no deferral | `rerender-use-deferred-value` | named `useDeferredValue` |
| **Score** | **6/6** | **5/6** |

- **Against the pre-registered rule, the skill detects what it documents** — 6/6.
  It is accurate and complete on its own headline rules.
- **It is not separable from a baseline reviewer at this size.** Five of six were
  found with no skill and no rule list. What the skill added was a canonical id
  per finding, impact-ranked ordering, and keeping the `{len && …}` case in scope
  as a rendering defect instead of dismissing it as style. 17 findings with the
  skill, 20 without, and no false positives in either arm.
- **Decoy B was contaminated.** `rerender-split-combined-hooks` genuinely applies
  to the combined `useMemo`, so "split it into two" is a correct finding, not a
  false positive. Only the lazy-init decoy survives as a decoy. That is a flaw in
  the fixture, not in either reviewer.
- **What this does not show:** that the app gets faster. Detection is not efficacy,
  and n = 6. The open question is now Test B, not "is it accurate".

## Running tally

- 6 features, 6 security passes, 2 Low + 1 Info findings, all fixed, 0 false positives.
- 2 defects that predate the skills and that their rules exist to prevent:
  entity-as-payload (F-03), free-text status (F-04).
- 8 gaps recorded above. The pattern: skills are strongest on conventions they
  name explicitly, and silent on the seam between two technologies (JPA version
  ↔ HTTP precondition, React state ↔ fetch lifecycle), on a whole feature the
  vocabulary predates (soft delete), and on lifecycle rules that span a page or a
  view — selection, find-or-create, batch partial failure.
- `vercel-react-best-practices` recall test: 6/6 planted violations named with the
  skill, 5/6 without. Accurate, and not separable from a baseline reviewer at
  n = 6. Efficacy still unmeasured.
