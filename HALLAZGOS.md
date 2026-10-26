# Hallazgos

## auth

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-01 | auth | Credenciales concatenadas en SQL | Vulnerabilidad | `username` y `password` se interpolaban en SQL; la consulta ahora usa `PreparedStatement` con valores enlazados y conserva los status observados. | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:46-54 | alta | Resuelto |
| H-AUTH-02 | auth | Contraseñas almacenadas en texto plano | Vulnerabilidad | La migracion actualiza filas una por una y `initialize()` captura excepciones sin impedir el arranque; un fallo puede dejar filas en claro y la migracion parcialmente aplicada. | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:39-41,74-92; auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:13-89 | alta | Pendiente |
| H-AUTH-03 | auth | Token codificado en Base64 con secreto embebido | Vulnerabilidad | Base64 no firma ni cifra el contenido; username, rol, expiracion y secreto quedan codificados, por lo que el token no acredita integridad criptografica. | auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15 | alta | Pendiente |
| H-AUTH-04 | auth | Secreto en codigo y CORS con origenes permisivos | Vulnerabilidad | El secreto esta fijo en Java y la expresion CORS `/.*/` acepta cualquier origen; el alcance real del riesgo depende de como los clientes y consumidores usen el token. | auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8; auth/src/main/resources/application.properties:4-7 | media | Pendiente |
| H-AUTH-05 | auth | Dependencias con versiones candidatas a revision SCA | Sugerencia | Quarkus 3.27.4 y Commons Text 1.9 estan declarados. Sin consultar advisories no se puede afirmar que tengan una vulnerabilidad aplicable ni asignar un CVE. | auth/pom.xml:13,49-50 | media | Pendiente |
| H-AUTH-06 | auth | Respuesta de error expone detalles internos | Vulnerabilidad | El cliente recibe un mensaje generico con status 500; los detalles quedan en el log del servidor. | auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:50-52 | media | Resuelto |
| H-AUTH-07 | auth | Resource concentra validacion, persistencia, token y respuesta HTTP | Refactor | La validacion HTTP queda en `AuthResource`, la autenticacion y mapeo en `AuthService`, la persistencia en `LegacyDatabase` y la salida en DTO `LoginResponse`. | auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:27-53; auth/src/main/java/com/bcp/workshop/auth/AuthService.java:18-33; auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4 | baja | Resuelto |
| H-AUTH-08 | auth | Falta contrato acordado de `POST /auth/login` | Sugerencia | `docs/ENDPOINTS.md` documenta el contrato observado, pero aun no hay confirmacion de que sea el contrato acordado con sus consumidores. | docs/ENDPOINTS.md; auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:12-53; auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:29-126 | baja | Pendiente |

No se omitieron candidatos de auth de `RETO.md`: los siete tienen evidencia en el codigo. H-AUTH-05 confirma las versiones declaradas, no la existencia de advisories aplicables.

### Contrato observado, pendiente de confirmacion

- `POST /auth/login` consume JSON con `username` y `password`; valida solicitud, nombre y contraseña ausentes/vacios con 400 (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:24-34`; campos en `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-7`).
- Credenciales no encontradas producen 401; el camino exitoso produce 200 con `token`, `expiresIn`, `user` y `debug` (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-49`).
- La persistencia usa `AUTH_DB_URL` o `jdbc:sqlite:./data/workshop.db`; inicializa usuarios de ejemplo y busca credenciales con SQL concatenado (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:15,21-24,31-42`).
- El token Base64 incluye username, rol, expiracion y el secreto literal (`auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15`).
- CORS esta habilitado con origen `/.*/`; la aplicacion configura puerto 8080 (`auth/src/main/resources/application.properties:1-7`).
- Un error no controlado puede producir 500 con mensaje y clase de excepcion en el cuerpo (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:54-58`).

Las pruebas no se ejecutaron durante este Discovery; el test existente solo caracteriza el login exitoso (`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:13-20`).