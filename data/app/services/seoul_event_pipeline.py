import asyncio
from datetime import date
from typing import Any

from app.collectors.seoul_event import SeoulEventCollector
from app.normalizers.seoul_event import (
    normalize_active_seoul_events,
)
from app.schemas.seoul_event import (
    SeoulEvent,
    SeoulEventBatch,
    SeoulEventCollectionFailure,
)


def deduplicate_seoul_events(
    events: list[SeoulEvent],
) -> list[SeoulEvent]:
    seen_external_ids: set[str] = set()
    unique_events: list[SeoulEvent] = []

    for event in events:
        if event.external_id in seen_external_ids:
            continue

        seen_external_ids.add(event.external_id)
        unique_events.append(event)

    return unique_events


def build_seoul_event_json(
    events: list[SeoulEvent],
) -> list[dict[str, Any]]:
    return [
        event.model_dump(mode="json")
        for event in events
    ]


class SeoulEventPipeline:
    def __init__(
        self,
        collector: SeoulEventCollector,
    ) -> None:
        self._collector = collector

    def run(
        self,
        area_name: str,
        target_date: date | None = None,
    ) -> list[dict[str, Any]]:
        raw_batch = self._collector.fetch(area_name)
        active_events = normalize_active_seoul_events(
            raw_batch,
            target_date=target_date,
        )
        unique_events = deduplicate_seoul_events(
            active_events
        )

        return build_seoul_event_json(unique_events)

    async def run_many(
        self,
        area_codes: list[str],
        target_date: date | None = None,
        max_concurrency: int = 15,
    ) -> SeoulEventBatch:
        if max_concurrency < 1:
            raise ValueError(
                "max_concurrency는 1 이상이어야 합니다."
            )

        semaphore = asyncio.Semaphore(max_concurrency)

        async def collect_area(
            area_code: str,
        ) -> tuple[
            list[SeoulEvent],
            SeoulEventCollectionFailure | None,
        ]:
            async with semaphore:
                try:
                    raw_batch = await asyncio.to_thread(
                        self._collector.fetch,
                        area_code,
                    )
                    events = normalize_active_seoul_events(
                        raw_batch,
                        target_date=target_date,
                    )
                    return events, None
                except Exception as exc:
                    return [], SeoulEventCollectionFailure(
                        area=area_code,
                        error=str(exc),
                    )

        results = await asyncio.gather(
            *(
                collect_area(area_code)
                for area_code in area_codes
            )
        )

        events: list[SeoulEvent] = []
        failures: list[SeoulEventCollectionFailure] = []

        for collected_events, failure in results:
            events.extend(collected_events)

            if failure is not None:
                failures.append(failure)

        unique_events = deduplicate_seoul_events(events)
        success_count = len(area_codes) - len(failures)
        failure_count = len(failures)

        if failure_count == 0:
            status = "SUCCESS"
        elif success_count == 0:
            status = "FAILED"
        else:
            status = "PARTIAL_SUCCESS"

        return SeoulEventBatch(
            status=status,
            requested_count=len(area_codes),
            success_count=success_count,
            failure_count=failure_count,
            items=unique_events,
            failures=failures,
        )
