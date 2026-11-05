# Design

## Context

See [proposal.md](proposal.md) for the motivation and [specs/auth-login/spec.md](specs/auth-login/spec.md) for the observable requirements. The change is limited to `auth/`.

`AuthResource.login` handles `POST /auth/login` and delegates credential lookup to `LegacyDatabase.findByCredentials`. That method currently builds a SQLite query by concatenating `username` and `password` into SQL, executes it through `Statement`, and maps a matching row to `UserRecord`. The resource returns HTTP 200 with `user.username` and a token when a record is found, or HTTP 401 without a token otherwise. The existing `AuthResourceTest` covers only a successful login.

## Goals / Non-Goals

**Goals:**
- Make the credential lookup interpret both supplied values only as data.
- Preserve the endpoint and successful and rejected login contracts defined in the spec.
- Keep the change local to the auth repository lookup and its focused tests.

**Non-Goals:**
- Change request validation, error formats, token generation or format, response fields beyond the protected contract, database schema, or other endpoints.
- Refactor authentication into new layers or modify `api/` or `web/`.
- Add dependencies or change credential storage practices as part of this issue.

## Protected Contracts

- `POST /auth/login` remains the login route with the existing JSON request shape.
- `student` / `Workshop123!` continues to return HTTP 200 with `user.username` equal to `student` and a `token`.
- The specified SQL-like attempt in either credential field returns HTTP 401 and no `token`.
- Existing credential validation and response behavior outside these requirements remain unchanged.

## Affected Components

- `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`: credential query construction and execution.
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: HTTP-level characterization of successful and rejected credential outcomes.
- `AuthResource` and `LegacyTokenUtil` are contract boundaries, not planned implementation targets; their route, response construction, and token behavior should remain unchanged.

No files or behavior in `api/`, `web/`, or shared root documentation need to change.

## Decisions

1. **Use JDBC `PreparedStatement` with bound username and password parameters.** The current interpolated SQL is the point where credential input can alter the lookup. Binding each value keeps the query structure fixed while preserving literal credential matching. Java's JDBC API is already available through the existing SQLite driver, so no dependency is needed. Alternatives considered: escaping or filtering input, which is brittle and may reject legitimate values without fixing the query boundary; adding an ORM or another database abstraction, which is disproportionate for this local lookup.

2. **Keep the change within `LegacyDatabase.findByCredentials`.** Preserve the method's caller, lookup result mapping, and the resource's success/failure response paths. This isolates the security correction and reduces the chance of changing login behavior. Alternatives considered: moving authentication into a new service or refactoring `AuthResource`, which would expand scope without being needed to bind SQL values.

3. **Characterize behavior at the HTTP boundary before changing the lookup.** Extend the existing Quarkus/RestAssured test to pin the valid login response and both specified injection outcomes. These tests cover the security boundary and make accidental response/token regressions visible. Avoid asserting unspecified message text or other response fields.

## Incremental Strategy

1. Add or complete HTTP-level characterization tests for the existing successful login, the specified username injection attempt, and the specified password injection attempt. Assert the exact status, `user.username` and token presence for success, and 401 plus token absence for rejection.
2. Run the focused auth tests before implementation. Confirm the valid-login characterization passes and that each new attack test exposes the current unsafe behavior; do not treat a 500 as a passing rejection.
3. Replace only the credential query's string interpolation and `Statement` execution with a parameterized JDBC query, binding username and password in their corresponding placeholders. Preserve row mapping and resource closure.
4. Rerun the auth tests, then verify the component's configured test/coverage gate. Do not change the proposal's scope or other components to satisfy this design.

Tests run from `auth/` (or via `python tools/lab.py test auth` from the repository root, per `AGENTS.md`).

## Characterization Tests Before Critical Behavior Changes

Before changing query construction, the auth test suite should establish:

- A request with `student` and `Workshop123!` returns 200, `user.username == "student"`, and a non-null token.
- A request with `' OR '1'='1` as the username is rejected with 401 and has no token.
- A request with `student` as the username and `' OR '1'='1` as the password is rejected with 401 and has no token.

These are API-level tests through `POST /auth/login`; they should not depend on database internals or assert unspecified error text. Keep them in the existing `AuthResourceTest` rather than introducing a separate test framework or test module.

## Risks / Trade-offs

- **An attack test can pass for the wrong reason (for example, a server error).** Mitigation: assert 401 explicitly and assert that the response has no token; keep the valid-login test as a control.
- **Test database state or initialization can affect repeatability.** Mitigation: use the existing workshop test setup and fixed credentials; do not write or mutate records in these tests.
- **Narrow scope leaves existing exception handling and other authentication concerns unchanged.** Mitigation: treat those as separate issues; do not broaden this change or conceal scanner findings.
- **Parameterized lookup prevents SQL syntax from changing this query but does not address password hashing or broader authentication hardening.** Mitigation: retain those as explicit non-goals and avoid implying broader security coverage.

## Rollback

No schema or data migration is introduced, so there is no data rollback. Rolling back the query change would restore the authentication-bypass risk; prefer a forward fix and block or revert the release only if necessary to contain a deployment regression, with the security impact explicitly accepted until the fix is restored.