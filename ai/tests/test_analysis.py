from fastapi.testclient import TestClient

from app.api import analysis
from app.main import app
from app.services.dense_scene_escalation import (
    CONGESTION_CORRECTION_ENABLED_VARIABLE,
    determine_dense_scene_assessment,
)
from app.services.frame_quality import FrameQualityResult
from app.services.person_detector import PersonDetectionResult
from app.services.shadow_analysis import (
    ModelAnalysisSnapshot,
    ShadowAnalysisDifferences,
    ShadowAnalysisRecord,
)


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


def fake_detection_value_error(
    _: bytes,
) -> PersonDetectionResult:
    raise ValueError(
        "Sensitive internal model error",
    )


def fake_congestion_failure(
    _: list[tuple[float, float]],
) -> None:
    raise RuntimeError("Congestion calculation failed")


def fake_quality_failure(_: bytes) -> None:
    raise RuntimeError("Frame quality inspection failed")


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
        "congestionSource": "YOLO",
        "visualCongestionScore": 0.94,
        "localPeakCongestionScore": 15.0,
        "densityLevel": None,
        "riskScore": None,
        "confidence": None,
        "qualityIssues": [],
        "failureCodes": [],
        "frameWidth": 1280,
        "frameHeight": 720,
        "brightnessScore": 127.5,
        "blurScore": 350.0,
        "modelName": "YOLO",
        "modelVersion": "yolo11n",
        "modelWeightId": "yolo11n.pt",
        "congestionRuleVersion": "yolo-grid-v0.2",
        "frameQualityRuleVersion": "frame-quality-v0.3",
        "processingTimeMs": 10,
    }


