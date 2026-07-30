from datetime import date, datetime
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator

from app.schemas.disaster_message import DisasterMessage
from app.schemas.seoul_congestion import SeoulCongestion
from app.schemas.seoul_event import SeoulEvent


CollectionStatus = Literal[
    "SUCCESS",
    "PARTIAL_SUCCESS",
    "FAILED",
]


class AreaCollectionRequest(BaseModel):
    area_codes: list[str] = Field(
        min_length=1,
        max_length=200,
    )
    max_concurrency: int = Field(
        default=15,
        ge=1,
        le=20,
    )

    @field_validator("area_codes")
    @classmethod
    def normalize_area_codes(
        cls,
        values: list[str],
    ) -> list[str]:
        normalized = list(
            dict.fromkeys(
                value.strip()
                for value in values
                if value.strip()
            )
        )

        if not normalized:
            raise ValueError("지역 코드가 하나 이상 필요합니다.")

        return normalized


class SeoulCongestionCollectionRequest(
    AreaCollectionRequest
):
    pass


class SeoulEventCollectionRequest(AreaCollectionRequest):
    target_date: date | None = None


class DisasterMessageCollectionRequest(BaseModel):
    page_no: int = Field(default=1, ge=1)
    num_of_rows: int = Field(default=100, ge=1, le=1000)
    start_date: date | None = None
    region: str | None = None

    @field_validator("region")
    @classmethod
    def normalize_region(
        cls,
        value: str | None,
    ) -> str | None:
        if value is None:
            return None

        return value.strip() or None


class CollectionRun(BaseModel):
    external_run_id: str
    source: str
    started_at: datetime
    finished_at: datetime
    status: CollectionStatus
    success_count: int
    failure_count: int
    error_code: str | None = None
    error_message: str | None = None
    error_details: dict[str, Any] | None = None


class CollectionFailure(BaseModel):
    area: str
    error: str


class SeoulCongestionCollectionResponse(BaseModel):
    collection_run: CollectionRun
    items: list[SeoulCongestion]
    failures: list[CollectionFailure] = Field(
        default_factory=list
    )


class SeoulEventCollectionResponse(BaseModel):
    collection_run: CollectionRun
    items: list[SeoulEvent]
    failures: list[CollectionFailure] = Field(
        default_factory=list
    )


class DisasterMessageCollectionResponse(BaseModel):
    collection_run: CollectionRun
    items: list[DisasterMessage]
