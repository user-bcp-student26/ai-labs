package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthServiceTest {
    @Test
    void buildsLoginResponseForValidCredentials() {
        UserRecord user = new UserRecord(1, "student", "Workshop Student", "STUDENT");
        AuthService service = new AuthService(new StubDatabase(user));

        LoginResponse response = service.authenticate("student", "Workshop123!");

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(3600, response.expiresIn());
        assertEquals("student", response.user().username());
        assertEquals("Workshop Student", response.user().displayName());
        assertEquals("STUDENT", response.user().role());
        assertEquals("authenticated-with-legacy-flow", response.debug());
    }

    @Test
    void returnsNullForInvalidCredentials() {
        AuthService service = new AuthService(new StubDatabase(null));

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