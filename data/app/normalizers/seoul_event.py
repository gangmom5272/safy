from datetime import date, datetime, timedelta, timezone

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


def normalize_seoul_event(
    raw: SeoulEventRaw,
    area_name: str,
    area_code: str,
) -> SeoulEvent:
    start_date, end_date = parse_event_period(
        raw.event_period
    )
    return SeoulEvent(
        area_name=area_name.strip(),
        area_code=area_code.strip(),
        name=raw.event_name.strip(),
        period=raw.event_period.strip(),
        start_date=start_date,
        end_date=end_date,
        place=raw.event_place.strip(),
        longitude=coordinate_to_float(raw.longitude),
        latitude=coordinate_to_float(raw.latitude),
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