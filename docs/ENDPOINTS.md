# Contrato HTTP — API FastAPI

## Estado del contrato

- **Contrato observado:** esta documentación refleja el código actual de
  `api/app/main.py`; no implica que el comportamiento inseguro observado sea un
  contrato aprobado.
- **Contrato acordado:** no se encontró un contrato previo acordado. Ninguna
  respuesta o protección adicional se presume.
- **Riesgos y preguntas abiertas:** los hallazgos `H-API-01` a `H-API-07`
  siguen `Pendiente` en `HALLAZGOS.md`. En particular, aún se debe acordar el
  emisor y la migración del token y la política de extensión/tamaño de archivos.
  El código actual todavía no implementa JWT ni límites para archivos.

## Convenciones comunes

- Base URL local documentada en `README.md`: `http://localhost:8000`.
- Las cinco rutas descritas requieren el header
  `Authorization: Bearer <token-vigente>`.
- **Token observado, no recomendado para producción:** la API decodifica un
  valor Base64 con la forma `username|role|expiration|secret`; compara el
  secreto con un valor embebido y verifica la expiración. No verifica firma,
  emisor ni audiencia JWT. La API no expone una ruta para emitir tokens. El
  valor `<token-vigente>` de los ejemplos debe sustituirse por uno válido para
  el entorno; el formato y la coordinación con el emisor son una pregunta
  abierta.
- El formato esperado para clientes es `Authorization: Bearer <token>`, pero
  el código no valida estrictamente el esquema `Bearer`: elimina esa cadena si
  aparece y también puede aceptar el valor Base64 sin prefijo.
- Un token ausente o inválido produce `401`. El detalle de `detail` puede
  contener el texto de una excepción de análisis del token.
- Los errores internos no se traducen en cada ruta: FastAPI devuelve `500
  Internal Server Error` con el cuerpo de texto `Internal Server Error` en la
  configuración observada. No es un contrato de error estable.
- CORS está configurado globalmente para aceptar cualquier origen, método y
  header, junto con credenciales (`api/app/main.py:49-54`).

## `GET /api/tasks`

### 1. Qué hace

Lista las tareas del propietario seleccionado para la consulta.

### 2. Autenticación

Requiere `Authorization: Bearer <token-vigente>`; véanse las convenciones
comunes.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `owner` (query) | string | No | Propietario que se filtra. Si falta o es una cadena vacía, se usa el `username` del token. El código actual también acepta un propietario distinto al del token. |

Ejemplo completo de request (sustituir el marcador por un token válido):

```http
GET /api/tasks?owner=student HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token-vigente>
```

### 4. Response exitosa

Estado: `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| respuesta | array de objetos | Tareas del propietario consultado. |
| `id` | integer | Identificador SQLite de la tarea. |
| `title` | string | Título. |
| `description` | string | Descripción. |
| `status` | string | Estado almacenado. |
| `owner` | string | Propietario almacenado. |
| `priority` | string | Prioridad almacenada. |
| `createdAt` | string | Fecha y hora SQLite (`datetime('now')`). |

Ejemplo completo observado:

```json
[
  {
    "id": 1,
    "title": "Refactor login",
    "description": "Separar responsabilidades en frontend",
    "status": "TODO",
    "owner": "student",
    "priority": "HIGH",
    "createdAt": "2026-11-02 22:08:33"
  },
  {
    "id": 2,
    "title": "Review API",
    "description": "Revisar seguridad de endpoints",
    "status": "IN_PROGRESS",
    "owner": "student",
    "priority": "MEDIUM",
    "createdAt": "2026-11-02 22:08:33"
  }
]
```

### 5. Respuestas de error posibles

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token que no supera la validación. | `{"detail":"Invalid token: invalid token"}` |
| `500 Internal Server Error` | Falla no capturada al consultar SQLite, por ejemplo, si la conexión no está disponible. | `Internal Server Error` (texto) |

### 6. Notas de seguridad

El filtro `owner` se concatena en SQL y no se compara con la identidad
autenticada. Actualmente un cliente autenticado puede consultar otro owner;
además, la consulta no está parametrizada (`api/app/main.py:67-75`).

## `POST /api/tasks`

### 1. Qué hace

Crea una tarea con los campos recibidos y la asigna al propietario indicado o,
si se omite, al usuario del token.

### 2. Autenticación

Requiere `Authorization: Bearer <token-vigente>`; véanse las convenciones
comunes.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| body | objeto JSON | Sí | El body debe ser un objeto JSON; no se exige ningún campo individual. |
| `title` | cualquier valor JSON | No | Se convierte a string; default `""`. |
| `description` | cualquier valor JSON | No | Se convierte a string; default `""`. |
| `status` | cualquier valor JSON | No | Se convierte a string; default `"TODO"`. |
| `priority` | cualquier valor JSON | No | Se convierte a string; default `"MEDIUM"`. |
| `owner` | cualquier valor JSON | No | Se convierte a string; default al `username` del token. Actualmente el cliente puede indicar otro owner. |

Ejemplo completo de request:

```http
POST /api/tasks HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token-vigente>
Content-Type: application/json

