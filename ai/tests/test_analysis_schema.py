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
