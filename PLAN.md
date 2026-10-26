# Plan de remediacion

## Alcance y seleccion

Se incluyen todos los hallazgos de auth que aparecen como `Pendiente` en `HALLAZGOS.md`: H-AUTH-01 a H-AUTH-08. Se priorizan primero los riesgos altos confirmados en codigo; las versiones SCA requieren confirmar advisories antes de proponer una actualizacion, y el token/CORS requieren validar compatibilidad y origenes con las partes consumidoras. Este plan es una propuesta; no representa aprobacion humana ni ejecucion de pruebas.

## Contrato observado a preservar

`docs/ENDPOINTS.md` documenta la linea base observada de `POST /auth/login`: JSON con `username` y `password`; solicitud o campos ausentes/vacios producen 400, credenciales no encontradas 401 y login exitoso 200 con `token`, `expiresIn`, `user` y `debug`. El status 500 tambien se conserva, pero el cuerpo que exponia excepciones era el problema a corregir. El formato interno del token no esta documentado como contrato acordado y no se inspeccionaron consumidores fuera de auth; validar su compatibilidad es un bloqueo previo a cambiarlo. No se inferiran origenes CORS aprobados. El contrato sigue pendiente de confirmacion por sus consumidores (H-AUTH-08).

## H-AUTH-01 — Credenciales concatenadas en SQL

- **Hallazgo:** H-AUTH-01, `Pendiente`; `username` y `password` se concatenan en la consulta ejecutada con `Statement`.
- **Problema:** las credenciales pueden modificar la estructura SQL y alterar la autenticacion.
- **Cambio propuesto:** sustituir la consulta por `PreparedStatement` con placeholders y enlazar usuario y contraseña mediante setters; mantener el mismo resultado de login y el mismo contrato HTTP.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y, si se necesita aislar persistencia, `auth/src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java`.
- **Caracterizacion antes del cambio:** probar credenciales ficticias validas (200), contraseña invalida (401), y username o password con comilla simple (401, sin 500). Usar una base SQLite temporal y verificar que la entrada se trate como dato, no como sintaxis SQL.

## H-AUTH-02 — Contraseñas en texto plano

- **Hallazgo:** H-AUTH-02, `Pendiente`; el esquema y los usuarios de ejemplo guardan contraseñas directamente.
- **Problema:** una copia o lectura de SQLite revelaria contraseñas reutilizables.
- **Cambio propuesto:** guardar hashes lentos con salt y verificar mediante una abstraccion de hashing; definir migracion segura de filas existentes y de los usuarios ficticios, sin registrar contraseñas. Justificar cualquier dependencia de hashing antes de agregarla.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`, `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java` o el servicio que asuma la verificacion, `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`, y `auth/pom.xml` solo si la solucion elegida requiere una dependencia justificada.
- **Caracterizacion antes del cambio:** probar el usuario ficticio sembrado con su contraseña valida (200 y campos de respuesta existentes) y una contraseña incorrecta (401); usar base temporal y no exponer credenciales en logs.

## H-AUTH-03 — Token Base64 con secreto embebido

- **Hallazgo:** H-AUTH-03, `Pendiente`; el token se forma con datos legibles y se codifica en Base64 sin firma.
- **Problema:** Base64 no protege integridad ni autenticidad; un consumidor que confie en el contenido no puede detectar modificaciones.
- **Cambio propuesto:** emitir un token firmado, con expiracion y claims explicitos. Mantener el campo `token` y los demas campos de la respuesta exitosa. Seleccionar mecanismo y dependencia solo tras revisar opciones Quarkus existentes y justificar cualquier dependencia nueva.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` (o reemplazo dedicado), `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`, `auth/src/main/resources/application.properties`, `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y, si se justifica, `auth/pom.xml`.
- **Caracterizacion antes del cambio:** probar que login valido devuelve 200, token no vacio, `expiresIn` y los campos de `user`; probar credenciales invalidas con 401. No fijar como contrato el contenido Base64 actual.
- **Pendiente previo:** coordinar compatibilidad del formato firmado con sus consumidores; estos no se leyeron por el alcance acordado.

## H-AUTH-04 — Secreto en codigo y CORS permisivo

- **Hallazgo:** H-AUTH-04, `Pendiente`; el secreto esta en una constante Java y CORS permite origenes mediante `/.*/`.
- **Problema:** el secreto no puede rotarse externamente y una politica CORS abierta permite solicitudes desde origenes no restringidos.
- **Cambio propuesto:** externalizar el secreto mediante configuracion y usar un allowlist CORS configurable con origenes aprobados. No incluir secretos reales ni asumir origenes no confirmados. Mantener el endpoint y su respuesta; tratar el estrechamiento de CORS como cambio de politica que requiere validar clientes.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`, `auth/src/main/resources/application.properties` y `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` o una prueba de configuracion HTTP dedicada.
- **Caracterizacion antes del cambio:** probar preflight CORS desde `http://localhost:4200` y registrar los headers actuales; comprobar que un origen no listado se acepta actualmente. Tras acordar el allowlist, probar origen aprobado y rechazo de origen no aprobado usando valores ficticios de test.
- **Pendiente previo:** confirmar los origenes permitidos con quien mantiene los clientes y despliegues.

