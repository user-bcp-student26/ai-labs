# Hallazgos

## auth

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-01 | auth | Credenciales concatenadas en SQL | Vulnerabilidad | `username` y `password` se interpolaban en SQL; la consulta ahora usa `PreparedStatement` con valores enlazados y conserva los status observados. | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:46-54 | alta | Resuelto |
| H-AUTH-02 | auth | Contraseñas almacenadas en texto plano | Vulnerabilidad | La migracion actualiza filas una por una y `initialize()` captura excepciones sin impedir el arranque; un fallo puede dejar filas en claro y la migracion parcialmente aplicada. | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:39-41,74-92; auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:13-89 | alta | CANDIDATO |
| H-AUTH-03 | auth | Token codificado en Base64 con secreto embebido | Vulnerabilidad | Base64 no firma ni cifra el contenido; username, rol, expiracion y secreto quedan codificados, por lo que el token no acredita integridad criptografica. | auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15 | alta | CANDIDATO |
| H-AUTH-04 | auth | Secreto en codigo y CORS con origenes permisivos | Vulnerabilidad | El secreto esta fijo en Java y la expresion CORS `/.*/` acepta cualquier origen; el alcance real del riesgo depende de como los clientes y consumidores usen el token. | auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8; auth/src/main/resources/application.properties:4-7 | media | CANDIDATO |
| H-AUTH-05 | auth | Dependencias con versiones candidatas a revision SCA | Sugerencia | Quarkus 3.27.4 y Commons Text 1.9 estan declarados. Sin consultar advisories no se puede afirmar que tengan una vulnerabilidad aplicable ni asignar un CVE. | auth/pom.xml:13,49-50 | media | Pendiente |
| H-AUTH-06 | auth | Respuesta de error expone detalles internos | Vulnerabilidad | El cliente recibe un mensaje generico con status 500; los detalles quedan en el log del servidor. | auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:50-52 | media | Resuelto |
| H-AUTH-07 | auth | Resource concentra validacion, persistencia, token y respuesta HTTP | Refactor | La validacion HTTP queda en `AuthResource`, la autenticacion y mapeo en `AuthService`, la persistencia en `LegacyDatabase` y la salida en DTO `LoginResponse`. | auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:27-53; auth/src/main/java/com/bcp/workshop/auth/AuthService.java:18-33; auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4 | baja | Resuelto |
| H-AUTH-08 | auth | Falta contrato acordado de `POST /auth/login` | Sugerencia | `docs/ENDPOINTS.md` documenta el contrato observado, pero aun no hay confirmacion de que sea el contrato acordado con sus consumidores. | docs/ENDPOINTS.md; auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:12-53; auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:29-126 | baja | Pendiente |
| H-AUTH-09 | auth | Cuentas predeterminadas se crean al inicializar la base de datos | Vulnerabilidad | La inicializacion inserta cuentas predefinidas si no existen; si se despliega una base nueva y el login queda accesible con esas cuentas, terceros podrian autenticarse como usuarios predeterminados. El impacto depende de la configuracion real de despliegue. | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:21-38 | media | CANDIDATO |
| H-AUTH-10 | auth | Fallos de base de datos se reportan como credenciales invalidas | Defecto | `findByCredentials()` captura errores de persistencia y devuelve `null`, que `AuthResource.login()` convierte en 401; una indisponibilidad de DB se vuelve indistinguible de un rechazo de credenciales. | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:46-60; auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:43-46 | media | CANDIDATO |

No se omitieron candidatos de auth de `RETO.md`: los siete tienen evidencia en el codigo. H-AUTH-05 confirma las versiones declaradas, no la existencia de advisories aplicables.

### Contrato observado, pendiente de confirmacion

- `POST /auth/login` consume JSON con `username` y `password`; valida solicitud, nombre y contraseña ausentes/vacios con 400 (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:24-34`; campos en `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-7`).
- Credenciales no encontradas producen 401; el camino exitoso produce 200 con `token`, `expiresIn`, `user` y `debug` (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-49`).
- La persistencia usa `AUTH_DB_URL` o `jdbc:sqlite:./data/workshop.db`; inicializa usuarios de ejemplo y busca credenciales con SQL concatenado (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:15,21-24,31-42`).
- El token Base64 incluye username, rol, expiracion y el secreto literal (`auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15`).
- CORS esta habilitado con origen `/.*/`; la aplicacion configura puerto 8080 (`auth/src/main/resources/application.properties:1-7`).
- Un error no controlado puede producir 500 con mensaje y clase de excepcion en el cuerpo (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:54-58`).

