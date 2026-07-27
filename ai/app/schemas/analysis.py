from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


AnalysisStatus = Literal["COMPLETED", "INVALID_FRAME", "FAILED"]


class FrameAnalysisResponse(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        json_schema_extra={
            "examples": [
                {
                    "analysisStatus": "COMPLETED",
                    "personCount": 4,
                    "personConfidences": [
                        0.91,
                        0.88,
                        0.84,
                        0.72,
                    ],
                    "visualCongestionScore": 1.88,
                    "localPeakCongestionScore": 15.0,
                    "densityLevel": None,
                    "riskScore": None,
                    "confidence": None,
                    "qualityIssues": [],
                    "frameWidth": 810,
                    "frameHeight": 1080,
                    "brightnessScore": 127.5,
                    "blurScore": 350.0,
                    "modelName": "YOLO",
                    "modelVersion": "yolo11n",
                    "modelWeightId": "yolo11n.pt",
                    "congestionRuleVersion": ("yolo-grid-v0.2"),
                    "frameQualityRuleVersion": ("frame-quality-v0.2"),
                    "processingTimeMs": 36,
                }
            ]
        },
    )

    analysis_status: AnalysisStatus = Field(alias="analysisStatus")
    person_count: int | None = Field(default=None, alias="personCount")
    person_confidences: list[float] = Field(
        default_factory=list,
        alias="personConfidences",
    )
    visual_congestion_score: float | None = Field(
        default=None,
        alias="visualCongestionScore",
    )
    local_peak_congestion_score: float | None = Field(
        default=None,
        alias="localPeakCongestionScore",
    )
    density_level: None = Field(default=None, alias="densityLevel")
    risk_score: None = Field(default=None, alias="riskScore")
    confidence: float | None = Field(
        default=None,
        ge=0.0,
        le=1.0,
        description=(
            "보정된 분석 결과 신뢰도입니다. MVP에서는 산정하지 않아 null을 반환합니다."
        ),
    )
    quality_issues: list[str] = Field(
        default_factory=list,
        alias="qualityIssues",
    )
    frame_width: int | None = Field(default=None, alias="frameWidth")
    frame_height: int | None = Field(default=None, alias="frameHeight")
    brightness_score: float | None = Field(
        default=None,
        alias="brightnessScore",
    )
    blur_score: float | None = Field(default=None, alias="blurScore")
    model_name: str = Field(alias="modelName")
    model_version: str = Field(alias="modelVersion")
    model_weight_id: str = Field(alias="modelWeightId")
    congestion_rule_version: str = Field(
        alias="congestionRuleVersion",
    )
    frame_quality_rule_version: str = Field(
        alias="frameQualityRuleVersion",
    )
    processing_time_ms: int = Field(alias="processingTimeMs")
