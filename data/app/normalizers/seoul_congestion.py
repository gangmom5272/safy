from datetime import datetime, timedelta, timezone

from app.schemas.seoul_congestion import (
    SeoulCongestion,
    SeoulCongestionRaw,
)


KST = timezone(timedelta(hours=9))

PPLTN_TIME_FORMATS = (
    "%Y-%m-%d %H:%M",
    "%Y-%m-%d %H:%M:%S",
    "%Y%m%d %H%M",
    "%Y%m%d %H%M%S",
)


def parse_population(value: str | int) -> int:
    if isinstance(value, int):
        return value

    return int(value.replace(",", "").strip())


def parse_observed_at(value: str) -> datetime:
    normalized = value.strip()

    for time_format in PPLTN_TIME_FORMATS:
        try:
            return datetime.strptime(
                normalized,
                time_format,
            ).replace(tzinfo=KST)
        except ValueError:
            continue

    raise ValueError(
        f"지원하지 않는 인구 관측 시각 형식입니다: {value}"
    )


def normalize_replacement_status(
    value: str | None,
) -> bool | None:
    if value is None:
        return None

    normalized = value.strip().upper()

    if normalized == "Y":
        return True

    if normalized == "N":
        return False

    return None


def normalize_seoul_congestion(
    raw: SeoulCongestionRaw,
) -> SeoulCongestion:
    return SeoulCongestion(
        area_name=raw.area_name.strip(),
        area_code=raw.area_code.strip(),
        congestion_level=raw.congestion_level.strip(),
        congestion_message=(
            raw.congestion_message.strip()
        ),
        population_min=parse_population(
            raw.population_min
        ),
        population_max=parse_population(
            raw.population_max
        ),
        is_replaced=normalize_replacement_status(
            raw.replacement_yn
        ),
        observed_at=parse_observed_at(raw.observed_at),
    )