{
  "title": "Review contract",
  "description": "Document observed API",
  "status": "TODO",
  "priority": "MEDIUM"
}
```

### 4. Response exitosa

Estado: `201 Created`.

| campo | tipo | descripción |
|---|---|---|
| `id` | integer | Identificador SQLite asignado. |
| `title` | string | Título almacenado. |
| `description` | string | Descripción almacenada. |
| `status` | string | Estado almacenado. |
| `owner` | string | Propietario almacenado. |
| `priority` | string | Prioridad almacenada. |
| `createdAt` | string | Fecha y hora SQLite (`datetime('now')`). |

Ejemplo completo observado:

```json
{
  "id": 4,
  "title": "Review contract",
  "description": "Document observed API",
  "status": "TODO",
  "owner": "student",
  "priority": "MEDIUM",
  "createdAt": "2026-11-02 22:08:34"
}
```

### 5. Respuestas de error posibles

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token que no supera la validación. | `{"detail":"Invalid token: invalid token"}` |
| `422 Unprocessable Entity` | Body ausente o que no es un objeto JSON válido para el parámetro `dict`. | `{"detail":[{"type":"missing","loc":["body"],"msg":"Field required","input":null}]}` |
| `500 Internal Server Error` | Falla no capturada durante el insert, commit o lectura de SQLite. | `Internal Server Error` (texto) |

### 6. Notas de seguridad

`owner` puede sobrescribir el usuario del token y los campos se interpolan en
SQL sin parámetros. No hay un modelo Pydantic que limite campos, tipos o
longitudes (`api/app/main.py:77-88`).

## `GET /api/summary`

### 1. Qué hace

Resume el total de tareas, su distribución por estado y las tareas abiertas de
prioridad alta para un propietario.

### 2. Autenticación

Requiere `Authorization: Bearer <token-vigente>`; véanse las convenciones
comunes.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `owner` (query) | string | No | Propietario que se resume. Si falta o es una cadena vacía, se usa el `username` del token. El código actual también acepta un propietario distinto al del token. |

Ejemplo completo de request:

```http
GET /api/summary?owner=student HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token-vigente>
```

### 4. Response exitosa

Estado: `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| `owner` | string | Propietario consultado. |
| `total` | integer | Cantidad total de tareas consultadas. |
| `byStatus` | object de string a integer | Conteo de tareas agrupadas por estado. |
| `highPriorityOpen` | integer | Tareas `HIGH` cuyo estado no es `DONE` ni `ARCHIVED`. |

Ejemplo completo observado:

```json
{
  "owner": "student",
  "total": 3,
  "byStatus": {
    "TODO": 2,
    "IN_PROGRESS": 1
  },
  "highPriorityOpen": 1
}
```

### 5. Respuestas de error posibles

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token que no supera la validación. | `{"detail":"Invalid token: invalid token"}` |
| `500 Internal Server Error` | Falla no capturada al consultar SQLite o procesar las filas. | `Internal Server Error` (texto) |

### 6. Notas de seguridad

El owner del query se usa directamente, sin comprobar que coincida con la
identidad, y se concatena en SQL (`api/app/main.py:90-105`).

## `POST /api/requirements`

### 1. Qué hace

Recibe y guarda un archivo de requerimientos en el directorio de carga
configurado.

### 2. Autenticación

Requiere `Authorization: Bearer <token-vigente>`; véanse las convenciones
comunes.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `file` (multipart) | archivo | Sí | Archivo enviado como una parte multipart llamada `file`. El código no limita extensión ni tamaño. |

Ejemplo completo de request:

```http
POST /api/requirements HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token-vigente>
Content-Type: multipart/form-data; boundary=<boundary-generado-por-el-cliente>

<parte multipart "file" con filename="requirements.txt" y contenido "Login must be accessible">
```

Con `curl`, el cliente genera el boundary automáticamente:

```bash
curl -X POST "http://localhost:8000/api/requirements" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@requirements.txt"
```

### 4. Response exitosa

