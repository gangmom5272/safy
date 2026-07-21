from app.normalizers.seoul_event import (
    normalize_seoul_event,
)
from app.schemas.seoul_event import SeoulEventRaw


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