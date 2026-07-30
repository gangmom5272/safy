from datetime import date

from app.normalizers.seoul_event import (
    normalize_seoul_event,
)
from app.schemas.seoul_event import (
    SeoulEventBatchRaw,
    SeoulEventRaw,
)
from app.services.seoul_event_pipeline import (
    SeoulEventPipeline,
    build_seoul_event_json,
    deduplicate_seoul_events,
)


class FakeCollector:
    def fetch(self, area_name: str) -> SeoulEventBatchRaw:
        assert area_name == "광화문·덕수궁"

        duplicate_event = {
            "EVENT_NM": "광화문 문화축제",
            "EVENT_PERIOD": "2026-07-20~2026-07-25",
            "EVENT_PLACE": "광화문광장",
            "EVENT_X": "126.976005300",
            "EVENT_Y": "37.572624100",
            "PAY_YN": "무료",
            "THUMBNAIL": None,
            "URL": "https://example.com/event",
            "EVENT_ETC_DETAIL": "야외 행사",
        }

        return SeoulEventBatchRaw(
            area_name="광화문·덕수궁",
            area_code="POI009",
            events=[
                SeoulEventRaw.model_validate(
                    duplicate_event
                ),
                SeoulEventRaw.model_validate(
                    duplicate_event
                ),
                SeoulEventRaw.model_validate(
                    {
                        **duplicate_event,
                        "EVENT_NM": "종료된 행사",
                        "EVENT_PERIOD": (
                            "2026-07-01~2026-07-10"
                        ),
                    }
                ),
                SeoulEventRaw.model_validate(
                    {
                        **duplicate_event,
                        "EVENT_NM": "기간 오류 행사",
                        "EVENT_PERIOD": "잘못된 기간",
                    }
                ),
            ],
        )


def test_pipeline_returns_active_unique_event_json() -> None:
    pipeline = SeoulEventPipeline(FakeCollector())

    result = pipeline.run(
        area_name="광화문·덕수궁",
        target_date=date(2026, 7, 21),
    )

    assert len(result) == 1
    assert len(result[0]["external_id"]) == 64
    assert result[0]["area_code"] == "POI009"
    assert result[0]["name"] == "광화문 문화축제"
    assert result[0]["start_date"] == "2026-07-20"
    assert result[0]["end_date"] == "2026-07-25"
    assert result[0]["is_paid"] is False


def test_deduplicate_and_build_json_preserve_order() -> None:
    batch = FakeCollector().fetch("광화문·덕수궁")

    first = normalize_seoul_event(
        batch.events[0],
        batch.area_name,
        batch.area_code,
    )
    duplicate = normalize_seoul_event(
        batch.events[1],
        batch.area_name,
        batch.area_code,
    )

    unique = deduplicate_seoul_events(
        [first, duplicate]
    )
    result = build_seoul_event_json(unique)

    assert len(result) == 1
    assert result[0]["external_id"] == first.external_id
