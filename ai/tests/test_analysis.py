from fastapi.testclient import TestClient

from app.api import analysis
from app.main import app
from app.services.person_detector import PersonDetectionResult


client = TestClient(app)


def fake_detect_people(_: bytes) -> PersonDetectionResult:
    return PersonDetectionResult(
        person_count=2,
        confidences=[0.9, 0.8],
        processing_time_ms=10,
    )


def test_analyze_frame(monkeypatch) -> None:
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    response = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json() == {
        "analysisStatus": "COMPLETED",
        "personCount": 2,
        "personConfidences": [0.9, 0.8],
        "densityLevel": None,
        "riskScore": None,
        "confidence": None,
        "modelName": "YOLO",
        "modelVersion": "yolo11n",
        "processingTimeMs": 10,
    }