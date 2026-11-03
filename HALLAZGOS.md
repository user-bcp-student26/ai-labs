# Hallazgos

## Contrato observado — API

`docs/ENDPOINTS.md` documenta el comportamiento observado en `api/app/main.py`;
no implica que el comportamiento inseguro observado sea un contrato acordado.

- **Autenticación:** las rutas requieren `Authorization: Bearer <token>`. El
  token se decodifica desde Base64 como `username|role|expiración|secreto`;
  actualmente se valida el secreto embebido y la expiración.
- **Persistencia:** SQLite usa `WORKSHOP_DB_PATH` o
  `./data/workshop.db`. Al inicializar crea la tabla `tasks` y agrega tres
  registros de ejemplo si está vacía; cierra la conexión al apagar la app.
- **Archivos:** el directorio usa `WORKSHOP_UPLOAD_DIR` o `./uploads`.
  `POST /api/requirements` recibe un archivo multipart y devuelve `owner`,
  `fileName`, `storedAt` y `size`.
- **`GET /api/tasks`:** acepta `owner` opcional y devuelve una lista con
  `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`.
- **`POST /api/tasks`:** crea una tarea (201) desde un objeto JSON; `status`,
  `priority` y `owner` tienen valores predeterminados.
- **`GET /api/summary`:** acepta `owner` opcional y devuelve `owner`, `total`,
  `byStatus` y `highPriorityOpen`.
- **`GET /api/backend-health`:** consulta `AUTH_HEALTH_URL` (por defecto
  `http://localhost:8080/q/health`) y devuelve el estado y cuerpo recibidos.
  Ante una excepción devuelve estado `0` y el texto de la excepción.

## Matriz de seguimiento

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | Token Base64 con secreto embebido | Vulnerabilidad | Base64 no firma el token; el secreto está en el código y la validación no comprueba firma, emisor ni audiencia. | api/app/main.py:14,19-26 | alta | CANDIDATO |
| H-API-02 | api | El cliente puede consultar o asignar otro `owner` | Vulnerabilidad | Las consultas aceptan `owner` del cliente y la creación permite sobrescribir el propietario del token, rompiendo el aislamiento por identidad. | api/app/main.py:68-70,78-84,91-93 | alta | CANDIDATO |
| H-API-03 | api | Filtros e inserts SQL concatenados | Vulnerabilidad | Los valores recibidos se incorporan directamente a consultas SQL; entradas con formato inesperado pueden alterar la consulta o impedir operaciones. | api/app/main.py:70,84,93 | alta | CANDIDATO |
| H-API-04 | api | Se conserva directamente el nombre y contenido del archivo | Vulnerabilidad | El nombre se usa para formar la ruta sin normalización; además se lee y guarda el contenido completo sin límite de tamaño ni validación de extensión. | api/app/main.py:107-111 | alta | CANDIDATO |
| H-API-05 | api | Versiones de dependencias requieren análisis SCA | Sugerencia | `urllib3` y `python-multipart` están fijadas a las versiones indicadas por el reto. Su estado frente a advisories no se verificó en este Discovery; no se afirma aquí que exista un CVE aplicable. | api/requirements.txt:4-5 | media | Pendiente |
| H-API-06 | api | Rutas, persistencia, reglas y serialización en un solo módulo | Refactor | La inicialización de SQLite y las rutas, consultas y mapeo de respuestas conviven en `main.py`, dificultando probar y cambiar cada responsabilidad por separado. | api/app/main.py:29-56,67-124 | media | Pendiente |
| H-API-07 | api | Tipado crudo, excepciones amplias y lógica anidada | Refactor | Los cuerpos y respuestas usan diccionarios sin modelos de dominio; hay capturas amplias y lógica condicional anidada, lo que reduce validación y claridad. Parte del texto de excepción se devuelve al cliente. | api/app/main.py:17-26,68,78,94-105,114-121 | media | Pendiente |

## Candidatos priorizados para revisión humana — API

Estos cuatro candidatos corresponden a los hallazgos de severidad alta con
evidencia directa en el código. `H-API-05` a `H-API-07` se conservan en la
matriz para seguimiento, pero no se incluyen en esta selección. El contrato de
`docs/ENDPOINTS.md` está descrito como observado, no como aprobado.