## H-AUTH-05 — Versiones candidatas a revision SCA

- **Hallazgo:** H-AUTH-05, `Pendiente`; `auth/pom.xml` declara Quarkus 3.27.4 y Commons Text 1.9, sin advisory confirmado en este Discovery.
- **Problema:** si un advisory aplicable afecta las versiones resueltas, el servicio podria quedar expuesto; la version declarada por si sola no demuestra una vulnerabilidad.
- **Cambio propuesto:** revisar advisories y el arbol de dependencias directas/transitivas. Actualizar, reemplazar o remover solo lo afectado y con evidencia; no silenciar scanners ni cambiar versiones sin justificar.
- **Archivos a tocar:** `auth/pom.xml` solo si la evidencia requiere un cambio; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` para regresion del contrato.
- **Caracterizacion antes del cambio:** ejecutar el test de login exitoso existente y ampliarlo con credenciales invalidas/status 401 antes de actualizar dependencias. Como verificacion SCA separada, registrar arbol resuelto y resultado del scanner/advisory usado.

## H-AUTH-06 — Detalles internos en errores

- **Hallazgo:** H-AUTH-06, `Pendiente`; el 500 devuelve el mensaje y la clase de la excepcion.
- **Problema:** la respuesta puede revelar detalles internos al cliente.
- **Cambio propuesto:** devolver mensaje generico y status 500; enviar detalles completos solo al log del servidor con logging controlado y sin credenciales ni secretos.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java` y `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterizacion antes del cambio:** provocar una excepcion ficticia de persistencia en una prueba unitaria de `AuthResource` y registrar el status 500, `message` y `exception` actuales. Despues, mantener 500 y verificar que el cuerpo no revele el mensaje ni la clase.

## H-AUTH-07 — Responsabilidades mezcladas en el Resource

- **Hallazgo:** H-AUTH-07, `Pendiente`; `login` valida, consulta persistencia, crea token y arma la respuesta.
- **Problema:** el acoplamiento dificulta pruebas aisladas y aumenta el riesgo de cambios de contrato durante refactors.
- **Cambio propuesto:** separar HTTP, reglas de autenticacion y acceso a datos siguiendo Resource → Service → Repository; mapear respuesta a DTO propio. Usar guard clauses, metodos de una responsabilidad y constantes con nombre, evitando duplicacion y anidamiento profundo.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`, `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`, y clases nuevas acotadas bajo `auth/src/main/java/com/bcp/workshop/auth/` para servicio, repositorio o DTO solo si eliminan responsabilidades reales; pruebas en `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterizacion antes del cambio:** ampliar primero las pruebas del recurso para solicitud nula, username/password ausentes o vacios (400), credenciales invalidas (401), login valido (200 y forma de respuesta) y excepcion interna (500). Ejecutar la misma suite tras cada extraccion.

## H-AUTH-08 — Contrato de login no documentado

- **Hallazgo:** H-AUTH-08, `Pendiente`; `docs/ENDPOINTS.md` registra el comportamiento observado, pero falta contrastarlo y acordarlo con los consumidores.
- **Problema:** una refactorizacion podria cambiar accidentalmente status, campos o datos de respuesta que un cliente espera.
- **Cambio propuesto:** solicitar confirmacion del contrato documentado a la persona responsable y registrar por separado contrato observado, contrato acordado y riesgos pendientes. `docs/ENDPOINTS.md` contiene el observado; no se afirma que haya sido aprobado.
- **Archivos a tocar:** `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; actualizar `docs/ENDPOINTS.md` solo si la confirmacion acuerda cambios.
- **Caracterizacion antes del cambio:** crear pruebas de request nulo, campos obligatorios, credenciales invalidas y validas; verificar status y estructura de respuesta observados en `HALLAZGOS.md`. No afirmar que esos comportamientos fueron acordados hasta obtener confirmacion.

## Secuencia, verificaciones y pendientes

1. Escribir y ejecutar las pruebas de caracterizacion antes de modificar codigo; usar base SQLite temporal y datos ficticios.
2. Resolver primero el contrato y las dependencias externas: compatibilidad del token, origenes CORS aprobados y advisories SCA aplicables. Registrar decisiones humanas; no se presumen aprobaciones.
3. Implementar en cambios revisables: consultas parametrizadas; hashing y migracion; token/configuracion; errores; separacion de capas. Reejecutar pruebas del slice tras cada cambio.
4. Ejecutar `mvn test` desde `auth/` para regresion y `mvn verify` desde `auth/` cuando se necesite el reporte JaCoCo. Registrar resultados reales en el paso de testing; ninguno se ejecuto al crear este plan.

