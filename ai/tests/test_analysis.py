from fastapi.testclient import TestClient

from app.api import analysis
from app.main import app
from app.services.frame_quality import FrameQualityResult
from app.services.person_detector import PersonDetectionResult


client = TestClient(app)


def fake_valid_quality(_: bytes) -> FrameQualityResult:
    return FrameQualityResult(
        is_valid=True,
        issues=[],
        width=1280,
        height=720,
        brightness_score=127.5,
        blur_score=350.0,
    )


def fake_detect_people(_: bytes) -> PersonDetectionResult:
    return PersonDetectionResult(
        person_count=2,
        confidences=[0.9, 0.8],
        processing_time_ms=10,
        person_centers=[
            (0.10, 0.10),
            (0.11, 0.11),
        ],
    )


def fake_detect_no_people(_: bytes) -> PersonDetectionResult:
    return PersonDetectionResult(
        person_count=0,
        confidences=[],
        processing_time_ms=5,
        person_centers=[],
    )


def fake_invalid_quality(_: bytes) -> FrameQualityResult:
    return FrameQualityResult(
        is_valid=False,
        issues=["TOO_DARK"],
        width=1280,
        height=720,
        brightness_score=10.0,
        blur_score=0.0,
    )


def fail_if_detection_runs(_: bytes) -> PersonDetectionResult:
    raise AssertionError("품질 미달 프레임에서 YOLO가 실행되면 안 됩니다.")


def fake_detection_failure(_: bytes) -> PersonDetectionResult:
    raise RuntimeError("YOLO inference failed")


def test_analyze_frame(monkeypatch) -> None:
    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_valid_quality,
        raising=False,
    )
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
        "visualCongestionScore": 0.94,
        "localPeakCongestionScore": 15.0,
        "densityLevel": None,
        "riskScore": None,
        "confidence": None,
        "qualityIssues": [],
        "frameWidth": 1280,
        "frameHeight": 720,
        "brightnessScore": 127.5,
        "blurScore": 350.0,
        "modelName": "YOLO",
        "modelVersion": "yolo11n",
        "modelWeightId": "yolo11n.pt",
        "congestionRuleVersion": "yolo-grid-v0.2",
        "frameQualityRuleVersion": "frame-quality-v0.2",
        "processingTimeMs": 10,
    }


def test_invalid_frame_skips_detection(monkeypatch) -> None:
    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_invalid_quality,
        raising=False,
    )
    monkeypatch.setattr(
        analysis,
        "detect_people",
        fail_if_detection_runs,
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={
            "image": (
                "dark.jpg",
                b"fake-image",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200
    assert response.json()["analysisStatus"] == "INVALID_FRAME"
    assert response.json()["personCount"] is None
    assert response.json()["qualityIssues"] == ["TOO_DARK"]
    assert response.json()["visualCongestionScore"] is None
    assert response.json()["localPeakCongestionScore"] is None

    response_body = response.json()

    assert response_body["modelName"] == "YOLO"
    assert response_body["modelVersion"] == "yolo11n"
    assert response_body["modelWeightId"] == "yolo11n.pt"
    assert response_body["congestionRuleVersion"] == "yolo-grid-v0.2"
    assert response_body["frameQualityRuleVersion"] == "frame-quality-v0.2"
    assert response_body["confidence"] is None


def test_analysis_failure_returns_failed_status(monkeypatch) -> None:
    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_valid_quality,
    )
    monkeypatch.setattr(
        analysis,
        "detect_people",
        fake_detection_failure,
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json()["analysisStatus"] == "FAILED"
    assert response.json()["personCount"] is None
    assert response.json()["personConfidences"] == []
    assert response.json()["qualityIssues"] == []
    assert response.json()["visualCongestionScore"] is None
    assert response.json()["localPeakCongestionScore"] is None

    response_body = response.json()

    assert response_body["confidence"] is None
    assert response_body["modelName"] == "YOLO"
    assert response_body["modelVersion"] == "yolo11n"
    assert response_body["modelWeightId"] == "yolo11n.pt"
    assert response_body["congestionRuleVersion"] == "yolo-grid-v0.2"
    assert response_body["frameQualityRuleVersion"] == "frame-quality-v0.2"


def test_no_people_returns_completed_with_zero_congestion(
    monkeypatch,
) -> None:
    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_valid_quality,
    )
    monkeypatch.setattr(
        analysis,
        "detect_people",
        fake_detect_no_people,
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("empty.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json()["analysisStatus"] == "COMPLETED"
    assert response.json()["personCount"] == 0
    assert response.json()["visualCongestionScore"] == 0.0
    assert response.json()["localPeakCongestionScore"] == 0.0
