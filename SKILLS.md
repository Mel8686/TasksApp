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
| 2026-11-11 | sort | `data.md`: "always sort on a stable, unique column (`Sort.by("id")`)" — that is the `id` tie-breaker, and the reason paging over duplicate titles neither drops nor repeats a row (verified: 21 ids over 5 pages, 21 unique). Its parameter-validation shape is what the 400 reuses | nothing on turning a user-chosen order into a whitelist: the token map, the 400 for an unknown token and `nullsLast()` on a nullable date are ours | 0 reference reads (applied from the earlier `data.md` read), 1 unit test, 0 defects |

## `vercel-react-best-practices`

**What it had to work with:** one screen. `App.tsx` is 588 lines holding 5
components, 3 effects and 22 `useState`; no router, no context, no client cache,
two source files, 5 rows per page by default and a hard cap of 50. Read all of
this section as evidence about a skill with almost no surface here, not as a
verdict on the skill.

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | nothing new; aborting superseded fetches and debounce are rules it endorses | nothing | one skill read |
| 2026-11-03 | CSV export/import | nothing | `FormData` must not be sent with a JSON content type — a real bug it has no rule for | 0 |
| 2026-11-03 | optimistic concurrency | nothing | stale-closure capture (`const target = editing`), conflict-state branching — no rule covers either | 0 |
| 2026-11-05 | archive + restore | nothing new; kept the abort/debounce effect and derived the view from state, no effect added | state coherence across a view switch: resetting page and closing the editor when the row set changes is not in its rules; it is a perf skill, and this was correctness | 0 |
| 2026-11-10 | tags | endorsed reusing the debounced + aborted fetch effect for the new `tag` parameter, and `rerender-derived-state` / `rendering-conditional-render` shaped the chips and the empty state (ternaries, nothing stored twice) | chip-click-as-filter is UI state design it has no rule for; also silent that a selection must be cleared when its row leaves the view — correctness again, not performance | 0 |
| 2026-11-10 | bulk actions | `js-set-map-lookups` (the selection is a `Set`, O(1) per row) and `rerender-functional-setstate` on every selection write, which is what keeps the page checkbox honest | the "did every selected row just leave this page?" step-back is the same bug class as archive's; no rule. And `rerender-memo` had to be declined per `rerender-simple-expression-in-memo` — the skill working as intended, by saying no | 0 |
| 2026-11-11 | sort | `rerender-dependencies` again, and this time it shaped the change: `sort` joins the fetch effect as a primitive string, so the debounced + aborted effect still fires once per change instead of looping on a rebuilt object | changing the order must reset `page` and drop the selection — a re-sorted page holds different rows, so a kept page number and a kept `Set` both point at rows no longer on screen. Third feature running where that class is ours to remember, not the skill's to warn about | 0 |

## `owasp-security`

| Date | Feature | Caught | Missed | Cost |
|---|---|---|---|---|
| 2026-11-03 | search + paging | unbounded `page` → deep `OFFSET` (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | CSV export/import | export claimed to stream but loaded every row (Low) | nothing | 1 pass, 0 false positives |
| 2026-11-03 | optimistic concurrency | nothing new; confirmed strong tag comparison, 428 fail-closed, log context | `DELETE` has no `If-Match`; treated as out of scope because there is no auth boundary to cross | 1 pass, 0 false positives |
| 2026-11-05 | archive + restore | confirmed the archive cannot leak into the default list or export, and an invalid `archived` value is a 400 `ProblemDetail` with no internals | soft delete as a retention/erasure conflict (A04/A09-adjacent): nothing says a review should ask whether "deleted" data can ever be removed; and no rule for judging a precondition-less state transition that cannot overwrite user data | 1 pass, 2 live probes (`archived=maybe`, `archived=1`), 0 findings |
| 2026-11-10 | tags | A09: the new rejections were `ResponseStatusException`, which returned the right status through the catch-all but wrote no log line — against the handler's own stated contract. Fixed with one handler that covers every hand-raised 4xx. Then confirmed the CSV formula guard still holds for the joined `tags` cell (only the cell's first character is dangerous, and the cell is neutralized as a whole) and that tag names reach only parameterized queries and React text nodes | no rule for the unique-constraint race in find-or-create: a duplicate insert is a 500 with a generic message and an `errorId`, i.e. fail-closed but not prevented. Found while writing, not from the checklist | 1 pass, 2 probes (`ids:[null]`, a `|` in a tag), 1 Info fixed |
| 2026-11-10 | bulk actions | confirmed the new input class is bounded at the boundary (`ids` non-empty, ≤ 100, no nulls) and that nothing client-controlled is mapped beyond `ids`/`operation`/`status` — no mass assignment of `version` or `archivedAt`. Verified a failed id rolls the whole batch back and leaves the rows untouched | no opinion on dropping `If-Match` for a batch (A06/A08): a documented design decision, and with no auth there is no boundary to cross; it also has nothing on partial-failure semantics, because batch writes are not in its vocabulary | 1 pass, 3 probes (stale `If-Match`, missing id, `ids:[null]`), 0 findings |
| 2026-11-11 | sort | confirmed the whitelist closes the classic sort-injection path: the token is a key in a five-entry map, so no client text reaches a property or an expression, the 400 does not echo the input back, and that 400 is logged (A09, seen in the log) | sort / `ORDER BY` is absent from its vocabulary: `grep -i 'sort\|order by'` over `review-checklist.md` and `owasp-report.md` returns nothing. "Whitelist the token, never a field name" is knowledge the review did not supply — A05 covers it only in principle | 1 pass, 2 probes (`sort=title;drop`, `sort=createdAt`), 0 findings |

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

