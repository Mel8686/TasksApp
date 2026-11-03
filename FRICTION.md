# Friction log

Purpose: record what actually slowed the build down, while it is still fresh.
This file is the raw material for deciding what belongs in a starter kit. It is
**not** a backlog and **not** a design document. Nothing here is a commitment.

Rules for adding an entry:

1. Write it when it happens, not at the end.
2. Describe the observable symptom and the exact evidence.
3. Say what would have prevented it.
4. Assign one bucket: `context`, `gate`, `scaffold`, `skill`, `mcp`, `tooling`.
5. If it cost nothing, do not write it down. A log of everything is a log of nothing.

Buckets, briefly:

| Bucket | Meaning |
|---|---|
| `context` | An instruction file would have told the agent the right thing |
| `gate` | A check would have failed the build instead of shipping the defect |
| `scaffold` | A generated starting point or script would have removed the step |
| `skill` | A procedure an agent needs to follow across several files |
| `mcp` | A tool server would have supplied information faster/more reliably |
| `tooling` | Environment or CLI behaviour that bit us |

---

## 2026-11-02 — v0 build (bare CRUD, no auth)

### F-01 — No MCP servers were configured, so MCP usefulness is unmeasured

- **Bucket:** `mcp`
- **Symptom:** The environment had zero MCP servers. Every question about the
  codebase, the schema or the running app was answered by reading files or
  shelling out.
- **Evidence:** No MCP servers available in this session; all discovery was done
  with file reads and PowerShell.
- **Impact:** The one bucket the exercise most wanted evidence for produced *no*
  evidence. That is a result, not a gap: you cannot conclude a kit should ship
  MCP servers from a build where none existed.
- **Would have helped:** Nothing yet. Deliberately deferred until a build
  actually wants a capability a server provides (e.g. live DB introspection,
  browser automation).
- **Note:** Do not add MCP servers to the kit on the strength of this entry.
  Add one when its absence is the friction, then re-measure.

---

### F-02 — `start.spring.io` rejects `webmvc` as a dependency id

- **Bucket:** `scaffold`
- **Symptom:** Requesting `dependencies=webmvc,data-jpa,flyway,postgresql`
  returned HTTP 400 and an empty body. The same request with `web` succeeded.
- **Evidence:** `Invoke-WebRequest : The remote server returned an error: (400) Bad Request.`
  No message explaining which parameter was wrong.
- **Impact:** One wasted round trip, plus the ambiguity of not knowing whether
  the failure was the URL, the Java version, or the dependency list.
- **Would have helped:** A pinned bootstrap script with the exact, known-good
  URL, so nobody has to rediscover the ids. Spring Boot 4 renamed starters
  (`spring-boot-starter-webmvc` on the classpath, but the Initializr id is still
  `web`), which is exactly the kind of detail that goes stale in memory.

---

### F-03 — Entity-as-payload lets an empty string through while a missing field is a 500

- **Bucket:** `gate` (+ `context`)
- **Symptom:**
  - `POST {"title": ""}` → **201**, row stored with `title = ''`.
  - `POST {"status": "TODO"}` (no title) → **500**, because the only guard is
    the database `not null` constraint throwing inside the request.
- **Evidence:**
  ```
  == blank title ==
  accepted: id=3 title=''
  == missing title entirely ==
  rejected: 500
  ```
- **Impact:** Two different failure modes for the same class of mistake, and the
  bad one is the successful one. A client gets no signal that its input was
  wrong.
- **Would have helped:** Bean Validation on a request DTO (`@NotBlank`,
  `@Size`) plus an exception handler mapping violations to 400. v0 has neither
  because the entity is the payload — this is the debt that decision buys, and
  it has now appeared in the first ten minutes rather than later.

---

### F-04 — Any string is accepted as a status

- **Bucket:** `gate`
- **Symptom:** `POST {"title":"Weird status","status":"BANANA"}` → **201**.
- **Evidence:** `accepted status=BANANA`
- **Impact:** `status` is a `String` field with a default and no constraint, so
  the API accepts states the UI cannot render (`App.tsx` only knows TODO / DOING
  / DONE, and the badge class silently falls through).
- **Would have helped:** An enum in Java **and** a `check` constraint in the
  migration. The database is the cheapest place to make an invalid state
  impossible; the UI badge is not a validator.

---

### F-05 — Deleting a row that does not exist reports success

- **Bucket:** `gate`
- **Symptom:** `DELETE /api/tasks/999999` → **204 No Content**. `GET` on the same
  id correctly returns 404.
