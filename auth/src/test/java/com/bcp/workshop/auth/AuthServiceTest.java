package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthServiceTest {
    private static final byte[] TEST_SIGNING_KEY = "test-only-auth-signing-key-not-for-real-use"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void buildsLoginResponseForValidCredentials() {
        UserRecord user = new UserRecord(1, "student", "Workshop Student", "STUDENT");
        TokenService tokenService = new TokenService(TEST_SIGNING_KEY);
        AuthService service = new AuthService(new StubDatabase(user), tokenService);

        LoginResponse response = service.authenticate("student", "Workshop123!");

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(3600, response.expiresIn());
        TokenService.TokenClaims claims = tokenService.verify(response.token());
        assertEquals("student", claims.username());
        assertEquals("STUDENT", claims.role());
        assertEquals("student", response.user().username());
        assertEquals("Workshop Student", response.user().displayName());
        assertEquals("STUDENT", response.user().role());
        assertEquals("authenticated-with-legacy-flow", response.debug());
    }

    @Test
    void returnsNullForInvalidCredentials() {
        AuthService service = new AuthService(new StubDatabase(null), new TokenService(TEST_SIGNING_KEY));

        assertNull(service.authenticate("student", "wrong"));
    }

    private static final class StubDatabase extends LegacyDatabase {
        private final UserRecord user;

        private StubDatabase(UserRecord user) {
            this.user = user;
        }

        @Override
        public UserRecord findByCredentials(String username, String password) {
            return user;
        }
    }
}