def test_completed_analysis_runs_enabled_shadow_in_background(
    monkeypatch,
) -> None:
    shadow_calls: list[tuple[bytes, ModelAnalysisSnapshot]] = []

    class FakeShadowService:
        enabled = True

        async def analyze_and_log(
            self,
            *,
            image_bytes: bytes,
            official: ModelAnalysisSnapshot,
        ) -> None:
            shadow_calls.append(
                (
                    image_bytes,
                    official,
                )
            )

    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_valid_quality,
    )
    monkeypatch.setattr(
        analysis,
        "detect_people",
        fake_detect_people,
    )
    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: FakeShadowService(),
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={
            "image": (
                "test.jpg",
                b"fake-image",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200
    assert response.json()["modelName"] == "YOLO"
    assert response.json()["personCount"] == 2
    assert len(shadow_calls) == 1
    assert shadow_calls[0][0] == b"fake-image"
    assert shadow_calls[0][1].model_name == "YOLO"
    assert shadow_calls[0][1].person_count == 2


def test_invalid_frame_does_not_run_shadow(
    monkeypatch,
) -> None:
    class FakeShadowService:
        enabled = True

        async def analyze_and_log(self, **_) -> None:
            raise AssertionError(
                "invalid frames must not run shadow analysis",
            )

    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_invalid_quality,
    )
    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: FakeShadowService(),
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


def test_shadow_configuration_failure_keeps_yolo_response(
    monkeypatch,
    caplog,
) -> None:
    caplog.set_level("ERROR")
    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_valid_quality,
    )
    monkeypatch.setattr(
        analysis,
        "detect_people",
        fake_detect_people,
    )

    def fail_to_build_shadow_service():
        raise RuntimeError("sensitive shadow configuration error")

    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        fail_to_build_shadow_service,
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={
            "image": (
                "test.jpg",
                b"fake-image",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200
    assert response.json()["analysisStatus"] == "COMPLETED"
    assert response.json()["modelName"] == "YOLO"
    assert response.json()["personCount"] == 2
    assert "P2PNet shadow analysis scheduling failed" in caplog.text


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
    assert response_body["frameQualityRuleVersion"] == "frame-quality-v0.3"
    assert response_body["confidence"] is None


def test_analysis_failure_returns_failed_status(
    monkeypatch,
    caplog,
) -> None:
    caplog.set_level("ERROR")
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
    assert response.json()["failureCodes"] == [
        "MODEL_INFERENCE_FAILED",
    ]
    assert response.json()["visualCongestionScore"] is None
    assert response.json()["localPeakCongestionScore"] is None

    response_body = response.json()

    assert response_body["confidence"] is None
    assert response_body["modelName"] == "YOLO"
    assert response_body["modelVersion"] == "yolo11n"
    assert response_body["modelWeightId"] == "yolo11n.pt"
    assert response_body["congestionRuleVersion"] == "yolo-grid-v0.2"
    assert response_body["frameQualityRuleVersion"] == "frame-quality-v0.3"
    assert "Person detection failed" in caplog.text


def test_detection_value_error_does_not_expose_detail(
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
        fake_detection_value_error,
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={
            "image": (
                "test.jpg",
                b"fake-image",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200
    assert response.json()["analysisStatus"] == "FAILED"
    assert response.json()["failureCodes"] == [
        "MODEL_INFERENCE_FAILED",
    ]
    assert "detail" not in response.json()
    assert "Sensitive internal model error" not in response.text


def test_congestion_failure_returns_failure_code(
    monkeypatch,
    caplog,
) -> None:
    caplog.set_level("ERROR")
    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_valid_quality,
    )
    monkeypatch.setattr(
        analysis,
        "detect_people",
        fake_detect_people,
    )
    monkeypatch.setattr(
        analysis,
        "calculate_congestion",
        fake_congestion_failure,
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={
            "image": (
                "test.jpg",
                b"fake-image",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200
    assert response.json()["analysisStatus"] == "FAILED"
    assert response.json()["failureCodes"] == [
        "CONGESTION_CALCULATION_FAILED",
    ]
    assert response.json()["personCount"] is None
    assert "Congestion calculation failed" in caplog.text


def test_quality_inspection_failure_returns_internal_error(
    monkeypatch,
    caplog,
) -> None:
    caplog.set_level("ERROR")
    monkeypatch.setattr(
        analysis,
        "inspect_frame_quality",
        fake_quality_failure,
    )

    response = client.post(
        "/api/v1/analyze/frame",
        files={
            "image": (
                "test.jpg",
                b"fake-image",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200
    assert response.json()["analysisStatus"] == "FAILED"
    assert response.json()["failureCodes"] == [
        "INTERNAL_AI_ERROR",
    ]
    assert response.json()["qualityIssues"] == []
    assert response.json()["personCount"] is None
    assert response.json()["frameWidth"] is None
    assert response.json()["frameHeight"] is None
    assert "Frame quality inspection failed" in caplog.text


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


def _build_shadow_service(
    *,
    shadow_snapshot: ModelAnalysisSnapshot,
    call_log: list[str],
):
    """보정 경로 검증용 가짜 그림자 서비스."""

    class FakeShadowService:
        enabled = True

        async def analyze_and_log(
            self,
            *,
            image_bytes: bytes,
            official: ModelAnalysisSnapshot,
        ):
            call_log.append("awaited")

            return ShadowAnalysisRecord(
                analysis_id="analysis-1",
                frame_sha256="sha",
                selection_status="YOLO_PRIMARY_P2PNET_SHADOW",
                automatic_fallback="DISABLED",
                official=official,
                shadow=shadow_snapshot,
                differences=ShadowAnalysisDifferences(
                    person_count=None,
                    visual_congestion_score=None,
                    local_peak_congestion_score=None,
                ),
                dense_scene=determine_dense_scene_assessment(
                    official_local_peak_congestion_score=(
                        official.local_peak_congestion_score
                    ),
                    shadow_local_peak_congestion_score=(
                        shadow_snapshot.local_peak_congestion_score
                    ),
                ),
            )

    return FakeShadowService()


def _shadow_snapshot(
    *,
    visual: float | None,
    local_peak: float | None,
    low_confidence: bool | None,
    status: str = "COMPLETED",
) -> ModelAnalysisSnapshot:
    return ModelAnalysisSnapshot(
        model_name="P2PNet",
        model_version="safy-p2pnet-v2-epoch3",
        model_weight_id="epoch-003.pth",
        analysis_status=status,
        person_count=180,
        visual_congestion_score=visual,
        local_peak_congestion_score=local_peak,
        processing_time_ms=900,
        failure_codes=[],
        low_confidence_signal=low_confidence,
    )


def test_correction_disabled_keeps_yolo_congestion(monkeypatch) -> None:
    monkeypatch.delenv(
        CONGESTION_CORRECTION_ENABLED_VARIABLE,
        raising=False,
    )
    monkeypatch.setattr(analysis, "inspect_frame_quality", fake_valid_quality)
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    call_log: list[str] = []
    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: _build_shadow_service(
            shadow_snapshot=_shadow_snapshot(
                visual=90.0,
                local_peak=100.0,
                low_confidence=False,
            ),
            call_log=call_log,
        ),
    )

    payload = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    ).json()

    # 플래그가 꺼져 있으면 YOLO 값을 그대로 쓴다.
    assert payload["congestionSource"] == "YOLO"
    assert payload["visualCongestionScore"] == 0.94


def test_correction_enabled_raises_congestion_scores(monkeypatch) -> None:
    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "true")
    monkeypatch.setattr(analysis, "inspect_frame_quality", fake_valid_quality)
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    call_log: list[str] = []
    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: _build_shadow_service(
            shadow_snapshot=_shadow_snapshot(
                visual=90.0,
                local_peak=100.0,
                low_confidence=False,
            ),
            call_log=call_log,
        ),
    )

    payload = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    ).json()

    assert call_log == ["awaited"]
    assert payload["congestionSource"] == "P2PNET_CORRECTED"
    assert payload["visualCongestionScore"] == 90.0
    assert payload["localPeakCongestionScore"] == 100.0
    # 공식 인원수는 여전히 YOLO 결과다.
    assert payload["personCount"] == 2
    assert payload["modelName"] == "YOLO"


def test_correction_enabled_but_gate_blocks_keeps_yolo(monkeypatch) -> None:
    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "true")
    monkeypatch.setattr(analysis, "inspect_frame_quality", fake_valid_quality)
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    call_log: list[str] = []
    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: _build_shadow_service(
            shadow_snapshot=_shadow_snapshot(
                visual=90.0,
                local_peak=100.0,
                low_confidence=True,
            ),
            call_log=call_log,
        ),
    )

    payload = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    ).json()

    assert payload["congestionSource"] == "YOLO"
    assert payload["visualCongestionScore"] == 0.94


def test_correction_enabled_with_failed_shadow_returns_failed(monkeypatch) -> None:
    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "true")
    monkeypatch.setattr(analysis, "inspect_frame_quality", fake_valid_quality)
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    call_log: list[str] = []
    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: _build_shadow_service(
            shadow_snapshot=_shadow_snapshot(
                visual=None,
                local_peak=None,
                low_confidence=None,
                status="FAILED",
            ),
            call_log=call_log,
        ),
    )

    payload = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    ).json()

    assert payload["analysisStatus"] == "FAILED"
    assert payload["congestionSource"] == "YOLO"
    assert payload["personCount"] is None
    assert payload["visualCongestionScore"] is None
    assert payload["localPeakCongestionScore"] is None
    assert payload["failureCodes"] == [
        "MODEL_INFERENCE_FAILED",
    ]