- **6/6**, against a pre-registered threshold of ≥ 5/6: it finds its own headline
  rules. 17 findings with the skill, 20 without, and no false positives either way.
- **Not separable at this size.** The baseline found five of six with no rule
  list. What the skill added was an id per finding, impact order, and keeping
  `{len && …}` in scope as a rendering defect rather than dismissing it as style.
- **Fixture flaw:** decoy B was contaminated — `rerender-split-combined-hooks`
  genuinely applies to the combined `useMemo`, so that finding was correct, not a
  false positive. Only the lazy-init decoy survived as a decoy.

### Pre-registered: Test B, a fixture the rules do not agree with instinct

Test A was too easy: five of six were found with no skill, because the violations
look like mistakes. Test B keeps the same protocol and changes the fixture, so the
only variable is the fixture's difficulty. Two files, `board.ts` and
`TaskBoard.tsx`, in `%TEMP%/opencode/perf-test-2/src/`; nothing else in that folder.
Four blind reviewers, two per arm, same prompt within an arm, no mention that
anything is planted. Foreground, parallel, one pass each, no follow-up.

The six planted items are chosen so that each one is a pattern a competent
reviewer might defend: an effect that computes a count reads as "normal React"; a
manual `loading` boolean is what most codebases do; a default `tags = []` looks
like defensive coding; awaiting the config before checking the flag looks
necessary; a fresh `filters` object in deps looks like clarity; a `setState` on
mousemove is unambiguous but easy to skim past.

| # | Planted (file: what) | Accepted rule id(s) |
|---|---|---|
| 1 | `TaskBoard.tsx` `OpenCount`: count kept in state, set from an effect | `rerender-derived-state-no-effect` / `rerender-derived-state` |
| 2 | `TaskBoard.tsx` `StatusFilter`: `filters` object built in render, used in effect deps | `rerender-dependencies` |
| 3 | `TaskBoard.tsx` `SearchBox`: manual `searching` boolean around an await | `rendering-usetransition-loading` |
| 4 | `TaskBoard.tsx` `CursorGlow`: `mousemove` writes `setState` | `rerender-use-ref-transient-values` |
| 5 | `TaskBoard.tsx` `TagList`: `memo` component with `tags = []` default, rendered as `<TagList />` | `rerender-memo-with-default-value` / `rerender-memo` |
| 6 | `board.ts` `notifyIfEnabled`: `await fetchConfig()` before the cheap `enabled` check | `async-cheap-condition-before-await` |

Scoring, fixed before the run: an item counts only if the reviewer names the
pattern as a problem at that code location, either by rule id or by a description
that unambiguously points at the same line. A rule id quoted in a "rules I know"
preamble does not count. A hedged mention ("this could be memoised further") does
not count. Both arms are scored against this same key by the same reader.
Per item, per arm: **stably detected** = both reviewers; **detected** = at least
one. False positives are recorded but not scored.