Las pruebas no se ejecutaron durante este Discovery; el test existente solo caracteriza el login exitoso (`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:13-20`).

### Candidatos de Discovery (pendientes de revision humana)

#### H-AUTH-02 — Migracion de contraseñas puede quedar parcial
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:30-42,74-92`; `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:13-89`. La migracion actualiza cada fila por separado y el inicializador captura la excepcion.
- Impacto: un fallo durante la migracion puede dejar registros en formatos distintos y permitir que el servicio arranque con credenciales que no se autentican correctamente; las filas aun no migradas podrian conservar contraseñas en claro.
- Prioridad propuesta: Alta.
- Contrato potencialmente afectado: autenticacion y disponibilidad de `POST /auth/login` durante o despues de la inicializacion.
- Como validarlo: en una base aislada con varias filas heredadas, inyectar un fallo en una actualizacion intermedia e inspeccionar si quedan filas sin migrar y si el servicio sigue aceptando solicitudes.

##### Investigación del issue #22
- Issue: [#22 — Migracion de contraseñas puede quedar parcial](https://github.com/user-bcp-student26/ai-labs/issues/22), abierto. Describe que la migracion actualiza filas individualmente y que el inicializador captura excepciones; advierte que un fallo podria dejar formatos mixtos y contraseñas en claro.
- Baseline examinado: `6917fc52b2383b19cdafbbde6043af3fb88ddd43`; HEAD local confirmado en ese SHA y sin diferencias en los dos archivos auth relevantes.
- Hechos: `LegacyDatabase.initialize()` llama a la migracion y captura excepciones (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:30-43`); la migracion itera las filas pendientes y ejecuta una actualizacion por fila (`:74-92`). Existe una prueba de migracion exitosa y login (`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:289-323`), pero no prueba un fallo intermedio. Esta prueba y la inicializacion usan `AUTH_DB_URL`, cuyo valor por defecto es `jdbc:sqlite:./data/workshop.db` (prueba `:25-27`; produccion `LegacyDatabase.java:19`).
- Hipótesis/no confirmado: un error durante una actualización podría dejar una migración parcial; no se identificó un paso de fallo reproducible ni se comprobó el efecto sobre el arranque o el login.
- Esperado vs. actual: el issue implica que un fallo no debe dejar contraseñas parcialmente migradas ni permitir un arranque engañoso, pero no define un criterio formal. El código inspeccionado captura la excepción; el resultado en ejecución no se comprobó.
- Texto SQL: el contenido obtenido por GitHub MCP y la lectura local disponible muestran `UPDATE legacy_users SET ****** WHERE username=?` en `LegacyDatabase.java:86-87`. No se puede determinar si los asteriscos son una redacción de MCP/herramienta o el contenido literal; por ello no se afirma que la sentencia sea válida ni inválida.
- Reproducción revisada: `python tools/lab.py test auth` ejecutaría `mvn verify` en `auth`, incluyendo la prueba de migración exitosa; no inyecta el fallo reportado. Las pruebas usan la base indicada por `AUTH_DB_URL` y, si no se aísla, pueden tocar `data/workshop.db`; además generan resultados de pruebas/cobertura en `target`. Resultado: **no comprobado**, comando no ejecutado por falta de un escenario directo de fallo y de una base de datos de prueba aislada verificada. La reproducción requeriría varias filas heredadas en una DB aislada y un fallo controlado durante una actualización, sin configurar, editar ni generar datos para esta investigación.