def test_correction_enabled_without_available_p2pnet_returns_failed(
    monkeypatch,
) -> None:
    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "true")
    monkeypatch.setattr(analysis, "inspect_frame_quality", fake_valid_quality)
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    class DisabledShadowService:
        enabled = False

    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: DisabledShadowService(),
    )

    payload = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    ).json()

    assert payload["analysisStatus"] == "FAILED"
    assert payload["personCount"] is None
    assert payload["visualCongestionScore"] is None
    assert payload["localPeakCongestionScore"] is None
    assert payload["failureCodes"] == [
        "MODEL_INFERENCE_FAILED",
    ]


def test_correction_enabled_with_incomplete_p2pnet_result_returns_failed(
    monkeypatch,
) -> None:
    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "true")
    monkeypatch.setattr(analysis, "inspect_frame_quality", fake_valid_quality)
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: _build_shadow_service(
            shadow_snapshot=_shadow_snapshot(
                visual=None,
                local_peak=None,
                low_confidence=False,
            ),
            call_log=[],
        ),
    )

    payload = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    ).json()

    assert payload["analysisStatus"] == "FAILED"
    assert payload["personCount"] is None
    assert payload["visualCongestionScore"] is None
    assert payload["localPeakCongestionScore"] is None
    assert payload["failureCodes"] == [
        "MODEL_INFERENCE_FAILED",
    ]


def test_correction_enabled_with_p2pnet_exception_returns_failed(
    monkeypatch,
) -> None:
    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "true")
    monkeypatch.setattr(analysis, "inspect_frame_quality", fake_valid_quality)
    monkeypatch.setattr(analysis, "detect_people", fake_detect_people)

    class FailingShadowService:
        enabled = True

        async def analyze_and_log(self, **_) -> None:
            raise RuntimeError("sensitive P2PNet failure")

    monkeypatch.setattr(
        analysis,
        "get_shadow_analysis_service",
        lambda: FailingShadowService(),
    )

    payload = client.post(
        "/api/v1/analyze/frame",
        files={"image": ("test.jpg", b"fake-image", "image/jpeg")},
    ).json()

    assert payload["analysisStatus"] == "FAILED"
    assert payload["personCount"] is None
    assert payload["visualCongestionScore"] is None
    assert payload["localPeakCongestionScore"] is None
    assert payload["failureCodes"] == [
        "MODEL_INFERENCE_FAILED",
    ]
