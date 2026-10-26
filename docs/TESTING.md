# Pruebas de auth

## Alcance y trazabilidad

Componente: `auth`. IDs relacionados: H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-06, H-AUTH-07 y H-AUTH-08. H-AUTH-05 (SCA) no se verifica con estas pruebas; su revisión de advisories sigue pendiente. Los endpoints y resultados esperados se basan en el contrato observado en `docs/ENDPOINTS.md`, no en una aprobacion de consumidores.

## Matriz de casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Sin autenticación | Camino feliz | 200; `status=UP`; `checks=[]`. |
| `GET /q/health` | POST también aceptado por el runtime | Borde | 200; el mismo reporte `UP`. |
| `GET /q/health` | Respuesta de error | Error | No hay error documentado/observado en el estado actual; no existen checks que permitan inducir `DOWN`. |
| `POST /auth/login` | Usuario de ejemplo y request completo | Camino feliz | 200; token no vacío, `expiresIn=3600` y perfil esperado. |
| `POST /auth/login` | Request JSON `null` | Borde/error | 400; `{"message":"Request is null"}`. |
| `POST /auth/login` | Username ausente o solo espacios | Borde/error | 400; `{"message":"Username is required"}`. |
| `POST /auth/login` | Password ausente o solo espacios | Borde/error | 400; `{"message":"Password is required"}`. |
| `POST /auth/login` | Username/password con comilla simple | Borde | 401; no se altera la consulta SQL. |
| `POST /auth/login` | JSON malformado | Error | 400; cuerpo vacío (0 bytes), según Quarkus observado. |
| `POST /auth/login` | Credenciales incorrectas o usuario inexistente | Error | 401; mensaje genérico que incluye el username recibido. |
| `POST /auth/login` | Método GET | Error | 405; cuerpo vacío (0 bytes). |
| `POST /auth/login` | `Accept: text/plain` | Error | 406; cuerpo vacío (0 bytes). |
| `POST /auth/login` | `Content-Type: text/plain` | Error | 415; cuerpo vacío (0 bytes). |
| `POST /auth/login` | Excepción de autenticación simulada | Error | 500; `{"message":"An unexpected error occurred"}` y sin campo `exception`. Se prueba invocando `AuthResource` directamente. |
| `POST /auth/login` | Preflight CORS desde origen no listado | Seguridad/regresión | El test caracteriza el comportamiento actual permisivo; no implica que CORS abierto sea deseable. |
| `POST /auth/login` | Hash seeded, salt y migración heredada | Seguridad/regresión | Hash PBKDF2 verificado; migración conserva capacidad de login. |

## Codigo de los tests

Fuentes canonicas: `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y `auth/src/test/java/com/bcp/workshop/auth/AuthServiceTest.java`.

### `AuthResourceTest.java`

```java
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
```

### `AuthServiceTest.java`

```java
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
```

## Comandos y ejecuciones

Directorio de trabajo: `auth/`. Se uso una base aislada para no leer ni alterar `data/workshop.db`:

```powershell
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/auth-contract-tests.db'
mvn test
```

- **Salida real:** codigo de salida 0; 17 pruebas, 0 fallos, 0 errores, 0 omitidas.

Verificacion final, que vuelve a ejecutar las pruebas, construye Quarkus y genera JaCoCo:

```powershell
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/auth-contract-tests.db'
mvn verify
```

- **Salida real:** codigo de salida 0; 17 pruebas, 0 fallos, 0 errores, 0 omitidas; `BUILD SUCCESS`.
- **Reporte:** `auth/target/site/jacoco/index.html` y `auth/target/site/jacoco/jacoco.csv`.
- **Metodo:** JaCoCo 0.8.12; sumar `LINE_COVERED` y `LINE_MISSED` del CSV y calcular `LINE_COVERED / (LINE_COVERED + LINE_MISSED)`. Clases productivas tocadas: 118/124 lineas, **95,16%**. Bundle completo: 120/126 lineas, **95,24%**. La meta de 85% se supera en ambos calculos.

La llamada GET de health y los casos de protocolo se validan en `@QuarkusTest`. La excepcion 500 se simula invocando `AuthResource.login` directamente, pues la suite no incluye un mecanismo de mock HTTP para sustituir `AuthService`.

## Discrepancias encontradas

- La primera prueba de borde esperaba `405` para `POST /q/health`, siguiendo la version previa de `docs/ENDPOINTS.md`. Quarkus devolvio `200` y el reporte `UP`; se corrigieron el contrato y el test para registrar el comportamiento real. No se cambio codigo productivo para ocultar la discrepancia.
- No quedan fallos en la ultima ejecucion verificada.

## Pendientes

- No hay un health check personalizado que permita producir y comprobar `DOWN`/`503`; su estado y cuerpo siguen sin evidencia.
- El caso 500 prueba status/cuerpo en el metodo del recurso, no una solicitud HTTP de extremo a extremo.
- H-AUTH-05 (advisories SCA), compatibilidad de token, origenes CORS aprobados y ratificacion del contrato permanecen pendientes en `HALLAZGOS.md`.

## Pruebas propuestas

- Cuando se incorpore un health check real, añadir casos `DOWN`/`503` con su cuerpo observado y actualizar `docs/ENDPOINTS.md`.
- Si se incorpora soporte de mock del servicio en pruebas Quarkus, mover la simulacion 500 a una solicitud HTTP end-to-end sin cambiar el comportamiento documentado.

## Documentos consultados

- `README.md`
- `RETO.md`
- `docs/GOOD_PRACTICES.md`
- `docs/ENDPOINTS.md`
- `HALLAZGOS.md`
- `PLAN.md`
- `auth/pom.xml`
- `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java`
- `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`
- `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`
- `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java`
- `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
- `auth/src/main/resources/application.properties`
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`
- `auth/src/test/java/com/bcp/workshop/auth/AuthServiceTest.java`
