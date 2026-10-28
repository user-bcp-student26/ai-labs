---
name: issue-error-investigation
description: 'Usa esta skill cuando haya que investigar un issue que reporta un error o comportamiento inesperado sin implementar una solución. Consulta el issue y el código mediante MCP, identifica el componente y el SHA examinado, separa hechos de hipótesis y reproduce localmente solo si el entorno permite hacerlo sobre esa versión, sin servicios externos, cambios de código ni dependencias nuevas.'
---

## Propósito
Investigar de forma reproducible un issue de error y dejar evidencia verificable del comportamiento reportado, sin modificar el producto ni convertir una hipótesis en un hallazgo confirmado.

## Entradas
- Identificador o enlace del issue y repositorio (`OWNER/REPO`).
- Acceso de lectura al issue y al código mediante GitHub MCP.
- Acceso local al repositorio y a sus instrucciones, pruebas y herramientas ya disponibles.
- Si existen, el comportamiento esperado, el resultado observado por quien reporta y los pasos o datos mínimos para reproducirlo.

Si falta el repositorio o no es posible identificar el issue sin ambigüedad, solicita ese dato antes de investigar. No inventes pasos, resultados ni criterios esperados ausentes.

## Salidas
Entrega un informe con:
- Enlace al issue y componente afectado, o la evidencia y limitación si no puede identificarse.
- SHA completo e inmutable del código examinado; indica explícitamente si la reproducción local usó exactamente ese SHA.
- Hechos respaldados por referencias comprobables y, por separado, hipótesis pendientes.
- Archivos y líneas relevantes, con rutas y números de línea vinculados al SHA.
- Pasos acotados de reproducción, comando exacto y precondiciones.
- Resultado esperado, resultado observado y conclusión: reproducido, no reproducido o no comprobado.
- Impedimentos concretos cuando no sea posible ejecutar o confirmar la reproducción.
- Ubicación de la salida local relevante, sanitizada para no conservar datos sensibles.

## Flujo
1. **Consultar el issue por MCP.** Usa herramientas de lectura del GitHub MCP configurado, como `issue_read`. Registra URL, título, estado, cuerpo, comentarios pertinentes y cualquier referencia a commit, versión, endpoint, archivo o síntoma. No cambies el issue ni publiques comentarios.
2. **Identificar el componente y el baseline.** Determina el componente a partir de evidencia del issue y la estructura documentada del repositorio. Respeta las instrucciones del repositorio y limita la inspección de código al componente afectado. Si el componente no se puede determinar, anota la incertidumbre y no inspecciones componentes ajenos.
3. **Examinar el código mediante MCP.** Usa búsqueda/lectura de código y commits del GitHub MCP configurado (por ejemplo, `search_code`, `get_file_contents` y `list_commits`) para leer los archivos pertinentes en un commit concreto. Registra el SHA completo y consulta el contenido referido a ese SHA, no solo el nombre mutable de una rama. Prefiere el SHA citado por el issue; si no existe, elige y justifica un commit de referencia sin presentarlo como la versión del incidente. Obtén referencias de línea del contenido de ese commit. Si MCP no permite fijar o verificar el commit, declara esa limitación.
4. **Separar evidencia e interpretación.** En una sección de **Hechos**, incluye solo datos observables en el issue, el código, la configuración o la salida de una comprobación, cada uno con referencia. En **Hipótesis**, enumera explicaciones aún no verificadas y la evidencia que falta. No describas una hipótesis como causa raíz confirmada ni infieras una solución.
5. **Diseñar una reproducción mínima antes de ejecutarla.** Define precondiciones, entradas no sensibles, pasos, comando exacto y una señal observable para cada resultado esperado. Inspecciona primero las instrucciones, pruebas, configuración y herramientas existentes necesarias para decidir si el comando es pertinente, local, acotado y seguro.
6. **Comprobar el baseline local sin alterar el proyecto.** Confirma el SHA local y que los archivos del componente que se usarán corresponden al SHA investigado; inspecciona el estado relevante del árbol de trabajo y los requisitos de ejecución. No hagas checkout, reset, fetch, instalación, migración, edición, generación de fixtures ni cambios de configuración para hacer coincidir versiones. Si no está disponible localmente el SHA exacto, no presentes una ejecución de otra versión como reproducción de ese baseline.
7. **Ejecutar solo comprobaciones revisadas.** Ejecuta únicamente comandos existentes que hayas inspeccionado y que puedas realizar con el entorno disponible. Confirma que no requieren servicios externos, acceso a producción, credenciales reales, descargas ni dependencias nuevas. Revisa antes sus posibles efectos sobre archivos o datos; no ejecutes una comprobación si esos efectos son desconocidos, destructivos, amplios o no se pueden aislar de forma segura. No amplíes la ejecución a suites o escenarios no necesarios.
8. **Registrar el resultado con precisión.** Conserva el comando, el SHA, las precondiciones y la salida relevante localmente, eliminando secretos, tokens, datos personales y otra información sensible. Registra por separado resultado esperado y observado, código de salida cuando aplique, y diferencias. Clasifica el desenlace:
   - **Reproducido:** la comprobación ejecutada en el SHA exacto muestra el síntoma descrito.
   - **No reproducido:** se ejecutó en el SHA exacto con precondiciones conocidas, pero el síntoma no apareció; no implica que la hipótesis sea falsa en todos los entornos.
   - **No comprobado:** no se pudo ejecutar o verificar el SHA/precondiciones; especifica exactamente el impedimento. La hipótesis permanece sin confirmar.