- **Evidence:** `returned 204/success for a missing id`
- **Impact:** A caller retrying a delete cannot distinguish "already gone" from
  "never existed", and the two verbs disagree about what absence means.
- **Would have helped:** A contract that states the expected status per verb, or
  a test asserting it. Nothing in v0 states either.

---

### F-06 — `createdAt` has different precision on create than on read

- **Bucket:** `context`
- **Symptom:** The `POST` response returns nanoseconds from the in-memory
  `Instant`; a subsequent `GET` returns microseconds read back from Postgres.
- **Evidence:**
  ```
  create: 2026-11-02T09:01:37.366556700Z
  read  : 2026-11-02T09:01:37.366557Z
  ```
- **Impact:** Harmless today. It becomes a real bug the moment a client compares
  the value it created against the value it reads (dirty-checking, optimistic
  concurrency, caching). It is also a pure symptom of serialising the entity:
  a DTO built after the flush would return one stable value.
- **Would have helped:** Nothing in v0. Worth remembering as the reason a
  response DTO exists.

---

### F-07 — The frontend hand-copies the API shape

- **Bucket:** `scaffold` (+ `contract`)
- **Symptom:** `App.tsx` declares `type Task = { id, title, status, dueDate, createdAt }`
  by hand, mirroring `Task.java`. Nothing connects the two.
- **Evidence:** `frontend/src/App.tsx` lines 5–11.
- **Impact:** Zero cost to write. The cost arrives on the first field change:
  rename `dueDate` in Java and TypeScript still compiles, then the page silently
  renders `undefined`. Not yet painful, so not yet a justification for a
  contract — but it is the obvious first candidate once a field is added.
- **Would have helped:** Nothing yet. Flagging so the next increment can test
  whether a generated client would have earned its setup cost.

---

### F-08 — Vite's proxy hides the absence of CORS

- **Bucket:** `scaffold`
- **Symptom:** The browser talks to `:5173` and Vite proxies `/api` to `:8080`,
  so the app works with no CORS configuration anywhere.
- **Evidence:** `proxy tasks=1` through `http://localhost:5173/api/tasks`.
- **Impact:** Positive in v0. It becomes a trap when a second client (mobile,
  another port, a deployed preview) appears: the backend has never had to allow
  an origin, and the first fix attempted is usually `@CrossOrigin("*")`.
- **Would have helped:** Nothing in v0. Recorded because "it works locally" here
  is a property of the dev server, not of the API.

---

### F-09 — PowerShell 5.1 `.Count` is silently empty on a single object

- **Bucket:** `tooling`
- **Symptom:** `$response.Count` returns nothing when the JSON payload is one
  object rather than an array; `@($response).Count` returns 1.
- **Evidence:** `type=System.Management.Automation.PSCustomObject count= len=1`
  on PowerShell `5.1.26100.1150`.
- **Impact:** Observed earlier in this session while asserting API responses.
  A check written as `.Count -eq 1` fails open — it errors as "empty", not as
  "wrong", so the wrong conclusion is easy to draw.
- **Would have helped:** Assertions in a real test framework rather than ad-hoc
  PowerShell. For scripts that must stay in PowerShell, `@(...)` on every
  collection before counting.

---

### F-10 — Edit appeared to do nothing; the row closed in the same click that opened it

- **Bucket:** `gate` (+ `tooling`)
- **Symptom:** Clicking **Edit** showed the input row for an instant, then it
  reverted to the read-only row. No error message, no network request. It looked
  like the button was dead.
- **Root cause:** The Save button was `type="submit"` with `form={edit-id}`, and
  it rendered in the same table cell, in the same position, as the Edit button it
  replaced. The pointer-up from the Edit click landed on the freshly mounted
  submit button, which submitted the form immediately. `saveEdit` then set
  `editing` back to `null`.
- **Evidence:** Instrumenting the component showed the sequence inside one click:
  ```
  [log] EDIT_CLICK 8
  [log] RENDER App
  [log] EDITING STATE = 8
  [log] SAVE_EDIT fired {id: 8, title: "UI add test", ...}
  ```
  The same click dispatched programmatically (`element.click()`, no pointer
  coordinates) left the row open and Save worked — confirming the bug was
  positional, not logical.
- **Impact:** The single most-used interaction in the app was broken. Every API
  check passed, `tsc` passed, and `vite build` passed. `PUT /api/tasks/{id}` with
  the exact payload the UI sends returned 200 throughout.