Decision, fixed before the run:

- Skill arm stably detects **≥ 5/6** and the baseline stably detects **≤ 3/6** →
  the skill adds detection on rules that are not common sense.
- Both arms stably **≥ 5/6** → accurate, still not separable; say so again and
  stop trying to separate it this way.
- Skill arm stably **≤ 3/6** → it does not deliver on the rules where it would
  matter most, and the README's React cell gets that, not the 6/6 from Test A.

The 6/6 from Test A stands as written. Test B does not replace it; it tests
whether that number came from the skill or from the reviewer.

### Result (2026-11-10)

Four reviews ran blind, two per arm, parallel, same prompt within an arm. Neither
was told anything was planted. `✓` = both reviewers named it at the right line;
`~/–` = one / none.

| # | Planted | With the skill | Without |
|---|---|---|---|
| 1 | count in state + effect | ✓ (both, by rule id) | ✓ (both) |
| 2 | `filters` object in deps | ✓ (both, by rule id) | ✓ (both) |
| 3 | manual `searching` boolean | ✓ (both: `useTransition`) | ~/– (neither) |
| 4 | `mousemove` → `setState` | ✓ (both, by rule id) | ✓ (both) |
| 5 | `memo` + `tags = []` default | ✓ (both, by rule id) | ✓ (both) |
| 6 | `await` before the cheap guard | ✓ (both, by rule id) | ✓ (both) |
| **Score** | | **6/6 stable** | **5/6 stable** |

- **Not separable, and the pre-registered hypothesis failed.** The threshold was
  skill ≥ 5/6 *and* baseline ≤ 3/6. The baseline found five of six, including
  every item built to be counterintuitive: a competent reviewer already knows
  these rules. Stop trying to separate recall this way.
- **One item separated.** No baseline reviewer proposed `useTransition` for the
  pending flag; both skill reviewers did. But the baselines had answered that same
  code with a different, also-correct finding (no debounce, no abort, out-of-order
  responses). That is vocabulary, not detection.
- **Fixture flaws, disclosed.** `searchTasks` is typed `Task[]` where this API
  returns `PageResponse`, and `visible` falls back to all rows when a filter
  legitimately returns 0 — both found by baseline reviewers, neither a planted
  item. One inaccuracy the other way: `setSearching(false)` after unmount no
  longer warns in React 18.

### What the two tests say, together

- **Accurate, twice.** Both fixtures: its own rules found, right ids, sane order,
  no false positives in any arm. That claim is settled.
- **Not separable from a strong reviewer, twice.** Test B existed to break the tie
  and did not. Its visible value is naming and ranking, not finding more.
- **The likely reason is scope, not the skill.** This frontend is too simple for a
  performance skill to have room: one screen, 5 components, 3 effects, no router,
  no client cache, no memoisation pressure, ≤ 50 rows, two source files. The skill
  is written for a frontend with waterfalls, long lists, expensive renders and a
  bundle worth splitting — an app this one is not.
- **That bounds the evidence; it is not a point for the skill.** It also means
  nothing here argues against keeping it. The honest state is *unproven*: to move
  off it, the frontend would need a screen where performance is measurable, which
  is a bigger feature than any this repo has agreed to.
- **Still unmeasured:** whether the app gets faster. Detection is not efficacy,
  and neither fixture is this app.

## Running tally

- 7 features, 7 security passes, 2 Low + 1 Info findings, all fixed, 0 false positives.
- 2 defects that predate the skills and that their rules exist to prevent:
  entity-as-payload (F-03), free-text status (F-04).
- 9 gaps recorded above. The pattern: skills are strongest on conventions they
  name explicitly, and silent on the seam between two technologies (JPA version
  ↔ HTTP precondition, React state ↔ fetch lifecycle), on a whole feature the
  vocabulary predates (soft delete), on classic sinks they never name (sort /
  `ORDER BY` injection), and on lifecycle rules that span a page or a view —
  selection, find-or-create, batch partial failure.
- `vercel-react-best-practices`: accurate twice (6/6 in both fixtures), separable
  neither time (5/6 without it in both). 0 defects caught across seven features, 1
  defensive rule declined. Most likely bound by how little this frontend does,
  which makes it unproven here rather than wrong.
