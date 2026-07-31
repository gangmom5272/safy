from typing import Literal, Self

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    model_validator,
)


AnalysisStatus = Literal[
    "COMPLETED",
    "INVALID_FRAME",
    "FAILED",
]

QualityIssue = Literal[
    "TOO_DARK",
    "TOO_BRIGHT",
    "TOO_BLURRY",
    "LOW_RESOLUTION",
    "DECODE_FAILED",
]

FailureCode = Literal[
    "MODEL_INFERENCE_FAILED",
    "CONGESTION_CALCULATION_FAILED",
    "INTERNAL_AI_ERROR",
]

CongestionSource = Literal[
    "YOLO",
    "P2PNET_CORRECTED",
]


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
                    "congestionSource": "YOLO",
                    "densityLevel": None,
                    "riskScore": None,
                    "confidence": None,
                    "qualityIssues": [],
                    "failureCodes": [],
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
    congestion_source: CongestionSource = Field(
        default="YOLO",
        alias="congestionSource",
        description=(
            "C·L 값의 출처입니다. P2PNET_CORRECTED는 고밀도 장면에서 "
            "위험을 올리는 방향으로만 P2PNet 값으로 보정했다는 뜻입니다."
        ),
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
    quality_issues: list[QualityIssue] = Field(
        default_factory=list,
        alias="qualityIssues",
    )
    failure_codes: list[FailureCode] = Field(
        default_factory=list,
        alias="failureCodes",
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
    processing_time_ms: int = Field(
        alias="processingTimeMs",
    )

    @model_validator(mode="after")
    def validate_status_codes(self) -> Self:
        if self.analysis_status == "COMPLETED" and self.failure_codes:
            raise ValueError("COMPLETED 응답에는 failureCodes를 포함할 수 없습니다")

        if self.analysis_status == "INVALID_FRAME":
            if self.failure_codes:
                raise ValueError(
                    "INVALID_FRAME 응답에는 failureCodes를 포함할 수 없습니다"
                )

            if not self.quality_issues:
                raise ValueError("INVALID_FRAME 응답에는 qualityIssues가 필요합니다")

        if self.analysis_status == "FAILED" and not self.failure_codes:
            raise ValueError("FAILED 응답에는 failureCodes가 필요합니다")

        return self
