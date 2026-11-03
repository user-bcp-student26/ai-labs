import base64
import time
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


def token(username: str = "student", role: str = "STUDENT") -> str:
    raw = f"{username}|{role}|{int(time.time()) + 3600}|WORKSHOP_SECRET_2026"
    return base64.b64encode(raw.encode()).decode()


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
        )
    assert response.status_code == 201
    assert (upload_dir / "requirements.txt").exists()
    assert response.json() == {
        "owner": "student",
        "fileName": "requirements.txt",
        "storedAt": str(upload_dir / "requirements.txt"),
        "size": 24,
    }


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": "Bearer invalid"})
    assert response.status_code == 401


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
