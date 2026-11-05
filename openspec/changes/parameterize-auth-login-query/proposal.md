# Proposal

## Why

Issue #21 (H-AUTH-01) identifies an authentication bypass in `auth/`: crafted
credentials can alter the login database lookup and authenticate without a valid
password. Preventing this is necessary to protect the login boundary while
keeping legitimate sign-in behavior intact.

## What Changes

- Require credential lookup to treat submitted usernames and passwords as
  literal credential values, so SQL-like input cannot change the lookup or
  grant access.
- Preserve successful login for the documented-in-Issue `student` credentials
  and preserve the `POST /auth/login` response fields `user.username` and
  `token`.
- Ensure the Issue's injection attempts as either username or password are
  rejected with `401` and do not return a token.

## Capabilities

### New Capabilities

- `auth-login`: Covers observable login authentication outcomes, including
  rejection of credentials that attempt to bypass authentication.

### Modified Capabilities

None. The OpenSpec project currently has no existing capability specs.

## Impact

- Scope is limited to the `auth/` component and its `POST /auth/login` behavior.
- `docs/ENDPOINTS.md` documents the FastAPI API routes and does not specify
  `POST /auth/login`; therefore it establishes no conflicting login details.
  The login compatibility baseline for this proposal is the explicit contract
  in Issue #21. No other documented API behavior is changed.
- Out of scope: `api/`, `web/`, other authentication behavior, token format,
  database schema, and changes to other endpoints.
- No dependencies or user-facing endpoint changes are proposed.
