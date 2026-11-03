# Pruebas — API FastAPI

## Alcance y contrato probado

Estas pruebas corresponden a `api/` y a los endpoints descritos como contrato
observado en [ENDPOINTS.md](./ENDPOINTS.md). La implementación actual aún no
está refactorizada. El comportamiento inseguro no se convierte en una
expectativa aprobada: las pruebas de aislamiento por identidad se mantienen
fallidas y se registran aparte como bug de `H-API-02`.

## Matriz de casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /api/tasks` | Listar tareas del token con sus campos documentados | Camino feliz | `200`; lista de tareas con `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`. |
| `GET /api/tasks` | `owner` vacío | Caso borde | `200`; se usa el `username` del token. |
| `GET /api/tasks` | Consultar tareas de otro `owner` | Seguridad — H-API-02 | No devolver registros ajenos; no se presupone un código de error mientras siga abierta esa decisión. |
| `GET /api/tasks` | Sin token | Error — H-API-01 | `401` con `detail` de token inválido. |
| `GET /api/tasks` | SQLite no disponible | Error | `500 Internal Server Error`. |
| `POST /api/tasks` | Crear una tarea con campos válidos | Camino feliz | `201`; respuesta con campos de tarea y `owner` predeterminado al usuario autenticado. |
| `POST /api/tasks` | Body `{}` | Caso borde | `201`; defaults observados: título/descripción vacíos, `TODO`, `MEDIUM` y owner del token. |
| `POST /api/tasks` | Asignar la tarea a otro `owner` | Seguridad — H-API-02 | No crearla para otro owner; no se presupone un código de error mientras siga abierta esa decisión. |
| `POST /api/tasks` | Sin token | Error — H-API-01 | `401` con `detail` de token inválido. |
| `POST /api/tasks` | Body ausente | Error | `422`; error de validación FastAPI en `body`. |
| `POST /api/tasks` | SQLite no disponible | Error | `500 Internal Server Error`. |
| `GET /api/summary` | Resumen del usuario autenticado | Camino feliz | `200`; `owner`, `total`, `byStatus` y `highPriorityOpen` con valores esperados. |
| `GET /api/summary` | `owner` vacío | Caso borde | `200`; se usa el `username` del token. |
| `GET /api/summary` | Sin token | Error — H-API-01 | `401` con `detail` de token inválido. |
| `GET /api/summary` | SQLite no disponible | Error | `500 Internal Server Error`. |
| `POST /api/requirements` | Subir archivo con filename y contenido | Camino feliz | `201`; `owner`, `fileName`, `storedAt` y `size`. |
| `POST /api/requirements` | Archivo sin extensión y vacío | Caso borde — H-API-04 | `201`; conserva el filename y `size` vale `0`, comportamiento observado. |
| `POST /api/requirements` | Sin token | Error — H-API-01 | `401` con `detail` de token inválido. |
| `POST /api/requirements` | Multipart malformado | Error | `400` con detalle de error de parsing. |
| `POST /api/requirements` | Falta la parte `file` | Error | `422`; validación indica `body.file` obligatorio. |
| `POST /api/requirements` | Escritura no disponible | Error — H-API-04 | `500 Internal Server Error`. |
| `POST /api/requirements` | Límite máximo de tamaño | Pendiente — H-API-04 | No se define resultado: el contrato y código actuales no establecen límite. No se inventa uno en la prueba. |
| `GET /api/backend-health` | Upstream responde `200` y `UP` | Camino feliz | API responde `200` con `status: 200` y `body: "UP"`. |
| `GET /api/backend-health` | Upstream responde `503` | Caso borde | API responde `200`; refleja `status: 503` y el body upstream. |
| `GET /api/backend-health` | Sin token | Error — H-API-01 | `401` con `detail` de token inválido. |
| `GET /api/backend-health` | Excepción de conexión upstream | Error representado en body | API responde `200` con `status: 0` y el texto de la excepción en `body`; no es un error HTTP de esta ruta. |

## Ejecución y cobertura

- **Componente e IDs:** API; `H-API-01` (401 de autenticación), `H-API-02`
  (aislamiento de owner, con dos fallos), `H-API-03` (solo comportamiento SQL
  funcional de camino feliz), y `H-API-04` (carga y errores). No se hizo
  análisis SCA de `H-API-05`; no es una prueba de endpoint ni se cambiaron
  dependencias en este paso.
