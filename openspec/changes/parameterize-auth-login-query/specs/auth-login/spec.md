# Spec Delta

## Purpose

Define externally observable login behavior that accepts valid credentials
without allowing crafted credential values to bypass authentication.

## ADDED Requirements

### Requirement: Valid credentials preserve the login response
The login endpoint MUST continue to authenticate the valid workshop student credentials and return the established successful response fields.

#### Scenario: Student signs in with valid credentials
- **WHEN** a client submits `student` and `Workshop123!` to `POST /auth/login`
- **THEN** the endpoint responds with `200`
- **AND** the response contains `user.username` equal to `student`
- **AND** the response contains a `token`

### Requirement: Credential input cannot bypass authentication
The login endpoint MUST treat credential values as data and MUST NOT authenticate a request by interpreting SQL-like input as part of the credential lookup.

#### Scenario: Injection attempt is supplied as username
- **WHEN** a client submits `' OR '1'='1` as the username to `POST /auth/login`
- **THEN** the endpoint responds with `401`
- **AND** the response does not contain a `token`

#### Scenario: Injection attempt is supplied as password
- **WHEN** a client submits `' OR '1'='1` as the password to `POST /auth/login`
- **THEN** the endpoint responds with `401`
- **AND** the response does not contain a `token`