9. **Entregar, sin actuar sobre el issue.** Presenta el informe conforme a **Salidas** y referencia la ubicación de la evidencia local. No cierres ni edites el issue, no crees comentarios, ramas o PR, y no implementes, prepares ni apliques un arreglo.

## Constraints
- No escribas ni modifiques código, pruebas, configuración o datos del producto; no instales dependencias ni alteres el checkout para reproducir.
- No uses servicios externos durante la reproducción local. Las consultas MCP del issue y del código son únicamente de lectura.
- No ejecutes comandos que no hayas revisado, ni ocultes fallos, advertencias o impedimentos.
- No solicites, muestres ni guardes secretos reales, tokens, credenciales, datos personales o datos de producción. Sanitiza la salida antes de conservarla.
- No confundas el SHA consultado por MCP con el SHA ejecutado localmente; registra ambos si difieren y no declares reproducido el baseline si no coinciden.
- No inventes referencias de archivo/línea, pasos, resultados esperados o evidencia. Marca como desconocido lo que no se pudo verificar.
- No cierres el issue, no cambies su estado ni prepares una solución. El alcance es investigación y reproducción, no corrección.

## Adaptación
- Usa exclusivamente el servidor GitHub MCP y las herramientas de lectura que estén configuradas; no presupongas que una herramienta o campo está disponible. Si no puedes leer el issue o fijar el código a un SHA, registra el bloqueo en lugar de sustituir evidencia MCP por suposiciones.
- Sigue las instrucciones específicas del componente identificado (por ejemplo, auth, api o web) y no inspecciones otros componentes salvo que el issue o la evidencia demuestren que son necesarios; si el alcance debe ampliarse, detente y solicita autorización.
- Adapta el comando a la documentación y herramientas ya presentes en el repositorio. Usa solo pruebas o scripts cuyo alcance y efectos hayas revisado; omite comprobaciones que requieran servicios, red, secretos, dependencias ausentes o datos no aislados.
- Conserva la evidencia en el destino y formato exigidos por el repositorio, respetando las secciones existentes. Si no hay una convención, guarda un archivo local asociado al identificador del issue; incluye solo la salida mínima, sanitizada, y no sobrescribas evidencia previa.
- Usa las convenciones del repositorio para IDs de hallazgo, nombres de componentes y enlaces. Si no hay convención, etiqueta cada hecho e hipótesis para que se distingan claramente y usa rutas con números de línea.
