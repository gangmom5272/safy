from app.schemas.seoul_event import (
    SeoulEvent,
    SeoulEventRaw,
)


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


def normalize_seoul_event(
    raw: SeoulEventRaw,
    area_name: str,
    area_code: str,
) -> SeoulEvent:
    return SeoulEvent(
        area_name=area_name.strip(),
        area_code=area_code.strip(),
        name=raw.event_name.strip(),
        period=raw.event_period.strip(),
        place=raw.event_place.strip(),
        longitude=coordinate_to_float(raw.longitude),
        latitude=coordinate_to_float(raw.latitude),
        is_paid=normalize_payment_status(raw.pay_yn),
        thumbnail_url=empty_to_none(raw.thumbnail_url),
        detail_url=empty_to_none(raw.detail_url),
        details=empty_to_none(raw.details),
    )