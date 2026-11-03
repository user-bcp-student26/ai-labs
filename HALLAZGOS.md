# Hallazgos

## Contrato observado — API

`docs/ENDPOINTS.md` no existe; este resumen se deriva de `api/app/main.py` y
describe el comportamiento observado, no un contrato acordado.

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
| H-API-01 | api | Token Base64 con secreto embebido | Vulnerabilidad | Base64 no firma el token; el secreto está en el código y la validación no comprueba firma, emisor ni audiencia. | api/app/main.py:14,19-26 | alta | Pendiente |
| H-API-02 | api | El cliente puede consultar o asignar otro `owner` | Vulnerabilidad | Las consultas aceptan `owner` del cliente y la creación permite sobrescribir el propietario del token, rompiendo el aislamiento por identidad. | api/app/main.py:68-70,78-84,91-93 | alta | Pendiente |
| H-API-03 | api | Filtros e inserts SQL concatenados | Vulnerabilidad | Los valores recibidos se incorporan directamente a consultas SQL; entradas con formato inesperado pueden alterar la consulta o impedir operaciones. | api/app/main.py:70,84,93 | alta | Pendiente |
| H-API-04 | api | Se conserva directamente el nombre y contenido del archivo | Vulnerabilidad | El nombre se usa para formar la ruta sin normalización; además se lee y guarda el contenido completo sin límite de tamaño ni validación de extensión. | api/app/main.py:107-111 | alta | Pendiente |
| H-API-05 | api | Versiones de dependencias requieren análisis SCA | Sugerencia | `urllib3` y `python-multipart` están fijadas a las versiones indicadas por el reto. Su estado frente a advisories no se verificó en este Discovery; no se afirma aquí que exista un CVE aplicable. | api/requirements.txt:4-5 | media | Pendiente |
| H-API-06 | api | Rutas, persistencia, reglas y serialización en un solo módulo | Refactor | La inicialización de SQLite y las rutas, consultas y mapeo de respuestas conviven en `main.py`, dificultando probar y cambiar cada responsabilidad por separado. | api/app/main.py:29-56,67-124 | media | Pendiente |
| H-API-07 | api | Tipado crudo, excepciones amplias y lógica anidada | Refactor | Los cuerpos y respuestas usan diccionarios sin modelos de dominio; hay capturas amplias y lógica condicional anidada, lo que reduce validación y claridad. Parte del texto de excepción se devuelve al cliente. | api/app/main.py:17-26,68,78,94-105,114-121 | media | Pendiente |
