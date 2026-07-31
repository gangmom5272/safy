from datetime import date
from typing import Any

from app.collectors.disaster_message import (
    DisasterMessageCollector,
)
from app.normalizers.disaster_message import (
    normalize_disaster_message,
)
from app.schemas.disaster_message import DisasterMessage




def deduplicate_disaster_messages(
    messages: list[DisasterMessage],
) -> list[DisasterMessage]:
    seen_external_ids: set[str] = set()
    unique_messages: list[DisasterMessage] = []

    for message in messages:
        if message.external_id in seen_external_ids:
            continue

        seen_external_ids.add(message.external_id)
        unique_messages.append(message)

    return unique_messages


def build_internal_json(
    messages: list[DisasterMessage],
) -> list[dict[str, Any]]:
    return [
        message.model_dump(mode="json")
        for message in messages
    ]


class DisasterMessagePipeline:
    def __init__(
        self,
        collector: DisasterMessageCollector,
    ) -> None:
        self._collector = collector

    def run(
        self,
        page_no: int = 1,
        num_of_rows: int = 1000,
        start_date: date | None = None,
        region: str | None = None,
    ) -> list[dict[str, Any]]:
        raw_messages = self._collector.fetch(
            page_no=page_no,
            num_of_rows=num_of_rows,
            start_date=start_date,
            region=region,
        )

        normalized_messages = [
            normalize_disaster_message(raw)
            for raw in raw_messages
        ]

        unique_messages = deduplicate_disaster_messages(
            normalized_messages
        )

        return build_internal_json(unique_messages)