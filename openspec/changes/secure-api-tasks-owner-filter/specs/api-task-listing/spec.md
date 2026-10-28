# Spec Delta

## Purpose

Define el comportamiento seguro y compatible del listado de tareas filtrado por owner, evitando que los valores recibidos del cliente amplíen los registros que la respuesta puede incluir.

## ADDED Requirements

### Requirement: El filtro por defecto corresponde al usuario autenticado
Cuando la solicitud omite `owner`, el sistema MUST limitar el listado a las tareas cuyo owner coincide con el usuario autenticado.

#### Scenario: Listado sin owner explícito
- **WHEN** un usuario autenticado solicita `GET /api/tasks` sin parámetro `owner`
- **THEN** la respuesta es `200 OK` y contiene solo tareas de ese usuario

### Requirement: El filtro explícito queda ligado a la identidad
Cuando la solicitud incluye `owner`, el sistema MUST limitar el resultado a tareas del usuario autenticado; el valor explícito puede estrechar ese conjunto, pero MUST NOT autorizar acceso a tareas de otro usuario.

#### Scenario: Owner explícito coincide con la identidad
- **WHEN** un usuario autenticado solicita `GET /api/tasks` con un `owner` igual a su identidad
- **THEN** la respuesta `200 OK` contiene solo tareas de ese usuario

#### Scenario: Owner explícito no coincide con la identidad
- **WHEN** un usuario autenticado solicita `GET /api/tasks` con un `owner` diferente de su identidad
- **THEN** la respuesta es `200 OK` y contiene una lista vacía

### Requirement: El valor de owner no amplía los resultados
El contenido del parámetro `owner` MUST NOT alterar la selección de tareas ni permitir que la respuesta incluya registros que no coincidan con el owner solicitado.

#### Scenario: Valor de owner con sintaxis especial
- **WHEN** un usuario autenticado solicita el listado con un valor de `owner` que contiene sintaxis que podría cambiar una consulta y no coincide exactamente con su identidad
- **THEN** la respuesta es `200 OK`, no contiene tareas de otros owners y devuelve una lista vacía

### Requirement: El listado requiere autenticación válida
El sistema MUST rechazar las solicitudes de listado que no presenten una identidad válida.

#### Scenario: Token inválido
- **WHEN** una solicitud a `GET /api/tasks` presenta un token inválido
- **THEN** la respuesta es `401 Unauthorized`

### Requirement: La respuesta exitosa conserva su forma
Para una solicitud autenticada exitosa, el sistema MUST conservar la respuesta como una lista JSON de tareas con los campos existentes.

#### Scenario: Forma de cada tarea listada
- **WHEN** una solicitud autenticada obtiene una o más tareas
- **THEN** la respuesta es `200 OK` y cada elemento contiene `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`