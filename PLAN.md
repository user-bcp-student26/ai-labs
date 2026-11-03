# Plan

## Alcance y selección — API

Esta selección incluye `H-API-01` a `H-API-05`, todos con estado `Pendiente`
en `HALLAZGOS.md`: los cuatro hallazgos de severidad alta y el análisis SCA de
severidad media. Las propuestas anteriores para `H-API-06` y `H-API-07` se
conservan al final como diferidas, fuera de esta selección. No hay hallazgos de
otros componentes en el alcance de este plan.

`docs/ENDPOINTS.md` no existe. Se toma como referencia el contrato observado
documentado en `HALLAZGOS.md`: conservar rutas, métodos, estados de éxito,
campos de respuesta y valores predeterminados. El límite de autorización y las
validaciones de seguridad se endurecerán de forma intencional; no se cambiarán
los endpoints. Antes de implementar, una persona debe revisar y aprobar este
plan. Los cambios de token y los límites de archivos requieren resolver las
decisiones indicadas abajo; no se presuponen acuerdos ni resultados.

## H-API-01 — Token Base64 con secreto embebido

- **Problema:** Base64 no verifica integridad criptográfica y el código confía
  en un secreto embebido; tampoco valida firma, emisor ni audiencia.
- **Cambio propuesto:** verificar un JWT firmado con algoritmo permitido y
  claims de emisor, audiencia y expiración. Obtener la configuración de
  variables de entorno, sin secretos reales en código ni pruebas. Usar una
  biblioteca JWT mantenida en lugar de implementar criptografía propia; si se
  incorpora una dependencia, justificarla y fijar una versión revisada.
- **Archivos a tocar:** `api/app/main.py`; nuevo módulo
  `api/app/security.py`; `api/requirements.txt` solo si se aprueba la
  dependencia; `api/tests/test_api.py`.
- **Prueba de caracterización previa:** añadir pruebas del acceso autorizado y
  del formato de respuesta existente para una ruta protegida. Antes de cambiar
  autenticación, acordar con quien emite el token el algoritmo, emisor,
  audiencia y transición desde el token Base64 observado. Añadir pruebas para
  token expirado, firma inválida y claims incorrectos como pruebas de
  aceptación de la corrección, no como contrato previo. No se inspeccionarán
  otros componentes sin ampliar el alcance.
- **Pendiente:** compatibilidad con el emisor y configuración de claims; no
  hacer un cambio incompatible hasta resolverlo.

## H-API-02 — El cliente puede consultar o asignar otro `owner`

- **Problema:** los parámetros de consulta y el cuerpo de creación permiten
  elegir un propietario distinto a la identidad autenticada.
- **Cambio propuesto:** derivar siempre el propietario de la identidad
  verificada. Mantener el parámetro `owner` por compatibilidad de forma, pero
  no permitir que amplíe el acceso; rechazar una solicitud que intente leer o
  crear datos para otra identidad con un error genérico. Aplicar la misma regla
  en tareas y resumen.
- **Archivos a tocar:** `api/app/main.py` y `api/tests/test_api.py`; el
  repositorio de tareas no se introduce en esta selección.
- **Prueba de caracterización previa:** cubrir la lista y el resumen para la
  identidad propia, sus campos y valores, y la creación sin `owner` con el
  propietario predeterminado. Añadir pruebas de aceptación separadas para
  verificar que un `owner` ajeno no permite leer ni asignar datos de otra
  identidad.
- **Compatibilidad:** los endpoints y las respuestas válidas para el
  propietario autenticado se conservan; el acceso entre propietarios deja de
  estar permitido intencionalmente.

## H-API-03 — Filtros e inserts SQL concatenados

- **Problema:** valores de filtros y campos de tarea se interpolan en
  sentencias SQL.
- **Cambio propuesto:** reemplazar la concatenación por consultas parametrizadas
  en lecturas, inserciones y selección de la fila recién creada. Mantener las
  columnas, orden, valores predeterminados y forma de respuesta observados.
- **Archivos a tocar:** `api/app/main.py`; repositorio nuevo
  `api/app/repositories/task_repository.py` solo si se aprueba después la
  propuesta diferida H-API-06; `api/tests/test_api.py`.
- **Prueba de caracterización previa:** cubrir listado, resumen y creación con
  valores ordinarios y comprobar orden, estado HTTP y campos de respuesta.
  Añadir una prueba de aceptación con texto que contenga comillas para
  verificar que se trata como dato y no altera la consulta.

## H-API-04 — Nombre y contenido del archivo sin controles

- **Problema:** el nombre recibido se usa para formar la ruta y el archivo
  completo se lee en memoria; no se comprueba extensión ni tamaño.
- **Cambio propuesto:** normalizar el nombre y evitar rutas fuera del directorio
  de carga; permitir solo extensiones acordadas; imponer un máximo de tamaño
  comprobado durante la lectura y guardar por bloques. Usar constantes o
  configuración con nombre para los límites y una estrategia que evite
  sobrescribir archivos existentes. Conservar el estado 201 y los campos de
  respuesta.
- **Archivos a tocar:** `api/app/main.py`; módulo nuevo
  `api/app/services/requirement_upload_service.py` solo si se aprueba después
  la propuesta diferida H-API-06; `api/tests/test_api.py`.
