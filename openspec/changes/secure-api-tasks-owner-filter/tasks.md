# Tasks

## 1. Caracterizar el contrato que se preserva

- [ ] 1.1 Fortalecer la prueba del listado por defecto para comprobar que solo devuelve tareas del usuario autenticado y conserva campos y orden; verificar desde `api/` con `python -m pytest tests/test_api.py -k lists_seeded_tasks`.
- [ ] 1.2 Agregar una prueba para `GET /api/tasks?owner=<identidad autenticada>` que compruebe el filtro exacto y la respuesta `200`; verificar desde `api/` con `python -m pytest tests/test_api.py -k lists_tasks_for_authenticated_owner`.

## 2. Caracterizar las regresiones de seguridad

- [ ] 2.1 Agregar una prueba con tareas de dos owners que compruebe que solicitar un owner distinto de la identidad autenticada devuelve `200` y una lista vacía; verificar desde `api/` que la prueba falla contra la implementación actual porque expone las tareas del otro owner.
- [ ] 2.2 Agregar una prueba con un valor de owner que contiene sintaxis especial y no coincide con la identidad, comprobando que no devuelve tareas de ningún otro owner; verificar desde `api/` que la prueba falla contra la implementación actual sin cambiar aún el código funcional.

## 3. Corregir el filtro de `/api/tasks`

- [ ] 3.1 Parametrizar el valor usado por el filtro SQL de `owner` sin cambiar la proyección ni el orden; verificar desde `api/` que pasan las pruebas de listado y la regresión de sintaxis especial con `python -m pytest tests/test_api.py -k "lists_seeded_tasks or lists_tasks_for_authenticated_owner or owner_special_characters"`.
- [ ] 3.2 Limitar el resultado a la identidad autenticada y tratar un `owner` explícito distinto como filtro sin coincidencias; verificar desde `api/` con `python -m pytest tests/test_api.py -k owner_mismatch` que devuelve `200` y una lista vacía.

## 4. Validar el componente

- [ ] 4.1 Ejecutar toda la suite de `api/` con `python -m pytest --cov=app.main --cov-report=term-missing`; confirmar que todas las pruebas pasan y revisar que la cobertura del código modificado alcanza la meta del componente sin incluir otros componentes.