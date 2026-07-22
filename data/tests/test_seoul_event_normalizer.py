from datetime import date

from app.normalizers.seoul_event import (
    filter_active_events,
    normalize_active_seoul_events,
    normalize_seoul_event,
    parse_event_period,
)
from app.schemas.seoul_event import (
    SeoulEventBatchRaw,
    SeoulEventRaw,
)


def test_normalize_seoul_event() -> None:
    raw = SeoulEventRaw.model_validate(
        {
            "EVENT_NM": " 광화문 문화축제 ",
            "EVENT_PERIOD": (
                "2026-07-21~2026-07-25"
            ),
            "EVENT_PLACE": " 광화문광장 ",
            "EVENT_X": 126.9760053,
            "EVENT_Y": 37.5726241,
            "PAY_YN": None,
            "THUMBNAIL": (
                "https://example.com/event.jpg"
            ),
            "URL": "https://example.com/event",
            "EVENT_ETC_DETAIL": " 야외 행사 ",
        }
    )

    event = normalize_seoul_event(
        raw,
        area_name=" 광화문·덕수궁 ",
        area_code=" POI001 ",
    )

    assert event.area_name == "광화문·덕수궁"
    assert event.area_code == "POI001"
    assert event.name == "광화문 문화축제"
    assert event.period == "2026-07-21~2026-07-25"
    assert event.start_date == date(2026, 7, 21)
    assert event.end_date == date(2026, 7, 25)
    assert event.place == "광화문광장"
    assert event.longitude == 126.9760053
    assert event.latitude == 37.5726241
    assert event.is_paid is None
    assert event.thumbnail_url == (
        "https://example.com/event.jpg"
    )
    assert event.detail_url == (
        "https://example.com/event"
    )
    assert event.details == "야외 행사"
    assert event.source == "seoul-citydata"


def test_normalize_seoul_event_handles_empty_values() -> None:
    raw = SeoulEventRaw.model_validate(
        {
            "EVENT_NM": "무료 전시",
            "EVENT_PERIOD": "2026-07-21~2026-07-31",
            "EVENT_PLACE": "서울광장",
            "EVENT_X": "",
            "EVENT_Y": " ",
            "PAY_YN": "무료",
            "THUMBNAIL": "",
            "URL": None,
            "EVENT_ETC_DETAIL": " ",
        }
    )

    event = normalize_seoul_event(
        raw,
        area_name="시청·서울광장",
        area_code="POI002",
    )

    assert event.longitude is None
    assert event.latitude is None
    assert event.is_paid is False
    assert event.thumbnail_url is None
    assert event.detail_url is None
    assert event.details is None


def test_parse_event_period() -> None:
    start_date, end_date = parse_event_period(
        "2026-07-21~2026-07-25"
    )

    assert start_date == date(2026, 7, 21)
    assert end_date == date(2026, 7, 25)


def test_parse_event_period_handles_invalid_values() -> None:
    assert parse_event_period("") == (None, None)
    assert parse_event_period("잘못된 기간") == (None, None)
    assert parse_event_period(
        "2026-07-25~2026-07-21"
    ) == (None, None)


def test_filter_active_events() -> None:
    periods = [
        ("진행 중 행사", "2026-07-20~2026-07-25"),
        ("종료된 행사", "2026-07-01~2026-07-10"),
        ("예정 행사", "2026-08-01~2026-08-10"),
        ("기간 오류 행사", "잘못된 기간"),
    ]

    events = [
        normalize_seoul_event(
            SeoulEventRaw.model_validate(
                {
                    "EVENT_NM": name,
                    "EVENT_PERIOD": period,
                    "EVENT_PLACE": "서울광장",
                }
            ),
            area_name="시청·서울광장",
            area_code="POI002",
        )
        for name, period in periods
    ]

    result = filter_active_events(
        events,
        target_date=date(2026, 7, 21),
    )

    assert [event.name for event in result] == [
        "진행 중 행사"
    ]


def test_normalize_active_seoul_events() -> None:
    batch = SeoulEventBatchRaw(
        area_name="광화문·덕수궁",
        area_code="POI001",
        events=[
            SeoulEventRaw.model_validate(
                {
                    "EVENT_NM": "진행 중 행사",
                    "EVENT_PERIOD": (
                        "2026-07-20~2026-07-25"
                    ),
                    "EVENT_PLACE": "광화문광장",
                }
            ),
            SeoulEventRaw.model_validate(
                {
                    "EVENT_NM": "종료된 행사",
                    "EVENT_PERIOD": (
                        "2026-07-01~2026-07-10"
                    ),
                    "EVENT_PLACE": "광화문광장",
                }
            ),
        ],
    )

    result = normalize_active_seoul_events(
        batch,
        target_date=date(2026, 7, 21),
    )

    assert len(result) == 1
    assert result[0].name == "진행 중 행사"
    assert result[0].area_code == "POI001"