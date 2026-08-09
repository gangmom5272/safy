from pydantic import ValidationError

from app.collectors.seoul_congestion import (
    SeoulCongestionCollector,
)
from app.normalizers.seoul_congestion import (
    normalize_seoul_congestion,
)
from app.schemas.seoul_congestion import (
    SeoulCongestionBatch,
    SeoulCongestionBatchRaw,
    SeoulCongestionCollectionFailure,
)


def determine_collection_status(
    success_count: int,
    failure_count: int,
) -> str:
    if failure_count == 0:
        return "SUCCESS"

    if success_count == 0:
        return "FAILED"

    return "PARTIAL_SUCCESS"


class SeoulCongestionPipeline:
    def __init__(
        self,
        collector: SeoulCongestionCollector,
    ) -> None:
        self._collector = collector

    async def run(
        self,
        areas: list[str],
        max_concurrency: int = 10,
    ) -> SeoulCongestionBatch:
        raw_batch = await self._collector.fetch_many(
            areas=areas,
            max_concurrency=max_concurrency,
        )

        normalized_items = []
        failures = list(raw_batch.failures)

        for raw in raw_batch.items:
            try:
                normalized_items.append(
                    normalize_seoul_congestion(raw)
                )
            except (ValueError, ValidationError) as exc:
                failures.append(
                    SeoulCongestionCollectionFailure(
                        area=raw.area_code.strip(),
                        error=(
                            "혼잡도 정규화에 실패했습니다: "
                            f"{exc}"
                        ),
                    )
                )

        success_count = len(normalized_items)
        failure_count = len(failures)

        return SeoulCongestionBatch(
            status=determine_collection_status(
                success_count,
                failure_count,
            ),
            requested_count=raw_batch.requested_count,
            success_count=success_count,
            failure_count=failure_count,
            items=normalized_items,
            failures=failures,
        )
