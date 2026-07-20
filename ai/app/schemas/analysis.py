from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class FrameAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    analysis_status: Literal["COMPLETED"] = Field(alias="analysisStatus")
    person_count: int = Field(alias="personCount")
    person_confidences: list[float] = Field(alias="personConfidences")
    density_level: None = Field(default=None, alias="densityLevel")
    risk_score: None = Field(default=None, alias="riskScore")
    confidence: None = None
    model_name: str = Field(alias="modelName")
    model_version: str = Field(alias="modelVersion")
    processing_time_ms: int = Field(alias="processingTimeMs")