### H-API-01 — Token sin integridad criptográfica

- Estado: CANDIDATO
- Evidencia: `api/app/main.py:14,19-26` define un secreto en código, decodifica el token con Base64 y valida campos separados y expiración, sin verificar firma, emisor ni audiencia.
- Impacto: la API no puede comprobar criptográficamente que la identidad y el rol provienen de un emisor confiable; conocer el secreto embebido basta para construir un token que pase la comprobación actual.
- Prioridad propuesta: alta.
- Contrato potencialmente afectado: autenticación `Authorization` y respuestas `401` de las rutas protegidas. La coordinación del formato y del emisor del token sigue abierta en `docs/ENDPOINTS.md`.
- Cómo validarlo: agregar pruebas de aceptación y rechazo para token válido, expirado, alterado y con secreto incorrecto; antes de fijar claims o formato, acordar la transición con quien emite el token.

### H-API-02 — Lectura y asignación de tareas fuera de la identidad

- Estado: CANDIDATO
- Evidencia: `api/app/main.py:68-70,78-84,91-93` usa `owner` de query/body sin restringirlo a la identidad; `api/tests/test_api.py:103-123` contiene pruebas de aislamiento.
- Impacto: un usuario autenticado puede leer tareas ajenas, obtener su resumen o crear tareas asignadas a otro propietario.
- Prioridad propuesta: alta.
- Contrato potencialmente afectado: `GET /api/tasks?owner=...`, `GET /api/summary?owner=...` y `POST /api/tasks` con `owner`. Cambiar la autorización puede cambiar intencionalmente el comportamiento de solicitudes cross-owner; las rutas y formas de respuesta están documentadas como observadas.
- Cómo validarlo: ejecutar `python tools/lab.py test api` y mantener las pruebas que exigen aislamiento. En esta ejecución fallaron `test_tasks_do_not_expose_another_owners_records` y `test_created_task_cannot_be_assigned_to_another_owner`, ambas por aceptar `trainer` para la identidad `student`.

### H-API-03 — Valores de entrada interpolados en SQL

- Estado: CANDIDATO
- Evidencia: `api/app/main.py:70,84,93` concatena `owner` y campos del cuerpo en sentencias SQL.
- Impacto: los datos de solicitud pueden alterar la estructura o el resultado de consultas y escrituras, además de causar errores en operaciones ordinarias.
- Prioridad propuesta: alta.
- Contrato potencialmente afectado: filtros de propietario de `GET /api/tasks` y `GET /api/summary`, y creación mediante `POST /api/tasks`; deben conservarse endpoints, orden de listado y campos/valores de respuesta.
- Cómo validarlo: añadir pruebas con valores de entrada que incluyan comillas y caracteres SQL, comprobar que se tratan como datos literales y que no cambian el conjunto de filas ni la forma de respuesta; verificar que todas las consultas usan parámetros.

### H-API-04 — Nombre y tamaño de carga de archivo sin límites

- Estado: CANDIDATO
- Evidencia: `api/app/main.py:107-112` concatena `file.filename` al directorio de carga y lee el archivo completo en memoria antes de escribirlo; no se observa validación de ruta, extensión o tamaño.
- Impacto: un nombre controlado por el cliente puede dirigir la escritura fuera del directorio esperado o sobrescribir archivos; una carga grande puede consumir memoria y espacio en disco.
- Prioridad propuesta: alta.
- Contrato potencialmente afectado: `POST /api/requirements` multipart y su respuesta `owner`, `fileName`, `storedAt` y `size`. La lista de extensiones, tamaño máximo y respuesta de rechazo requieren acuerdo.
- Cómo validarlo: tras acordar límites y extensiones, probar nombres con separadores/rutas, extensiones no permitidas y contenido por encima del máximo; confirmar rechazo antes de escribir fuera del directorio y manejo acotado de memoria.

### Verificación ejecutada

- Comando: `python tools/lab.py test api`
- Resultado: 25 pasaron, 2 fallaron y 57 advertencias; cobertura global reportada: 98.21%.
- Los dos fallos son las pruebas de aislamiento de `H-API-02`; la suite no queda verde. Esta ejecución no valida todavía los escenarios de token alterado, SQL ni límites/rutas de archivos descritos arriba.
