import os

from fastapi.testclient import TestClient

from app import main
from app.main import app


client = TestClient(app)


def test_health_check() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_health_reports_not_initialized_before_startup(
    monkeypatch,
) -> None:
    monkeypatch.setattr(
        main.p2pnet_assets,
        "get_last_preparation",
        lambda: None,
    )

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "p2pnetShadow": {
            "ready": False,
            "status": "NOT_INITIALIZED",
            "modelVersion": None,
        },
    }


def test_health_does_not_leak_credentials_or_urls(
    tmp_path,
    monkeypatch,
) -> None:
    monkeypatch.setenv("P2PNET_SHADOW_ENABLED", "true")
    monkeypatch.setenv(
        "P2PNET_SHADOW_WEIGHT_URL",
        "https://minio.example.com/safy-ai-models/x.pth?X-Amz-Signature=secret",
    )
    monkeypatch.setenv("P2PNET_ASSET_AUTH_HEADER_NAME", "X-Secret-Header")
    monkeypatch.setenv("P2PNET_ASSET_AUTH_HEADER_VALUE", "super-secret-token")
    monkeypatch.setenv(
        "P2PNET_SHADOW_ROOT",
        str(tmp_path / "missing-source"),
    )
    monkeypatch.setenv(
        "P2PNET_SHADOW_WEIGHT_PATH",
        str(tmp_path / "missing-weight.pth"),
    )

    with TestClient(app) as startup_client:
        response = startup_client.get("/health")

    body = response.text

    assert response.status_code == 200
    assert "X-Amz-Signature" not in body
    assert "super-secret-token" not in body
    assert "X-Secret-Header" not in body
    assert "minio.example.com" not in body


def test_app_startup_prepares_p2pnet_assets(
    monkeypatch,
) -> None:
    startup_calls = []
    monkeypatch.setattr(
        main.p2pnet_assets,
        "initialize_p2pnet_shadow_assets",
        lambda: startup_calls.append(True),
    )

    with TestClient(app) as startup_client:
        response = startup_client.get("/health")

    assert response.status_code == 200
    assert startup_calls == [True]


def test_health_stays_up_when_p2pnet_assets_are_missing(
    tmp_path,
    monkeypatch,
) -> None:
    monkeypatch.setenv(
        "P2PNET_SHADOW_ENABLED",
        "true",
    )
    monkeypatch.setenv(
        "P2PNET_SHADOW_ROOT",
        str(tmp_path / "missing-source"),
    )
    monkeypatch.setenv(
        "P2PNET_SHADOW_WEIGHT_PATH",
        str(tmp_path / "missing-weight.pth"),
    )

    with TestClient(app) as startup_client:
        response = startup_client.get("/health")

    payload = response.json()

    assert response.status_code == 200
    assert payload["status"] == "ok"
    assert os.environ["P2PNET_SHADOW_ENABLED"] == "false"

    # 자산 실패 원인을 /health로 확인할 수 있어야 한다.
    assert payload["p2pnetShadow"]["ready"] is False
    assert payload["p2pnetShadow"]["status"] == "P2PNET_SOURCE_MISSING"
