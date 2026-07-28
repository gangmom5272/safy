import pytest

from app.schemas.analysis import FrameAnalysisResponse


def test_analysis_schema_contains_completed_example() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    example = schema["examples"][0]

    assert example["analysisStatus"] == "COMPLETED"
    assert example["personCount"] == 4
    assert example["personConfidences"] == [
        0.91,
        0.88,
        0.84,
        0.72,
    ]
    assert example["confidence"] is None
    assert example["modelName"] == "YOLO"
    assert example["modelVersion"] == "yolo11n"
    assert example["modelWeightId"] == "yolo11n.pt"
    assert example["congestionRuleVersion"] == "yolo-grid-v0.2"
    assert example["frameQualityRuleVersion"] == "frame-quality-v0.2"


def test_analysis_schema_accepts_calibrated_confidence() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    payload = schema["examples"][0].copy()
    payload["confidence"] = 0.82

    response = FrameAnalysisResponse.model_validate(
        payload,
    )

    assert response.confidence == 0.82


def test_analysis_schema_rejects_unknown_failure_code() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    payload = schema["examples"][0].copy()
    payload["analysisStatus"] = "FAILED"
    payload["failureCodes"] = [
        "UNKNOWN_FAILURE",
    ]

    with pytest.raises(ValueError):
        FrameAnalysisResponse.model_validate(
            payload,
        )


def test_analysis_schema_rejects_unknown_quality_issue() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    payload = schema["examples"][0].copy()
    payload["analysisStatus"] = "INVALID_FRAME"
    payload["qualityIssues"] = [
        "UNKNOWN_QUALITY_ISSUE",
    ]

    with pytest.raises(ValueError):
        FrameAnalysisResponse.model_validate(
            payload,
        )


def test_completed_response_rejects_failure_codes() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    payload = schema["examples"][0].copy()
    payload["analysisStatus"] = "COMPLETED"
    payload["failureCodes"] = [
        "INTERNAL_AI_ERROR",
    ]

    with pytest.raises(
        ValueError,
        match=("COMPLETED 응답에는 failureCodes를 포함할 수 없습니다"),
    ):
        FrameAnalysisResponse.model_validate(
            payload,
        )


def test_invalid_frame_rejects_failure_codes() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    payload = schema["examples"][0].copy()
    payload["analysisStatus"] = "INVALID_FRAME"
    payload["qualityIssues"] = [
        "TOO_DARK",
    ]
    payload["failureCodes"] = [
        "INTERNAL_AI_ERROR",
    ]

    with pytest.raises(
        ValueError,
        match=("INVALID_FRAME 응답에는 failureCodes를 포함할 수 없습니다"),
    ):
        FrameAnalysisResponse.model_validate(
            payload,
        )


def test_invalid_frame_requires_quality_issue() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    payload = schema["examples"][0].copy()
    payload["analysisStatus"] = "INVALID_FRAME"
    payload["qualityIssues"] = []
    payload["failureCodes"] = []

    with pytest.raises(
        ValueError,
        match=("INVALID_FRAME 응답에는 qualityIssues가 필요합니다"),
    ):
        FrameAnalysisResponse.model_validate(
            payload,
        )


def test_failed_response_requires_failure_code() -> None:
    schema = FrameAnalysisResponse.model_json_schema(
        by_alias=True,
    )
    payload = schema["examples"][0].copy()
    payload["analysisStatus"] = "FAILED"
    payload["personCount"] = None
    payload["personConfidences"] = []
    payload["visualCongestionScore"] = None
    payload["localPeakCongestionScore"] = None
    payload["qualityIssues"] = []
    payload["failureCodes"] = []

    with pytest.raises(
        ValueError,
        match=("FAILED 응답에는 failureCodes가 필요합니다"),
    ):
        FrameAnalysisResponse.model_validate(
            payload,
        )
