# Contratos de autenticacion

## Estado del contrato

Este documento registra el contrato **observado** en el codigo actual y en una ejecucion local de auth; no registra una aprobacion de compatibilidad por consumidores. No hay un contrato **acordado** confirmado. Siguen pendientes la compatibilidad del formato del token, el allowlist CORS y la confirmacion de los clientes, conforme a H-AUTH-03, H-AUTH-04 y H-AUTH-08 en `HALLAZGOS.md`.

## `GET /q/health`

### 1. Que hace

Permite a un operador o monitor comprobar si la instancia de autenticacion informa que esta operativa. La ruta la proporciona `quarkus-smallrye-health` (`auth/pom.xml:38-41`); no hay checks de salud propios en el codigo de auth. En la ejecucion local observada respondio `UP` con la lista de checks vacia.

### 2. Autenticacion

No requiere autenticacion. No se necesita header `Authorization`; una solicitud local sin credenciales devolvio 200. El servidor publica este endpoint en la configuracion actual; la proteccion de red externa depende del despliegue.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Cuerpo | ninguno | No | Es una solicitud GET; no consume un cuerpo JSON. |

Ejemplo de solicitud completa (no lleva ejemplo JSON porque no existe cuerpo de request):

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json
```

### 4. Response exitosa

**Estado:** `200 OK` cuando el health report observado esta `UP`.

| campo | tipo | descripcion |
|---|---|---|
| `status` | string | Estado agregado que informa el runtime; fue `UP` en la ejecucion observada. |
| `checks` | array | Checks registrados por el runtime. Fue un array vacio; el codigo de auth no define objetos de check. |

Ejemplo observado:

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

No se observaron respuestas de error para este endpoint: en la prueba local, tanto `GET /q/health` como `POST /q/health` respondieron `200` con el mismo reporte `UP`. No se documenta una respuesta `503` ni su cuerpo: no se encontro ni se ejecuto un health check que pudiera producir estado `DOWN`. La politica esperada para una instancia no saludable queda como pregunta abierta.

### 6. Notas de seguridad

La respuesta observada solo informa `UP` y una lista vacia; no contiene credenciales. La ruta no verifica un header de autenticacion. Aunque se documenta y recomienda `GET`, el runtime tambien acepto `POST` y devolvio `200`; este comportamiento de Quarkus se conserva como observacion, no como recomendacion de uso. La configuracion de host y el control de acceso perimetral deben revisarse en cada despliegue; la configuracion de auth y la evidencia del endpoint estan en `auth/src/main/resources/application.properties:1-8`.

## `POST /auth/login`

### 1. Que hace

Autentica las credenciales de un usuario y, si son validas, devuelve su perfil y un token para que el cliente pueda iniciar su flujo de sesion. La ruta y validaciones HTTP estan en `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:12-53`; la autenticacion y el mapeo de salida estan en `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:18-33`.

### 2. Autenticacion

No requiere autenticacion previa; no se necesita header `Authorization`. Envia `Content-Type: application/json`. La respuesta se serializa como JSON. La solicitud de login es el mecanismo de autenticacion.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| `username` | string | Si | Nombre de usuario. No puede ser nulo, vacio ni contener solo espacios. |
| `password` | string | Si | Contraseña. No puede ser nula, vacia ni contener solo espacios. |
| `client` | string | No | Campo aceptado por el DTO; no se usa en la autenticacion ni se devuelve. |

Los campos se declaran en `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-7`; las validaciones de presencia y contenido estan en `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:28-40`. El ejemplo usa el usuario ficticio de prueba y el campo opcional `client`, tal como en `AuthResourceTest`.

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4. Response exitosa

**Estado:** `200 OK` para credenciales validas.

| campo | tipo | descripcion |
|---|---|---|
| `token` | string | Token codificado emitido por el servicio. Su contenido cambia con el tiempo de expiracion. |
| `expiresIn` | integer | Duracion declarada del token, en segundos; el valor actual es `3600`. |
| `user` | object | Perfil del usuario autenticado. |
| `user.username` | string | Nombre de usuario. |
| `user.displayName` | string | Nombre para mostrar; el servicio lo escapa como HTML. |
| `user.role` | string | Rol del usuario, por ejemplo `STUDENT`. |
| `debug` | string | Indicador fijo del flujo actual: `authenticated-with-legacy-flow`. |

La forma del DTO esta en `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4`; los valores y el token se construyen en `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:18-33`. El token del ejemplo es un marcador, no un token reutilizable; el resto de los campos refleja la respuesta observada con el usuario ficticio.

```json
{
  "token": "<token emitido; cambia con cada login>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5. Respuestas de error

| estado | cuando ocurre | ejemplo de cuerpo |
|---|---|---|
| `400 Bad Request` | El cuerpo JSON es `null`. | `{"message":"Request is null"}` |
| `400 Bad Request` | Falta `username`, es `null`, vacio o solo espacios. | `{"message":"Username is required"}` |
| `400 Bad Request` | Falta `password`, es `null`, vacia o solo espacios. | `{"message":"Password is required"}` |
| `400 Bad Request` | El JSON esta malformado. En la ejecucion local, el runtime devolvio cuerpo vacio. | Cuerpo vacio (0 bytes). |
| `401 Unauthorized` | El usuario no existe o la contraseña no coincide. El mensaje incluye el `username` recibido. | `{"message":"Invalid user student or password"}` |
| `405 Method Not Allowed` | Se usa un metodo distinto de POST en `/auth/login`. En la ejecucion local, el runtime devolvio cuerpo vacio. | Cuerpo vacio (0 bytes). |
| `406 Not Acceptable` | El cliente envia un `Accept` incompatible, por ejemplo `text/plain`. En la ejecucion local, el runtime devolvio cuerpo vacio. | Cuerpo vacio (0 bytes). |
| `415 Unsupported Media Type` | El cuerpo se envia con un `Content-Type` no admitido, por ejemplo `text/plain`. En la ejecucion local, el runtime devolvio cuerpo vacio. | Cuerpo vacio (0 bytes). |
| `500 Internal Server Error` | Una excepcion no controlada llega al recurso durante la autenticacion. La prueba unitaria verifica el status y cuerpo del recurso. | `{"message":"An unexpected error occurred"}` |

Los status y cuerpos propios estan definidos en `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:28-53`; el cuerpo 500 tambien se verifica en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:111-126`. Los cuerpos vacios para errores de parseo y protocolo corresponden a las respuestas observadas en la ejecucion local; no son mensajes definidos por el recurso.

### 6. Notas de seguridad

- El password viaja en el cuerpo del request. Usa HTTPS en redes no locales; el servicio no fuerza TLS desde este recurso.
- Las contraseñas se verifican contra hashes PBKDF2-HMAC-SHA256 con salt (`auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:10-44`).
- El token actual **no esta firmado**: es Base64 de datos concatenados y un secreto hardcodeado. Aunque incluye una expiracion y `expiresIn` informa 3600 segundos, no hay verificacion del token ni aplicacion de su expiracion en este servicio. No debe tratarse como prueba criptografica de identidad. Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8-15`. H-AUTH-03 y H-AUTH-04 siguen pendientes.
- CORS esta habilitado con origen `/.*/`, que acepta cualquier origen en la configuracion actual (`auth/src/main/resources/application.properties:4-8`). No implica que el endpoint requiera ni valide un token.
- La respuesta 500 es generica para el cliente; el detalle de la excepcion se escribe en el log del servidor (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:49-53`).
- La linea base esta **observada**, no acordada con consumidores. La compatibilidad del token, los origenes CORS permitidos y la aprobacion del contrato siguen siendo preguntas abiertas (H-AUTH-03, H-AUTH-04 y H-AUTH-08).

## Documentos consultados

- `README.md`
- `RETO.md`
- `docs/GOOD_PRACTICES.md`
- `HALLAZGOS.md`
- `PLAN.md`
- `auth/pom.xml`
- `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
- `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java`
- `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java`
- `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
- `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java`
- `auth/src/main/resources/application.properties`
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`
