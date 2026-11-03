# Backend

## Required skill

Before writing, reviewing, or refactoring any file in this directory, load the
`java-springboot` skill with the skill tool and follow it.

It is vendored at `.opencode/skills/java-springboot/` (GitHub, MIT). It covers
controllers, validation and `ProblemDetail` errors, Spring Data JPA, configuration
properties, and build verification. Read its `references/rest-api.md` for
controller and validation work, and `references/data.md` for persistence.

The skill targets an **existing** Spring Boot 3/4 project, which is this one. For
scaffolding a new project it defers to a separate skill we do not have.

## Security

After any change touching an endpoint, a request/response type, a query
parameter, an error handler, or config, run the `owasp-security` skill over the
files you changed before reporting done. Report findings in its format, or state
plainly that there are none.

## Verification

- `.\mvnw.cmd test` — the context-load test runs Flyway and Hibernate `validate`
  against the real database, so a broken migration fails here.
- `.\mvnw.cmd package` — full build.
