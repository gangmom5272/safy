import asyncio
from datetime import date

from app.schemas.seoul_event import (
    SeoulEventBatchRaw,
    SeoulEventRaw,
)
from app.services.seoul_event_pipeline import (
    SeoulEventPipeline,
)


class FakeCollector:
    def fetch(self, area_code: str) -> SeoulEventBatchRaw:
        if area_code == "POI002":
            raise RuntimeError("수집 실패")

        return SeoulEventBatchRaw(
            area_name="광화문·덕수궁",
            area_code=area_code,
            events=[
                SeoulEventRaw.model_validate(
                    {
                        "EVENT_NM": "광화문 문화행사",
                        "EVENT_PERIOD": (
                            "2026-07-01~2026-07-31"
                        ),
                        "EVENT_PLACE": "광화문광장",
                        "EVENT_X": 126.976,
                        "EVENT_Y": 37.572,
                        "PAY_YN": "무료",
                    }
                )
            ],
        )


def test_run_many_returns_partial_success() -> None:
    pipeline = SeoulEventPipeline(FakeCollector())

    result = asyncio.run(
        pipeline.run_many(
            area_codes=["POI001", "POI002"],
            target_date=date(2026, 7, 30),
            max_concurrency=2,
        )
    )

    assert result.status == "PARTIAL_SUCCESS"
    assert result.requested_count == 2
    assert result.success_count == 1
    assert result.failure_count == 1
    assert len(result.items) == 1
    assert result.failures[0].area == "POI002"
