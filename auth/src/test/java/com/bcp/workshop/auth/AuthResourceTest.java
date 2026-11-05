package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String SQL_LIKE_INPUT = "' OR '1'='1";
    private static final String STUDENT_PASSWORD = "Workshop123!";

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    // Spec "Injection attempt is supplied as username": 401 and no token.
    @Test
    void rejectsSqlLikeInputAsUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"" + SQL_LIKE_INPUT + "\",\"password\":\"" + STUDENT_PASSWORD + "\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }

    // Spec "Injection attempt is supplied as password": 401 and no token.
    @Test
    void rejectsSqlLikeInputAsPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"" + SQL_LIKE_INPUT + "\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }
}