- **Prueba de caracterización previa:** cubrir una carga válida y verificar
  estado, campos de respuesta y archivo persistido. Añadir pruebas de
  aceptación para nombre con componentes de ruta, extensión no permitida,
  archivo sobre el límite y ausencia de escritura ante rechazo.
- **Pendiente:** acordar lista de extensiones, tamaño máximo y respuesta HTTP
  para cada rechazo; no inventar esos valores en la implementación.

## H-API-05 — Versiones de dependencias requieren análisis SCA

- **Problema:** `urllib3` y `python-multipart` están fijadas a las versiones
  señaladas en `HALLAZGOS.md`; todavía no hay evidencia de advisories aplicables
  ni de una versión correctiva.
- **Cambio propuesto:** revisar advisories y el árbol de dependencias efectivo
  con evidencia fechada. Actualizar solo los paquetes afectados a versiones
  corregidas y compatibles, sin silenciar hallazgos. No cambiar versiones
  basándose únicamente en el número indicado por el reto.
- **Archivos a tocar:** `api/requirements.txt`; `api/requirements-dev.txt`
  únicamente si una herramienta de análisis resulta necesaria y su inclusión
  queda justificada; `api/tests/test_api.py` solo si las actualizaciones
  requieren ajustar pruebas.
- **Prueba de caracterización previa:** comprobar el contrato de carga
  multipart y la respuesta de `/api/backend-health` con el comportamiento
  exitoso actual. Tras cualquier actualización, repetir esas pruebas y el
  análisis SCA, registrando resultado y versiones efectivamente resueltas.

## Propuestas previas diferidas — fuera de la selección actual

Se conservan estas propuestas de la versión anterior del plan para no perder
el historial. No forman parte del alcance de esta selección ni autorizan
cambios hasta que se seleccionen expresamente.

### H-API-06 — Rutas, persistencia, reglas y serialización en un solo módulo

- **Problema:** inicialización de SQLite, consultas, lógica de rutas y mapeo de
  respuestas están concentrados en `main.py`.
- **Cambio propuesto:** separar rutas, servicio, repositorio, esquemas y acceso
  a base de datos en responsabilidades pequeñas. Las rutas delegan reglas y
  persistencia; el repositorio conserva consultas parametrizadas. Extraer
  serialización compartida en un solo lugar y evitar duplicación. No introducir
  capas que no tengan responsabilidad concreta.
- **Archivos a tocar:** `api/app/main.py`; módulos nuevos bajo `api/app/`,
  por ejemplo `routers/`, `services/`, `repositories/`, `schemas.py` y
  `database.py`; `api/tests/test_api.py`.
- **Prueba de caracterización previa:** cubrir antes de mover código los
  endpoints existentes, estados HTTP, forma/campos de respuesta, valores por
  defecto, datos sembrados y comportamiento de persistencia que se pretenda
  conservar. Ejecutar regresión después de cada separación.

### H-API-07 — Tipado crudo, excepciones amplias y lógica anidada

- **Problema:** requests y responses se manejan como diccionarios sin modelos
  Pydantic; hay capturas amplias, texto de excepción devuelto al cliente y
  condiciones anidadas en el resumen.
- **Cambio propuesto:** definir modelos Pydantic de entrada y salida que
  conserven campos, defaults y serialización observados; dividir cálculos en
  funciones de una sola responsabilidad y usar retornos anticipados donde
  aclaren el flujo. Capturar excepciones específicas, devolver mensajes
  genéricos y conservar los detalles solo en logs controlados. Usar constantes
  nombradas para valores de configuración y límites.
- **Archivos a tocar:** `api/app/main.py`; `api/app/schemas.py` y servicios
  nuevos solo si no existen tras H-API-06; `api/tests/test_api.py`.
- **Prueba de caracterización previa:** comprobar requests válidos, defaults,
  campos y serialización de tareas y resumen; comprobar la forma de la
  respuesta de error de autenticación y de fallo de backend-health. Añadir
  pruebas de aceptación para entradas inválidas y para que las respuestas no
  expongan texto interno de excepciones.

## Secuencia y validación

1. Registrar las pruebas de caracterización de `H-API-01` a `H-API-05` antes
   de modificar el código o las dependencias y ejecutar la suite desde `api/`.
2. Resolver la compatibilidad del token y los límites de carga antes de
   implementar `H-API-01` y `H-API-04`.
3. Implementar los cambios seleccionados sin alterar rutas ni formatos de
   respuesta válidos; documentar y aprobar previamente los rechazos de
   solicitudes que incumplan las nuevas reglas de seguridad.
4. Investigar dependencias con evidencia antes de actualizar versiones.
5. Registrar resultados reales y actualizar estados en `HALLAZGOS.md`; no
   registrar aprobaciones ni pruebas que no hayan ocurrido.

## Ejecuciones registradas

- **API — H-API-01 a H-API-05, línea base previa a cambios:** desde `api/`,
  comando `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing`.
  Resultado: 4 pruebas aprobadas, cobertura total `app` 91% (96 statements,
  7 missed); 11 warnings de deprecación. No se modificó código antes de esta
  ejecución.
