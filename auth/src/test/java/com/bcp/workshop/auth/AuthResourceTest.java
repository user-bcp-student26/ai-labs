package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String DATABASE_URL = System.getenv()
            .getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

    @Test
    void reportsHealthWithoutAuthentication() {
        given()
                .accept("application/json")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks.size()", is(0));
    }

    @Test
    void alsoReportsHealthOnPost() {
        var response = given()
                .when().post("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks.size()", is(0))
                .extract().response();

        assertEquals("UP", response.jsonPath().getString("status"));
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user unknown-user or password"));
    }

    @Test
    void rejectsCredentialsContainingSingleQuotes() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student'\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!'\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void rejectsMissingLoginFields() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));

        given()
                .contentType("application/json")
                .body("{\"username\":\" \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));

        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsMalformedJsonWithBadRequest() {
        var response = given()
                .contentType("application/json")
                .body("{\"username\":")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .extract().response();

        assertTrue(response.asString().isEmpty());
    }

    @Test
    void rejectsUnsupportedLoginMethod() {
        var response = given()
                .when().get("/auth/login")
                .then()
                .statusCode(405)
                .extract().response();

        assertTrue(response.asString().isEmpty());
    }

    @Test
    void rejectsIncompatibleAcceptHeader() {
        var response = given()
                .accept("text/plain")
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(406)
                .extract().response();

        assertTrue(response.asString().isEmpty());
    }

    @Test
    void rejectsUnsupportedLoginContentType() {
        var response = given()
                .contentType("text/plain")
                .body("{}")
                .when().post("/auth/login")
                .then()
                .statusCode(415)
                .extract().response();

        assertTrue(response.asString().isEmpty());
    }

    @Test
    void returnsGenericMessageWhenInternalErrorOccurs() {
        AuthResource resource = new AuthResource();
        LegacyDatabase database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("simulated-database-failure");
            }
        };
        resource.authService = new AuthService(database);
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";

        Response response = resource.login(request);
        Map<?, ?> body = (Map<?, ?>) response.getEntity();

        assertEquals(500, response.getStatus());
        assertEquals("An unexpected error occurred", body.get("message"));
        assertFalse(body.containsKey("exception"));
    }

    @Test
    void allowsUnlistedOriginInCorsPreflight() {
        given()
                .header("Origin", "http://unlisted.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", is("http://unlisted.example"));
    }

    @Test
    void storesSeededPasswordAsSaltedHash() throws Exception {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, "student");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                String storedHash = result.getString("password");
                assertNotEquals("Workshop123!", storedHash);
                assertTrue(storedHash.startsWith("pbkdf2-sha256$"));
                assertTrue(PasswordHasher.verify("Workshop123!", storedHash));
                assertFalse(PasswordHasher.verify("wrong", storedHash));
            }
        }
    }

    @Test
    void generatesUniqueSaltsAndRejectsMalformedHashes() {
        String firstHash = PasswordHasher.hash("Workshop123!");
        String secondHash = PasswordHasher.hash("Workshop123!");
        String[] hashParts = firstHash.split("\\$");
        String unsupportedAlgorithm = "pbkdf2-sha1$600000$" + hashParts[2] + "$" + hashParts[3];
        String malformedIterations = "pbkdf2-sha256$not-a-number$" + hashParts[2] + "$" + hashParts[3];
        String insufficientIterations = "pbkdf2-sha256$1$" + hashParts[2] + "$" + hashParts[3];
        String excessiveIterations = "pbkdf2-sha256$1000001$" + hashParts[2] + "$" + hashParts[3];
        String malformedSalt = "pbkdf2-sha256$600000$%%%$" + hashParts[3];
        String shortSalt = "pbkdf2-sha256$600000$" + hashParts[2].substring(1) + "$" + hashParts[3];
        String shortKey = "pbkdf2-sha256$600000$" + hashParts[2] + "$AQ";

        assertNotEquals(firstHash, secondHash);
        assertFalse(PasswordHasher.isEncoded(null));
        assertFalse(PasswordHasher.verify(null, firstHash));
        assertFalse(PasswordHasher.verify("Workshop123!", null));
        assertFalse(PasswordHasher.verify("Workshop123!", "plaintext"));
        assertFalse(PasswordHasher.verify("Workshop123!", unsupportedAlgorithm));
        assertFalse(PasswordHasher.verify("Workshop123!", malformedIterations));
        assertFalse(PasswordHasher.verify("Workshop123!", insufficientIterations));
        assertFalse(PasswordHasher.verify("Workshop123!", excessiveIterations));
        assertFalse(PasswordHasher.verify("Workshop123!", malformedSalt));
        assertFalse(PasswordHasher.verify("Workshop123!", shortSalt));
        assertFalse(PasswordHasher.verify("Workshop123!", shortKey));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }

    @Test
    void migratesExistingPlaintextPasswordsAndKeepsLoginWorking() throws Exception {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT OR REPLACE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            insert.setString(1, "migration-test-user");
            insert.setString(2, "LegacyMigration123!");
            insert.setString(3, "Migration Test User");
            insert.setString(4, "STUDENT");
            insert.executeUpdate();
        }

        try {
            new LegacyDatabase().initialize();

            try (Connection connection = DriverManager.getConnection(DATABASE_URL);
                 PreparedStatement select = connection.prepareStatement(
                         "SELECT password FROM legacy_users WHERE username=?")) {
                select.setString(1, "migration-test-user");
                try (ResultSet result = select.executeQuery()) {
                    assertTrue(result.next());
                    String storedHash = result.getString("password");
                    assertNotEquals("LegacyMigration123!", storedHash);
                    assertTrue(storedHash.startsWith("pbkdf2-sha256$"));
                }
            }
            UserRecord migratedUser = new LegacyDatabase().findByCredentials("migration-test-user", "LegacyMigration123!");
            assertNotNull(migratedUser);
            assertEquals("migration-test-user", migratedUser.username());
        } finally {
            try (Connection connection = DriverManager.getConnection(DATABASE_URL);
                 PreparedStatement delete = connection.prepareStatement(
                         "DELETE FROM legacy_users WHERE username=?")) {
                delete.setString(1, "migration-test-user");
                delete.executeUpdate();
            }
        }
    }

}
