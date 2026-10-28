# Proposal

## Why

El [Issue #24](https://github.com/user-bcp-student26/ai-labs/issues/24), "Posible issue Seguridad en conexion a base de datos", señala `GET /api/tasks` como evidencia e identifica un impacto de seguridad. El reporte no incluye una entrada reproducible ni resultados esperados u observados; el cambio trata el caso como un riesgo candidato y define el comportamiento seguro que debe verificarse sin afirmar una explotación confirmada.

## What Changes

- **Alcance:** asegurar que los valores de `owner` enviados a `GET /api/tasks` no puedan alterar el significado de la consulta ni ampliar los registros devueltos.
- **Comportamiento que se preserva:** mantener `GET /api/tasks`, la autenticación existente, el filtro por el usuario autenticado cuando se omite `owner`, y el formato de la respuesta exitosa. Un `owner` explícito solo puede estrechar el listado a la identidad autenticada; si no coincide, el listado queda vacío. Un token inválido sigue rechazándose.
- **Baseline documentado:** `docs/ENDPOINTS.md` no documenta `/api/tasks`; por ello, no se atribuye a ese documento un contrato inexistente. Los puntos anteriores describen el comportamiento actual comprobable en el código y las pruebas. El Issue #24 no solicita cambiar el contrato HTTP. El endpoint documentado `POST /auth/login` y las demás secciones de componentes quedan fuera del cambio y sin modificaciones.
- **Fuera de alcance:** permitir consultas de tareas de otros owners, modificar otros endpoints (incluidos `POST /api/tasks` y `GET /api/summary`), cambiar el mecanismo de autenticación, el esquema o despliegue de la base de datos, o rediseñar los contratos HTTP.
- Los requisitos y escenarios observables de seguridad y compatibilidad se especifican en `api-task-listing`.

## Capabilities

### New Capabilities
- `api-task-listing`: comportamiento seguro y compatible del listado de tareas filtrado por owner.

### Modified Capabilities
- Ninguna. El repositorio no tiene specs existentes.

## Impact

- Área de código y pruebas: `api/app/main.py` y `api/tests/test_api.py`.
- No se cambia `docs/ENDPOINTS.md`: registra contratos observados, pero no contiene una especificación de `/api/tasks`.
- No se proponen dependencias ni cambios de otros componentes.