- **Fix:** Save is now `type="button"` with an `onClick`. The `<form>` stays so
  Enter still submits, and the title input autofocuses so the row is ready to
  type in. Verified with a real mouse: open, change title/status/date, save,
  reload, cancel, and Enter-to-submit all behave.
- **Would have helped:** A browser-level test of the edit flow. This bug is
  invisible to every gate in the stack above it. It is the strongest argument so
  far for a Playwright smoke test against the running app — and it is cheap here,
  because Edge is already installed and `playwright-core` drives it without
  downloading a browser.
- **Lesson for the kit:** "It compiles, the API works, and the build is green" is
  not the same claim as "the app works". Any kit that stops at the API boundary
  will ship this class of bug.

---

## 2026-11-03 — search, status filter and pagination

### F-11 — `findAll(null, Pageable)` throws in Spring Data JPA 4.0

- **Bucket:** `skill`
- **Symptom:** The no-filter branch of the list endpoint failed every request
  with 500: `InvalidDataAccessApiUsageException: Specification must not be null`.
- **Evidence:** `at SimpleJpaRepository.getQuery(SimpleJpaRepository.java:805)` /
  `Caused by: java.lang.IllegalArgumentException: Specification must not be null`.
- **Impact:** 3 minutes, and only because the first curl after restart returned
  no rows. `findAll(Specification, Pageable)` used to accept null; 4.0 asserts.
- **Would have helped:** `references/data.md` says to use `Specification` but not
  that an empty filter must branch to `findAll(Pageable)`. One sentence.

---

### F-12 — Spring's built-in ProblemDetails advice outranks a user advice

- **Bucket:** `skill`
- **Symptom:** `?size=100` and `?page=-1` returned Spring's default
  `{"detail":"Validation failure"}` instead of the project's
  `{"detail":"Request validation failed","errors":{...}}`. The custom
  `@ExceptionHandler(HandlerMethodValidationException.class)` never ran.
- **Evidence:** Same advice handled body validation correctly; adding
  `@Order(HIGHEST_PRECEDENCE)` to the advice made the query-parameter errors use
  the project shape.
- **Impact:** The API had two error shapes for the same class of client mistake,
  which is precisely the drift the advice exists to prevent.
- **Would have helped:** `references/rest-api.md` warns to *test* invalid query
  parameters, which is how this was found — so the skill did its job. It does not
  mention the ordering trap, which is the part that took the time.

---

### F-13 — `curl.exe -d` from PowerShell 5.1 mangles a JSON body

- **Bucket:** `tooling`
- **Symptom:** `curl.exe -d '{"title":"Demo"}'` posted a body the API rejected as
  `{"detail":"Malformed request body"}`. The identical JSON via
  `--data-binary @file` succeeded.
- **Evidence:** inline → 400 `Malformed request body`; from file → 201 with the
  new row. The JSON was valid in both cases.
- **Impact:** Two attempts spent debugging the server for a client-side quoting
  problem — the same class as F-09, in a different command.
- **Would have helped:** Post fixtures from files, or use `Invoke-RestMethod` for
  JSON bodies and keep `curl.exe` for headers and multipart.

---

## 2026-11-03 — optimistic concurrency

### F-14 — Two `--data-binary` options under `curl --parallel` become one body

- **Bucket:** `tooling`
- **Symptom:** To race two `PUT`s with the same `If-Match`, I ran
  `curl --parallel ... --data-binary @a.json URL --data-binary @b.json URL`.
  Both requests returned 400 `Malformed request body`. The same files went
  through fine one request at a time.
- **Evidence:** `round 1 from v3 -> 400 + 400` for six rounds, with the API
  logging `HttpMessageNotReadableException`. curl concatenates repeated
  `--data*` options into a single body, so both requests carried
  `{...}{...}`.
- **Impact:** One round of suspecting the server again, immediately after F-13.
  `--next` splits option groups but still does not give two independent bodies
  in one process.
- **Would have helped:** Fire concurrent requests from .NET
  (`HttpClient.SendAsync` twice before awaiting), which worked first try and is
  what produced the real evidence: 6 rounds, one 200 and one 412 each time,
  version +1, no lost update.

---

## Not logged, on purpose

- Flyway creating the table on first boot and Hibernate `validate` agreeing with
  it: worked first time, no friction.
- Vite scaffolding, `npm install`, and the production build: worked first time.
- The `mvnw` wrapper downloading Maven: slow, but expected and unattended.

If the log stays this short, the honest read is that the *stack* was not the
friction. The friction was in the gaps between what the code does and what the
code promises — which is exactly where a kit either helps or does not.