- **Directorio de ejecución:** `api/`.
- **Comando:** `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing`
- **Código de salida:** `1`.
- **Resultado real:** `25 passed`, `2 failed`, `57 warnings`. Ambos fallos son
  las pruebas de aislamiento por owner descritas en la sección **Fallos**.
- **Cobertura global de `app`:** `98%`, según Coverage.py con branch coverage:
  `96/96` statements cubiertos y `14/16` branches cubiertos; total
  `110/112` puntos de cobertura, redondeado a `98%`. Quedan 2 branches
  parciales.
- **Cobertura del código de producción modificado:** no aplica/no se midió por
  separado; este cambio añadió pruebas, no modificó `api/app/`. El total de
  `app` es cobertura global, no cobertura de diff. El umbral global observado
  supera 85%, pero la suite no está verde.
- **Advertencias:** 55 avisos de deprecación relacionados principalmente con
  `FastAPI.on_event("shutdown")` y su uso en `api/app/main.py:123`.

## Fallos — bug reportado aparte

1. **H-API-02 — lectura cross-owner:** `test_tasks_do_not_expose_another_owners_records`
   falla porque `GET /api/tasks?owner=trainer` devuelve `200` y contiene una
   tarea cuyo owner es `trainer`. Evidencia del código:
   `api/app/main.py:68-70`.
2. **H-API-02 — asignación cross-owner:** `test_created_task_cannot_be_assigned_to_another_owner`
   falla porque `POST /api/tasks` devuelve `201` con `owner: "trainer"` para
   una identidad `student`. Evidencia del código:
   `api/app/main.py:78-84`.

Las aserciones se dejaron activas; no se usó `xfail`, no se alteró el resultado
para hacer pasar la suite y no se modificó el código de la API. `H-API-02`
permanece pendiente.

## Pendientes y pruebas propuestas

- Acordar la política y el tamaño máximo de archivos antes de agregar una
  prueba de rechazo por tamaño; actualmente solo se verifica el caso borde de
  archivo vacío y sin extensión.
- Acordar el status code de rechazo para un owner ajeno. Las pruebas actuales
  verifican la propiedad de no revelar/crear datos para otro owner sin imponer
  un código no acordado.
- La autenticación existente usa el token Base64 legacy. No hay un contrato
  JWT observado que permita inventar pruebas de emisor, audiencia o firma.
- No se actualizaron `HALLAZGOS.md`: `H-API-02` ya está registrado como
  `Pendiente`; esta ejecución aporta evidencia, no una resolución.

## Código de las pruebas añadidas

El código ejecutable completo, incluidos los tests de caracterización
preexistentes, está en
[api/tests/test_api.py](../api/tests/test_api.py). El siguiente bloque recoge
las pruebas añadidas para los casos borde, errores y seguridad; reutilizan el
helper `headers()` ya definido en ese módulo.

