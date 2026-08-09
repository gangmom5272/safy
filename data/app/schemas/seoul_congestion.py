from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class SeoulCongestionRaw(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    area_name: str = Field(alias="AREA_NM")
    area_code: str = Field(alias="AREA_CD")
    congestion_level: str = Field(
        alias="AREA_CONGEST_LVL",
    )
    congestion_message: str = Field(
        alias="AREA_CONGEST_MSG",
    )
    population_min: str | int = Field(
        alias="AREA_PPLTN_MIN",
    )
    population_max: str | int = Field(
        alias="AREA_PPLTN_MAX",
    )
    replacement_yn: str | None = Field(
        default=None,
        alias="REPLACE_YN",
    )
    observed_at: str = Field(alias="PPLTN_TIME")


class SeoulCongestion(BaseModel):
    area_name: str
    area_code: str
    congestion_level: str
    congestion_message: str
    population_min: int
    population_max: int
    is_replaced: bool | None
    observed_at: datetime
    latitude: float | None = None
    longitude: float | None = None
    source: Literal["seoul-citydata-ppltn"] = (
        "seoul-citydata-ppltn"
    )


class SeoulCongestionCollectionFailure(BaseModel):
    area: str
    error: str


class SeoulCongestionBatchRaw(BaseModel):
    requested_count: int
    success_count: int
    failure_count: int
    items: list[SeoulCongestionRaw]
    failures: list[SeoulCongestionCollectionFailure]


class SeoulCongestionBatch(BaseModel):
    status: Literal[
        "SUCCESS",
        "PARTIAL_SUCCESS",
        "FAILED",
    ]
    requested_count: int
    success_count: int
    failure_count: int
    items: list[SeoulCongestion]
    failures: list[SeoulCongestionCollectionFailure]
