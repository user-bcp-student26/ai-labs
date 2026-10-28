# Design

## Context

See `proposal.md` for the motivation and scope. `GET /api/tasks` currently derives a requested owner from the optional query parameter or the authenticated username, interpolates that value into a SQLite statement, and returns matching rows ordered by id. The API component instructions require parameterized queries and authorization bound to identity. `docs/ENDPOINTS.md` does not define this route, so the existing response shape and the change spec are the compatibility baseline.

## Goals / Non-Goals

**Goals:**
- Keep request authentication, endpoint path, successful response shape, and row ordering unchanged.
- Ensure values supplied through `owner` are data, not query syntax.
- Ensure a request cannot return tasks outside the authenticated user's ownership scope.

**Non-Goals:**
- Change token validation or introduce an authorization role model.
- Change `POST /api/tasks`, `GET /api/summary`, other API routes, or other components.
- Add a database migration, dependency, or a new HTTP response format.

## Decisions

1. **Bind the owner value as query data.** Use SQLite's parameter-binding interface for the owner predicate. This prevents special characters in the input from changing query structure while preserving exact-match filtering. Input escaping or a hand-maintained character allowlist was rejected because neither provides the same separation between SQL syntax and values and both can reject legitimate owner names.

2. **Make the authenticated username the authorization boundary.** The row selection must always be constrained to the identity returned by the existing authentication dependency. If `owner` is omitted, that identity is the effective filter. If supplied, it is an additional exact-match constraint; a different value yields an empty list with the existing successful response shape. Continuing to use an arbitrary explicit owner was rejected because it permits a request to cross the identity boundary. Returning a new `403` for this case was rejected for this change because it adds an HTTP behavior not present in the spec or documented endpoint contract.

3. **Keep the endpoint's observable representation stable.** Preserve `GET /api/tasks`, authentication failures, the `200` list response, its existing task fields, and id ordering. Tests should exercise the route with an isolated temporary SQLite database and distinguish the authenticated user's tasks from another owner's tasks; no production database or external service is needed.

## Risks / Trade-offs

- [Existing callers may rely on listing another owner's tasks] → Those results become unavailable to ordinary authenticated identities; this is intentional to meet the API identity-authorization constraint. Any future privileged cross-owner access needs a separately specified authorization rule.
- [A malformed or special-character owner value could regress filtering if input handling changes] → Verify that it cannot return another owner's rows and that a non-matching value yields the specified empty list.
- [The route has no contract in `docs/ENDPOINTS.md`] → Keep this change's compatibility assertions in `api-task-listing`; do not rewrite unrelated documentation sections as part of this design.

## Migration Plan

No schema or data migration is required. Deploy the API change as a normal application update. Rolling back the application restores the previous cross-owner behavior and the unsafe query construction, so rollback should be treated as reintroducing the reported risk rather than as a neutral data operation.