Estado: `201 Created`.

| campo | tipo | descripción |
|---|---|---|
| `owner` | string | `username` del token. |
| `fileName` | string o null | Nombre recibido en la parte multipart. |
| `storedAt` | string | Ruta formada con el directorio de carga configurado y el filename recibido. |
| `size` | integer | Número de bytes guardados. |

Ejemplo completo observado; `storedAt` depende de `WORKSHOP_UPLOAD_DIR` y del
directorio de trabajo:

```json
{
  "owner": "student",
  "fileName": "requirements.txt",
  "storedAt": "./uploads/requirements.txt",
  "size": 24
}
```

### 5. Respuestas de error posibles

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token que no supera la validación. | `{"detail":"Invalid token: invalid token"}` |
| `400 Bad Request` | El parser multipart no puede interpretar el body. | `{"detail":"There was an error parsing the body"}` |
| `422 Unprocessable Entity` | Falta la parte obligatoria `file`. | `{"detail":[{"type":"missing","loc":["body","file"],"msg":"Field required","input":null}]}` |
| `500 Internal Server Error` | Falla no capturada al leer/escribir el archivo o acceder a la ruta. | `Internal Server Error` (texto) |

### 6. Notas de seguridad

El filename se concatena al directorio sin normalización; no hay lista de
extensiones, límite de bytes ni protección contra sobrescritura. El contenido
completo se carga en memoria antes de escribirse (`api/app/main.py:107-112`).
La elección de extensiones, tamaño máximo y respuesta ante rechazo sigue
abierta.

## `GET /api/backend-health`

### 1. Qué hace

Consulta el endpoint de salud de autenticación configurado y devuelve el
resultado de esa dependencia externa.

### 2. Autenticación

Requiere `Authorization: Bearer <token-vigente>`; véanse las convenciones
comunes.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `AUTH_HEALTH_URL` (variable de entorno) | URL string | No | Destino de la consulta. Default observado: `http://localhost:8080/q/health`. No es un parámetro HTTP del cliente. |

Ejemplo completo de request:

```http
GET /api/backend-health HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token-vigente>
```

### 4. Response exitosa

Estado HTTP de la API: `200 OK`, incluso si el servicio consultado devuelve un
estado no exitoso. `status` y `body` contienen el estado HTTP y el cuerpo de
texto del servicio consultado.

| campo | tipo | descripción |
|---|---|---|
| `requestedBy` | string | `username` del token. |
| `status` | integer | Estado HTTP recibido del servicio consultado; `0` cuando la consulta produce una excepción. |
| `body` | string | Cuerpo UTF-8 del servicio, o texto de la excepción si falla la consulta. |

Ejemplo completo observado con un servicio de prueba que respondió `200` y
texto `UP`:

```json
{
  "requestedBy": "student",
  "status": 200,
  "body": "UP"
}
```

### 5. Respuestas de error posibles

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token que no supera la validación. | `{"detail":"Invalid token: invalid token"}` |

Un error de conexión con el servicio externo **no** cambia el estado HTTP de
esta API: la ruta responde `200 OK` con `status: 0` y el texto de la excepción
en `body`. Ese texto varía según la causa y el entorno; no se puede documentar
un valor único. Un estado no exitoso recibido del servicio externo también se
devuelve dentro de `status`, manteniendo el `200` de la API.

### 6. Notas de seguridad

La respuesta expone `str(exception)` al cliente cuando falla la conexión. El
destino se controla mediante configuración de entorno y la llamada usa un
timeout de 3 segundos (`api/app/main.py:114-121`).

## Evidencia y preguntas abiertas

Los ejemplos de respuesta de tareas, resumen, carga, errores de validación y
salud se comprobaron contra la aplicación actual en pruebas locales con SQLite
temporal y `TestClient`. Los timestamps, IDs y la ruta de almacenamiento son
valores ilustrativos; varían por ejecución y configuración.

1. ¿Qué emisor entrega el token a los clientes y cuál es el contrato de
   transición desde el token Base64 observado hacia un token firmado?
2. ¿Qué extensiones y tamaño máximo deben aceptarse para archivos y qué
   respuestas deben usarse cuando se rechacen?
3. ¿Se acuerda mantener como contrato el acceso por `owner` indicado por el
   cliente? El comportamiento observado permite consultar y crear tareas para
   otro owner; contradice la guía de autorización por identidad.
4. ¿Qué esquema de errores estable se quiere ofrecer para fallas internas? El
   comportamiento observado depende de FastAPI/Starlette y no está modelado
   explícitamente en las rutas.
