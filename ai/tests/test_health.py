import os

from fastapi.testclient import TestClient

from app import main
from app.main import app


client = TestClient(app)


def test_health_check() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


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

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert os.environ["P2PNET_SHADOW_ENABLED"] == "false"