```python
def create_test_app(tmp_path: Path, upload_dir: Path | None = None):
    selected_upload_dir = upload_dir or tmp_path / "uploads"
    return create_app(str(tmp_path / "workshop.db"), str(selected_upload_dir))


def test_lists_tasks_with_contract_fields(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())

    assert response.status_code == 200
    tasks = response.json()
    assert len(tasks) == 2
    assert {task["owner"] for task in tasks} == {"student"}
    assert set(tasks[0]) == {
        "id",
        "title",
        "description",
        "status",
        "owner",
        "priority",
        "createdAt",
    }


def test_empty_tasks_owner_query_defaults_to_token_identity(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.get("/api/tasks?owner=", headers=headers())

    assert response.status_code == 200
    assert {task["owner"] for task in response.json()} == {"student"}


def test_tasks_do_not_expose_another_owners_records(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.get("/api/tasks?owner=trainer", headers=headers())

    assert response.status_code != 200 or all(
        task["owner"] == "student" for task in response.json()
    )


def test_created_task_cannot_be_assigned_to_another_owner(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Private task", "owner": "trainer"},
        )

    assert response.status_code != 201 or response.json()["owner"] == "student"


def test_empty_task_payload_uses_documented_defaults(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers(), json={})

    assert response.status_code == 201
    assert response.json()["title"] == ""
    assert response.json()["description"] == ""
    assert response.json()["status"] == "TODO"
    assert response.json()["priority"] == "MEDIUM"
    assert response.json()["owner"] == "student"


def test_missing_task_body_returns_documented_422(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers())

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body"]
    assert response.json()["detail"][0]["type"] == "missing"


def test_summary_returns_contract_fields_for_identity(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.get("/api/summary", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "owner": "student",
        "total": 2,
        "byStatus": {"TODO": 1, "IN_PROGRESS": 1},
        "highPriorityOpen": 1,
    }


def test_empty_summary_owner_query_defaults_to_token_identity(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.get("/api/summary?owner=", headers=headers())

    assert response.status_code == 200
    assert response.json()["owner"] == "student"


def test_upload_without_extension_and_empty_content(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_test_app(tmp_path, upload_dir)
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements", b"", "application/octet-stream")},
        )

    assert response.status_code == 201
    assert response.json()["fileName"] == "requirements"
    assert response.json()["size"] == 0
    assert (upload_dir / "requirements").read_bytes() == b""


def test_missing_upload_file_returns_documented_422(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = client.post("/api/requirements", headers=headers())

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body", "file"]
    assert response.json()["detail"][0]["type"] == "missing"


def test_malformed_multipart_returns_documented_400(tmp_path: Path) -> None:
    app = create_test_app(tmp_path)
    request_headers = {
        **headers(),
        "Content-Type": "multipart/form-data; boundary=bad",
    }
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=request_headers,
            content=b"invalid",
        )

    assert response.status_code == 400
    assert response.json() == {"detail": "There was an error parsing the body"}


@pytest.mark.parametrize(
    ("method", "path", "request_kwargs"),
    [
        ("get", "/api/tasks", {}),
        ("post", "/api/tasks", {"json": {"title": "Task"}}),
        ("get", "/api/summary", {}),
        (
            "post",
            "/api/requirements",
            {"files": {"file": ("requirements.txt", b"content", "text/plain")}},
        ),
        ("get", "/api/backend-health", {}),
    ],
)
def test_every_endpoint_rejects_missing_authentication(
    tmp_path: Path, method: str, path: str, request_kwargs: dict
) -> None:
    app = create_test_app(tmp_path)
    with TestClient(app) as client:
        response = getattr(client, method)(path, **request_kwargs)

    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token: invalid token"}


@pytest.mark.parametrize(
    ("method", "path", "request_kwargs"),
    [
        ("get", "/api/tasks", {}),
        ("post", "/api/tasks", {"json": {"title": "Task"}}),
        ("get", "/api/summary", {}),
    ],
)
def test_database_failures_return_documented_500(
    tmp_path: Path, method: str, path: str, request_kwargs: dict
) -> None:
    app = create_test_app(tmp_path)
    app.state.connection.close()
    with TestClient(app, raise_server_exceptions=False) as client:
        response = getattr(client, method)(
            path,
            headers=headers(),
            **request_kwargs,
        )

    assert response.status_code == 500
    assert response.text == "Internal Server Error"


def test_upload_write_failure_returns_documented_500(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_test_app(tmp_path, upload_dir)
    upload_dir.rmdir()
    upload_dir.write_text("not a directory")

    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"content", "text/plain")},
        )

    assert response.status_code == 500
    assert response.text == "Internal Server Error"


def test_backend_health_returns_successful_upstream_response(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    app = create_test_app(tmp_path)
    upstream_response = SimpleNamespace(status=200, data=b"UP")
    pool_manager = SimpleNamespace(
        request=lambda *args, **kwargs: upstream_response
    )
    monkeypatch.setattr("app.main.urllib3.PoolManager", lambda: pool_manager)

    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 200,
        "body": "UP",
    }


def test_backend_health_returns_upstream_status_and_body(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    app = create_test_app(tmp_path)
    upstream_response = SimpleNamespace(status=503, data=b"maintenance")
    pool_manager = SimpleNamespace(
        request=lambda *args, **kwargs: upstream_response
    )
    monkeypatch.setattr("app.main.urllib3.PoolManager", lambda: pool_manager)

    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 503,
        "body": "maintenance",
    }


def test_backend_health_represents_upstream_exception_as_status_zero(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    app = create_test_app(tmp_path)

    def fail_request(*args, **kwargs):
        raise OSError("connection refused")

    pool_manager = SimpleNamespace(request=fail_request)
    monkeypatch.setattr("app.main.urllib3.PoolManager", lambda: pool_manager)

    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())

    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 0,
        "body": "connection refused",
    }
```
