from datetime import date
from typing import Any

from app.collectors.seoul_event import SeoulEventCollector
from app.normalizers.seoul_event import (
    normalize_active_seoul_events,
)
from app.schemas.seoul_event import SeoulEvent


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
