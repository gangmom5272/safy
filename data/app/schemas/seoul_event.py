from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class SeoulEventRaw(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    event_name: str = Field(alias="EVENT_NM")
    event_period: str = Field(alias="EVENT_PERIOD")
    event_place: str = Field(alias="EVENT_PLACE")
    longitude: float | str | None = Field(
        default=None,
        alias="EVENT_X",
    )
    latitude: float | str | None = Field(
        default=None,
        alias="EVENT_Y",
    )
    pay_yn: str | None = Field(
        default=None,
        alias="PAY_YN",
    )
    thumbnail_url: str | None = Field(
        default=None,
        alias="THUMBNAIL",
    )
    detail_url: str | None = Field(
        default=None,
        alias="URL",
    )
    details: str | None = Field(
        default=None,
        alias="EVENT_ETC_DETAIL",
    )


class SeoulEventBatchRaw(BaseModel):
    area_name: str
    area_code: str
    events: list[SeoulEventRaw]


class SeoulEvent(BaseModel):
    external_id: str
    area_name: str
    area_code: str
    name: str
    period: str
    start_date: date | None
    end_date: date | None
    place: str
    longitude: float | None
    latitude: float | None
    is_paid: bool | None
    thumbnail_url: str | None
    detail_url: str | None
    details: str | None
    source: Literal["seoul-citydata"] = "seoul-citydata"
