from types import SimpleNamespace

from app.services import (
    yolo_benchmark_adapter,
)


def test_load_yolo_predictor_returns_count(
    monkeypatch,
) -> None:
    loaded_models: list[str] = []

    monkeypatch.setattr(
        yolo_benchmark_adapter,
        "get_model",
        lambda: loaded_models.append("loaded"),
    )

    monkeypatch.setattr(
        yolo_benchmark_adapter,
        "detect_people",
        lambda image_bytes: SimpleNamespace(person_count=7),
    )

    predictor = yolo_benchmark_adapter.load_yolo_predictor()

    predicted_count = predictor(b"image")

    assert loaded_models == ["loaded"]
    assert predicted_count == 7
