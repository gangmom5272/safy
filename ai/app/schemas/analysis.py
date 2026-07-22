from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


AnalysisStatus = Literal["COMPLETED", "INVALID_FRAME", "FAILED"]


class FrameAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

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
    confidence: None = None
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
    processing_time_ms: int = Field(alias="processingTimeMs")