La linea base del endpoint es observada, no acordada. No se inspeccionaron consumidores fuera de `auth/` y no se consultaron advisories externos. La aprobacion humana registrada cubre todos los IDs, pero no sustituye las coordinaciones de compatibilidad y configuracion pendientes.

### Aprobacion Humana
- cpuente
- 2026/10/26
- todos

## Registro de ejecuciones

- **Componente/IDs:** auth; H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-06, H-AUTH-07, H-AUTH-08.
- **Antes del cambio:** `mvn test` desde `auth/`, con `AUTH_DB_URL=jdbc:sqlite:./target/auth-characterization.db`; 6 pruebas ejecutadas, 0 fallos, 0 errores. Caracteriza login valido y sus campos de respuesta, credenciales invalidas y con comilla, campos obligatorios, exposicion actual de excepcion y CORS abierto.
- **Intento previo:** `mvn test` desde la raiz fallo porque no hay `pom.xml` en ese directorio; no ejecuto pruebas. Se repitio desde `auth/` correctamente.
- **Despues del cambio H-AUTH-01:** `mvn test` desde `auth/`, con la misma SQLite temporal; 6 pruebas ejecutadas, 0 fallos, 0 errores. Los casos de comilla conservaron 401 sin error SQL en la salida.
- **H-AUTH-02, caracterizacion previa:** `mvn test` desde `auth/` con la SQLite temporal; 8 pruebas ejecutadas, 0 fallos, 0 errores. Se confirmo contraseña en claro tanto en el usuario sembrado como en una fila heredada.
- **H-AUTH-02, despues del cambio:** `mvn test` desde `auth/` con la misma SQLite temporal; 8 pruebas ejecutadas, 0 fallos, 0 errores. Las pruebas confirman hashes PBKDF2 con salt, migracion de fila heredada y login con la contraseña original.
- **H-AUTH-06, caracterizacion previa:** en la suite previa, la prueba de error interno confirmo status 500 y exposicion de mensaje y clase de excepcion.
- **H-AUTH-06, despues del cambio:** `mvn test` desde `auth/` con SQLite temporal; 8 pruebas ejecutadas, 0 fallos, 0 errores. El status 500 se conserva, la respuesta contiene un mensaje generico y no incluye la clave `exception`; el detalle simulado aparece solo en el log del servidor.
- **H-AUTH-07, caracterizacion previa:** antes de la extraccion, la suite ampliada paso 8 pruebas, 0 fallos y 0 errores; cubria validacion, login, SQL parametrizado, hashing/migracion, error interno y CORS observado.
- **H-AUTH-07, despues del cambio:** `mvn test` desde `auth/` con SQLite temporal; 8 pruebas ejecutadas, 0 fallos, 0 errores. Se conserva `POST /auth/login`, los status caracterizados y los campos `token`, `expiresIn`, `user` y `debug`.
- **Cobertura final, componente/IDs:** auth; H-AUTH-01, H-AUTH-02, H-AUTH-06 y H-AUTH-07. `mvn verify` desde `auth/`; 11 pruebas ejecutadas, 0 fallos, 0 errores, build exitoso. JaCoCo (`auth/target/site/jacoco/jacoco.csv`) muestra 119/125 lineas cubiertas (95%) en las clases Java modificadas (`AuthResource`, `AuthService`, `LoginResponse`, `LegacyDatabase`, `LegacyTokenUtil`, `PasswordHasher`), por encima de la meta local del 85%. La cobertura global del bundle es 90%.
- **Estado de IDs tras esta ejecucion:** H-AUTH-01, H-AUTH-02, H-AUTH-06 y H-AUTH-07 se resolvieron y sus pruebas posteriores pasan. H-AUTH-03 sigue pendiente por compatibilidad del token no comprobada; H-AUTH-04 por origenes CORS aprobados no confirmados; H-AUTH-05 por advisories SCA no investigados; H-AUTH-08 porque el contrato acordado y el artefacto documental se atienden en pasos posteriores.
- **JaCoCo intermedio:** tras la primera separacion, el CSV marco `AuthService` con 0%; se trasladaron las pruebas directas del servicio a `AuthServiceTest` sin `@QuarkusTest`, y el `mvn verify` final ahora registra sus 15/15 lineas cubiertas.
- **Incidencia corregida:** la primera ejecucion posterior de H-AUTH-02 encontro un cierre de bloque duplicado en `LegacyDatabase.java`; se corrigio y la repeticion paso. No hubo cambios fuera de `auth/`.
- **CodeQL / Dependency Review remotos:** no ejecutados en este recorrido local.