#### H-AUTH-03 — El token emitido no acredita integridad criptografica
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15`; el metodo construye el contenido del token y solo lo codifica en Base64.
- Impacto: el contenido codificado puede modificarse sin que este componente proporcione una comprobacion criptografica de integridad; el efecto para los clientes depende de como validen el token.
- Prioridad propuesta: Alta.
- Contrato potencialmente afectado: campo `token` de la respuesta exitosa de `POST /auth/login` y su formato esperado por consumidores.
- Como validarlo: en pruebas aisladas, emitir y decodificar un token, alterar uno de sus campos y comprobar si el mecanismo consumidor realmente rechaza la alteracion; confirmar tambien el formato esperado con los consumidores.

#### H-AUTH-04 — Secreto fijo y CORS acepta origenes no configurados
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8`; `auth/src/main/resources/application.properties:4-7`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:221-229` prueba una preflight desde un origen no listado.
- Impacto: el secreto no se puede rotar mediante configuracion externa y la politica CORS no restringe los origenes; el riesgo efectivo depende de la utilizacion del token y de los clientes.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: configuracion de CORS y consumidores del token de `POST /auth/login`.
- Como validarlo: comprobar en una prueba de configuracion que no existe secreto fijo en el codigo y probar solicitudes CORS con origen permitido y no permitido segun el allowlist acordado.

#### H-AUTH-09 — Cuentas predeterminadas se crean al inicializar
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:21-38` declara cuentas fijas y las inserta durante `initialize()`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:230-245` verifica la cuenta predeterminada de prueba.
- Impacto: si un despliegue accesible inicializa una base nueva con esas cuentas, alguien que conozca las credenciales de taller podria iniciar sesion como una de ellas. No se verifico si los despliegues reales usan esta inicializacion.
- Prioridad propuesta: Media, condicionada a la configuracion de despliegue.
- Contrato potencialmente afectado: quienes pueden autenticarse mediante `POST /auth/login` en una instancia recien inicializada.
- Como validarlo: inicializar una base temporal vacia, verificar las cuentas creadas y probar el login con los datos de prueba aislados; revisar la configuracion de cada despliegue para determinar si esta inicializacion esta habilitada y si el endpoint es accesible.

##### Investigación del issue #23
- Issue: [#23 — Cuentas predeterminadas se crean al inicializar](https://github.com/user-bcp-student26/ai-labs/issues/23), abierto y sin comentarios. El reporte indica que hay cuentas fijas insertadas durante la inicializacion y advierte que podrian permitir acceso en un despliegue accesible; no afirma haber verificado un despliegue real.
- Baseline examinado: el issue no cita un SHA. Se consulto el commit mas reciente disponible mediante GitHub MCP, `6917fc52b2383b19cdafbbde6043af3fb88ddd43`, solo como referencia de investigacion. El HEAD local coincide exactamente; los archivos auth examinados estaban limpios y la reproduccion se ejecuto en ese SHA.
- Hechos: `LegacyDatabase` declara cuentas de taller fijas (`LegacyDatabase.java:20-27`) y `initialize()` las inserta al crear la tabla (`:29-38`). `insertDefaultUser()` aplica `PasswordHasher.hash()` antes de persistir la contrasena (`:63-70`); una prueba verifica que se almacena un hash (`AuthResourceTest.java:244-257`). La prueba `logsInWithWorkshopUser()` envia las credenciales de prueba y exige HTTP 200 para el usuario `student` (`AuthResourceTest.java:56-68`). La existencia de hashes no elimina el hecho de que las credenciales predeterminadas son conocidas por quien tenga acceso al codigo o al material del taller.
- Hipotesis/no confirmado: el riesgo fuera del entorno de prueba depende de que un despliegue inicialice una base nueva con esas cuentas, permita acceso al endpoint y no cambie las credenciales. No se inspeccionaron despliegues ni configuraciones operativas; su exposicion real permanece desconocida.
- Reproduccion: precondicion: SHA local exacto y base SQLite aislada. Desde `auth`, se ejecuto `$env:AUTH_DB_URL='jdbc:sqlite:./target/issue-23-repro.db'; mvn -o '-Dquarkus.http.test-port=0' '-Dtest=AuthResourceTest#logsInWithWorkshopUser' test`. Esperado: la prueba de login comprueba HTTP 200 y el rol `STUDENT` tras inicializar la base vacia. Observado: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` (salida 0). Una ejecucion inicial sin puerto aleatorio no arranco porque el puerto 8081 ya estaba ocupado; se reintento con `quarkus.http.test-port=0`. Resultado: **reproducido en el baseline local** para la autenticacion con una cuenta predeterminada; no demuestra la exposicion de ningun despliegue real.
- Salida local: comando y resultado registrados aqui; la DB aislada `auth/target/issue-23-repro.db` se elimino tras la prueba. No se conservaron credenciales, datos personales ni salida sensible.

