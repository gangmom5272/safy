import hashlib
import json
import unicodedata
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

from app.schemas.seoul_event import (
    SeoulEvent,
    SeoulEventBatchRaw,
    SeoulEventRaw,
)


KST = timezone(timedelta(hours=9))


def empty_to_none(value: str | None) -> str | None:
    if value is None:
        return None

    stripped = value.strip()
    return stripped or None


def coordinate_to_float(
    value: float | str | None,
) -> float | None:
    if value is None:
        return None

    if isinstance(value, (int, float)):
        return float(value)

    normalized = empty_to_none(value)

    if normalized is None:
        return None

    return float(normalized)


def normalize_payment_status(
    value: str | None,
) -> bool | None:
    normalized = empty_to_none(value)

    if normalized is None:
        return None

    normalized = normalized.upper()

    if normalized in {"Y", "유료"}:
        return True

    if normalized in {"N", "무료"}:
        return False

    return None


def parse_event_period(
    value: str,
) -> tuple[date | None, date | None]:
    normalized = value.strip()

    if not normalized:
        return None, None

    try:
        start_text, end_text = normalized.split(
            "~",
            maxsplit=1,
        )
        start_date = date.fromisoformat(
            start_text.strip()
        )
        end_date = date.fromisoformat(
            end_text.strip()
        )
    except ValueError:
        return None, None

    if start_date > end_date:
        return None, None

    return start_date, end_date


def normalize_identity_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value)
    return " ".join(normalized.split()).casefold()


def normalize_identity_coordinate(
    value: float | None,
) -> str:
    if value is None:
        return ""

    coordinate = Decimal(str(value)).quantize(
        Decimal("0.0000001")
    )
    return format(coordinate, "f")


def generate_seoul_event_external_id(
    *,
    area_code: str,
    name: str,
    period: str,
    place: str,
    longitude: float | None,
    latitude: float | None,
) -> str:
    start_date, end_date = parse_event_period(period)
    canonical_period = (
        f"{start_date.isoformat()}~{end_date.isoformat()}"
        if start_date is not None and end_date is not None
        else normalize_identity_text(period)
    )
    identity_fields = {
        "area_code": normalize_identity_text(area_code),
        "latitude": normalize_identity_coordinate(latitude),
        "longitude": normalize_identity_coordinate(longitude),
        "name": normalize_identity_text(name),
        "period": canonical_period,
        "place": normalize_identity_text(place),
    }
    canonical_value = json.dumps(
        identity_fields,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )

    return hashlib.sha256(
        canonical_value.encode("utf-8")
    ).hexdigest()


def normalize_seoul_event(
    raw: SeoulEventRaw,
    area_name: str,
    area_code: str,
) -> SeoulEvent:
    start_date, end_date = parse_event_period(
        raw.event_period
    )
    area_name = area_name.strip()
    area_code = area_code.strip()
    name = raw.event_name.strip()
    period = raw.event_period.strip()
    place = raw.event_place.strip()
    longitude = coordinate_to_float(raw.longitude)
    latitude = coordinate_to_float(raw.latitude)

    return SeoulEvent(
        external_id=generate_seoul_event_external_id(
            area_code=area_code,
            name=name,
            period=period,
            place=place,
            longitude=longitude,
            latitude=latitude,
        ),
        area_name=area_name,
        area_code=area_code,
        name=name,
        period=period,
        start_date=start_date,
        end_date=end_date,
        place=place,
        longitude=longitude,
        latitude=latitude,
        is_paid=normalize_payment_status(raw.pay_yn),
        thumbnail_url=empty_to_none(raw.thumbnail_url),
        detail_url=empty_to_none(raw.detail_url),
        details=empty_to_none(raw.details),
    )


def filter_active_events(
    events: list[SeoulEvent],
    target_date: date | None = None,
) -> list[SeoulEvent]:
    comparison_date = (
        target_date or datetime.now(KST).date()
    )

    return [
        event
        for event in events
        if event.start_date is not None
        and event.end_date is not None
        and event.start_date
        <= comparison_date
        <= event.end_date
    ]


def normalize_active_seoul_events(
    batch: SeoulEventBatchRaw,
    target_date: date | None = None,
) -> list[SeoulEvent]:
    normalized_events = [
        normalize_seoul_event(
            raw,
            area_name=batch.area_name,
            area_code=batch.area_code,
        )
        for raw in batch.events
    ]

    return filter_active_events(
        normalized_events,
        target_date=target_date,
    )