#### H-AUTH-10 — Un error de persistencia se confunde con credenciales invalidas
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:46-60` captura excepciones y devuelve `null`; `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:43-46` mapea `null` a 401. La prueba de error interno en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:198-216` simula una excepcion que se propaga desde el servicio, no un fallo capturado por `LegacyDatabase`.
- Impacto: una DB inaccesible puede devolver 401 en vez de indicar un error interno, dificultando el diagnostico operativo y presentando una respuesta distinta de la causa real.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: codigos HTTP de `POST /auth/login` para credenciales invalidas frente a fallos internos.
- Como validarlo: configurar una URL de DB invalida en un entorno de prueba aislado y comparar la respuesta de login con el caso de credenciales invalidas; confirmar si el fallo se registra y se distingue como error interno.

Detener aqui para revision humana; los candidatos no estan aprobados como hallazgos confirmados.

## api

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | El filtro de `/api/tasks` concatena `owner` en SQL | Vulnerabilidad candidata | El valor opcional `owner` de la solicitud se incorpora a la consulta SQL por concatenacion. No se verifico el comportamiento en ejecucion ni el contrato de acceso entre propietarios; no se confirma explotabilidad ni impacto. | `api/app/main.py@ae23fee1402a4244699c6afdbffff173a836ccfd:67-71` | Por evaluar | CANDIDATO |
| H-API-02 | api | Datos de solicitudes se concatenan en consultas SQL | Vulnerabilidad candidata | `owner` en `GET /api/tasks` y `GET /api/summary`, y campos del cuerpo en `POST /api/tasks`, se insertan directamente en SQL ejecutado con SQLite. No se verificaron efectos, explotabilidad ni impacto en ejecucion. | [`api/app/main.py:68-71`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L68-L71), [`:78-85`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L78-L85), [`:91-93`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L91-L93) | Por evaluar | CANDIDATO |
| H-API-03 | api | Reporte genérico de posible inyección SQL sin escenario reproducible | Vulnerabilidad candidata | El issue apunta a `api/app/main.py`, donde las rutas citadas concatenan valores de solicitudes en SQL; el reporte no incluye una entrada ni un efecto observado. La explotabilidad e impacto siguen sin confirmarse. | [Issue #27](https://github.com/user-bcp-student26/ai-labs/issues/27); [`api/app/main.py:68-71`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L68-L71), [`:78-85`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L78-L85), [`:91-93`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L91-L93) | Por evaluar | CANDIDATO |

### Investigación del issue #24

- Issue: [#24 — Posible issue Seguridad en conexion a base de datos](https://github.com/user-bcp-student26/ai-labs/issues/24), abierto y sin comentarios. El cuerpo solo señala `api/app/main.py`, `@app.get("/api/tasks")` y un impacto de seguridad; no incluye pasos, entrada, resultado observado ni criterio esperado.
- Baseline MCP: `ae23fee1402a4244699c6afdbffff173a836ccfd` (commit `Initial commit`, unico commit devuelto para `api/app/main.py`). El issue no cita SHA; este se usa como referencia de codigo, no se afirma que sea la version del incidente. GitHub MCP devolvio el archivo en ese SHA (blob `08cfbaef5e0518e2d7cbd93e44bc569e2d2ee275`).
- Hechos: en ese baseline, el endpoint admite `owner` como parametro opcional; si no se proporciona, usa el nombre de usuario autenticado (`:67-69`). La consulta SQL interpola `requested_owner` y se ejecuta sin parametros enlazados (`:70-71`). El endpoint exige `legacy_identity`. Las pruebas del baseline cubren el listado ordinario y el rechazo de token invalido (`api/tests/test_api.py:19-24,54-59`), no el manejo de entradas de `owner`.
- Esperado vs. observado: el issue no especifica ninguno. El codigo confirma la concatenacion, pero no se observo una respuesta HTTP ni un efecto sobre la base de datos.
- Hipótesis pendiente: una entrada especialmente formada en `owner` podria alterar la consulta; el efecto y la explotabilidad no se comprobaron. La regla de autorizacion para consultar propietarios distintos tampoco esta definida por el issue.
- Estado local: `git rev-parse HEAD` devolvio `6917fc52b2383b19cdafbbde6043af3fb88ddd43`, distinto del SHA MCP. `git status --short --untracked-files=normal` muestra `api/app/main.py` modificado, por lo que el componente local no esta limpio ni corresponde al baseline investigado. `git diff -- api/app/main.py` confirma una diferencia en la ejecucion de esa consulta. No se hizo checkout, reset ni cambio de archivos de producto.
- Reproducción: **no comprobada**. No se ejecutaron pruebas: el SHA exacto no esta disponible como HEAD local limpio y ejecutar la suite desde el arbol actual no verificaria ese baseline. Ademas, el modulo crea una app al importarse y su inicializacion usa rutas locales por defecto para SQLite y uploads; no se confirmo un aislamiento seguro de esos efectos. Comandos de inspeccion ejecutados: `git rev-parse HEAD`, `git status --short`, `git status --short --untracked-files=normal`, `git diff -- api/app/main.py`. Ningun comando de prueba fue ejecutado.
- Para continuar harian falta el resultado esperado y observado o pasos reproducibles del reporte, y un checkout local limpio del SHA MCP con almacenamiento de prueba aislado; no se modifico el issue ni el codigo de producto.

### Investigación del issue #26

- Issue: [#26 — Posible issue Seguridad en conexion a base de datos por inyeccion](https://github.com/user-bcp-student26/ai-labs/issues/26), abierto y sin comentarios. Cuerpo (dos líneas): `Evidencia: en clase python ai-labs\api\app\main.py` / `Impacto: Issue de seguridad por inyeccion`. No proporciona pasos, valores de entrada, salida observada ni resultado esperado.
- Componente: `api`, identificado por la ruta `api/app/main.py` del issue y descrito en la documentación raíz como API Python/FastAPI.
- Baseline MCP: `79d18ab31ab15052cfca3e72e56d2f512ece5776`, commit `day 3` (2026-10-28 16:00:58 UTC), último commit listado para `api/app/main.py` y anterior a la creación del issue (16:13:34 UTC). El issue no cita SHA; este se usa como referencia examinada, no como una versión confirmada del incidente. `get_commit` y la lectura MCP del archivo fijada al SHA confirman el SHA completo; el blob de `api/app/main.py` es `08cfbaef5e0518e2d7cbd93e44bc569e2d2ee275`.
- Estado local: `git rev-parse HEAD` devolvió el mismo SHA completo. Antes de decidir sobre pruebas, `git status --porcelain=v1 --untracked-files=all` no mostró cambios; los blobs locales de `api/app/main.py`, `api/tests/test_api.py` y `api/pyproject.toml` coinciden con los hashes devueltos por GitHub MCP para ese SHA. No se hizo checkout, fetch ni cambio en código, pruebas o configuración.
- Hechos: en [`api/app/main.py:68-71`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L68-L71), `owner` se usa para formar la consulta de `GET /api/tasks`, que se ejecuta mediante `connection.execute(sql)`. En [`:78-85`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L78-L85), campos del cuerpo se concatenan en el `INSERT` de `POST /api/tasks`. En [`:91-93`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L91-L93), `owner` se concatena en la consulta de `GET /api/summary`. Las pruebas fijadas a ese SHA cubren el listado ordinario, creación/resumen ordinarios, carga de archivo y token inválido ([`api/tests/test_api.py:19-59`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/tests/test_api.py#L19-L59)); no hay un caso que compruebe el síntoma de inyección descrito genéricamente por el issue.
- Hechos e interpretación: el código muestra concatenación de campos de solicitudes en sentencias SQL, lo que respalda clasificarlo como candidato a inyección. No se observó en ejecución que una entrada altere la consulta ni se verificaron autorización, impacto o alcance.
- Hipótesis pendiente: alguna entrada de solicitud podría cambiar la semántica de una consulta. Para confirmarla faltan una solicitud/reproducción reportada y su señal observable; el issue no especifica ninguno.
- Esperado vs. observado: el issue no define resultado esperado ni observado. Observación estática: se construyen SQL mediante concatenación en las tres rutas citadas. Resultado dinámico: desconocido.
- Reproducción: **no comprobada**; no se ejecutó ningún comando de prueba. Se revisó `api/tests/test_api.py` y no contiene una prueba que sondee la inyección descrita. La suite existente no probaría el síntoma por sí sola, y el reporte no aporta una entrada ni un criterio de resultado con que elegir una prueba existente pertinente; por ello no se ejecutó una prueba irrelevante ni se creó/alteró una. No se usaron servicios externos ni se instalaron dependencias.
- Validación y evidencia: MCP de solo lectura para issue, comentarios, historial/commit y archivos del componente en el SHA indicado. Inspección local de HEAD, estado del árbol y hashes de archivos; no se ejecutaron pruebas. Este registro es la evidencia local; los cambios se limitan a `HALLAZGOS.md`.

### Investigación del issue #27

- Issue: [#27 — Posible issue Seguridad en conexion a base de datos por inyeccion sql](https://github.com/user-bcp-student26/ai-labs/issues/27), estado `open`, sin comentarios (`get_comments` devolvió una lista vacía). Cuerpo exacto:
  > Evidencia: revisar posible riesgo de inyeccion de sql clase python ai-labs\api\app\main.py<br>
  > Impacto: Atque mediante inyeccion sql
- Componente: `api`, indicado por la ruta `api\app\main.py` en el cuerpo y correspondiente a la sección API de las instrucciones del repositorio.
- Baseline examinado: `79d18ab31ab15052cfca3e72e56d2f512ece5776` (`day 3`, 2026-10-28 16:00:58 UTC). El issue no cita una versión; se eligió el commit local `HEAD`, confirmado por GitHub MCP y anterior a la creación del issue. La lectura MCP de `api/app/main.py` fijada a ese SHA informó blob `08cfbaef5e0518e2d7cbd93e44bc569e2d2ee275`.
- Hechos:
  - En [`api/app/main.py:68-71`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L68-L71), `owner` de `GET /api/tasks` se interpola en el SQL ejecutado por SQLite.
  - En [`api/app/main.py:78-85`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L78-L85), campos del cuerpo de `POST /api/tasks` se incorporan a la sentencia SQL; en [`api/app/main.py:91-93`](https://github.com/user-bcp-student26/ai-labs/blob/79d18ab31ab15052cfca3e72e56d2f512ece5776/api/app/main.py#L91-L93), `owner` se interpola en `GET /api/summary`. Esto acredita concatenación estática, no explotación.
  - La configuración de pytest apunta a `tests` (`api/pyproject.toml:1-3`). Las pruebas existentes usan una base y directorio de uploads bajo `tmp_path`, y cubren casos ordinarios y token inválido (`api/tests/test_api.py:19-59`); no especifican ni comprueban el síntoma descrito por el issue.
  - Alineación local: `git rev-parse HEAD` devolvió el mismo SHA; `git status --short -- api` y `git diff -- api` no mostraron cambios. Por tanto, el componente API local coincide con el baseline. El archivo `HALLAZGOS.md` ya tenía cambios locales antes de esta anotación; se conservaron.
- Hipótesis pendiente: alguna entrada podría alterar la semántica de una consulta debido a la interpolación descrita. El issue no identifica ruta concreta, entrada, respuesta observada, resultado esperado ni impacto; la explotabilidad no queda confirmada.
- Esperado vs. observado: desconocidos en el reporte. Observación estática en el código: concatenación en las tres rutas citadas. No se observó una solicitud ni efecto dinámico.
- Reproducción: **no comprobada**. No ejecuté pruebas: no existe en el issue una entrada o señal verificable para diseñar una prueba específica sin inventar el escenario; las pruebas existentes no prueban esa afirmación. No se generaron fixtures ni se modificaron pruebas. No se usaron servicios externos ni se instalaron dependencias.
- Comandos locales y resultados:
  - `git rev-parse HEAD` → `79d18ab31ab15052cfca3e72e56d2f512ece5776`.
  - `git status --short -- api` → sin salida (API sin cambios locales).
  - `git diff -- api` → sin salida (sin diferencias locales en API).
  - Pruebas → no ejecutadas; no se afirmó un resultado de reproducción.
- Consulta remota de solo lectura: `issue_read(get)`, `issue_read(get_comments)`, `list_commits(path="api/app/main.py")`, `get_commit` y `get_file_contents` fijados al SHA anterior. No se editó el issue ni el código del producto. Este registro queda en `HALLAZGOS.md`.