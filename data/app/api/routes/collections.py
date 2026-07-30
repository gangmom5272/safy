import asyncio
from collections.abc import Awaitable
from datetime import datetime, timezone
from typing import TypeVar
from uuid import uuid4

from fastapi import APIRouter, Depends

from app.api.dependencies import (
    get_disaster_message_pipeline,
    get_seoul_congestion_pipeline,
    get_seoul_event_pipeline,
)
from app.core.config import Settings, get_settings
from app.schemas.collection import (
    CollectionFailure,
    CollectionRun,
    DisasterMessageCollectionRequest,
    DisasterMessageCollectionResponse,
    SeoulCongestionCollectionRequest,
    SeoulCongestionCollectionResponse,
    SeoulEventCollectionRequest,
    SeoulEventCollectionResponse,
)
from app.schemas.disaster_message import DisasterMessage
from app.services.disaster_message_pipeline import (
    DisasterMessagePipeline,
)
from app.services.seoul_congestion_pipeline import (
    SeoulCongestionPipeline,
)
from app.services.seoul_event_pipeline import (
    SeoulEventPipeline,
)


router = APIRouter(
    prefix="/api/v1/collections",
    tags=["collections"],
)

T = TypeVar("T")


async def run_with_timeout(
    operation: Awaitable[T],
    timeout_seconds: float,
) -> T:
    return await asyncio.wait_for(
        operation,
        timeout=timeout_seconds,
    )


def build_collection_run(
    *,
    source: str,
    started_at: datetime,
    status: str,
    success_count: int,
    failure_count: int,
    failures: list[CollectionFailure] | None = None,
    error_code: str | None = None,
    error_message: str | None = None,
) -> CollectionRun:
    error_details: dict[str, object] | None = None

    if failures:
        error_details = {
            "failures": [
                failure.model_dump()
                for failure in failures
            ]
        }

    return CollectionRun(
        external_run_id=f"{source}:{uuid4()}",
        source=source,
        started_at=started_at,
        finished_at=datetime.now(timezone.utc),
        status=status,
        success_count=success_count,
        failure_count=failure_count,
        error_code=error_code,
        error_message=error_message,
        error_details=error_details,
    )


@router.post(
    "/seoul-congestions",
    response_model=SeoulCongestionCollectionResponse,
)
async def collect_seoul_congestions(
    request: SeoulCongestionCollectionRequest,
    pipeline: SeoulCongestionPipeline = Depends(
        get_seoul_congestion_pipeline
    ),
    settings: Settings = Depends(get_settings),
) -> SeoulCongestionCollectionResponse:
    started_at = datetime.now(timezone.utc)
    source = "seoul-citydata-ppltn"

    try:
        batch = await run_with_timeout(
            pipeline.run(
                areas=request.area_codes,
                max_concurrency=request.max_concurrency,
            ),
            settings.collection_timeout_seconds,
        )
        failures = [
            CollectionFailure(
                area=failure.area,
                error=failure.error,
            )
            for failure in batch.failures
        ]
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status=batch.status,
            success_count=batch.success_count,
            failure_count=batch.failure_count,
            failures=failures,
        )
        return SeoulCongestionCollectionResponse(
            collection_run=collection_run,
            items=batch.items,
            failures=failures,
        )
    except TimeoutError:
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status="FAILED",
            success_count=0,
            failure_count=len(request.area_codes),
            error_code="COLLECTION_TIMEOUT",
            error_message="혼잡도 수집 제한 시간을 초과했습니다.",
        )
        return SeoulCongestionCollectionResponse(
            collection_run=collection_run,
            items=[],
        )
    except Exception as exc:
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status="FAILED",
            success_count=0,
            failure_count=len(request.area_codes),
            error_code="COLLECTION_FAILED",
            error_message=str(exc),
        )
        return SeoulCongestionCollectionResponse(
            collection_run=collection_run,
            items=[],
        )


@router.post(
    "/seoul-events",
    response_model=SeoulEventCollectionResponse,
)
async def collect_seoul_events(
    request: SeoulEventCollectionRequest,
    pipeline: SeoulEventPipeline = Depends(
        get_seoul_event_pipeline
    ),
    settings: Settings = Depends(get_settings),
) -> SeoulEventCollectionResponse:
    started_at = datetime.now(timezone.utc)
    source = "seoul-citydata"

    try:
        batch = await run_with_timeout(
            pipeline.run_many(
                area_codes=request.area_codes,
                target_date=request.target_date,
                max_concurrency=request.max_concurrency,
            ),
            settings.collection_timeout_seconds,
        )
        failures = [
            CollectionFailure(
                area=failure.area,
                error=failure.error,
            )
            for failure in batch.failures
        ]
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status=batch.status,
            success_count=batch.success_count,
            failure_count=batch.failure_count,
            failures=failures,
        )
        return SeoulEventCollectionResponse(
            collection_run=collection_run,
            items=batch.items,
            failures=failures,
        )
    except TimeoutError:
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status="FAILED",
            success_count=0,
            failure_count=len(request.area_codes),
            error_code="COLLECTION_TIMEOUT",
            error_message="행사 수집 제한 시간을 초과했습니다.",
        )
        return SeoulEventCollectionResponse(
            collection_run=collection_run,
            items=[],
        )
    except Exception as exc:
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status="FAILED",
            success_count=0,
            failure_count=len(request.area_codes),
            error_code="COLLECTION_FAILED",
            error_message=str(exc),
        )
        return SeoulEventCollectionResponse(
            collection_run=collection_run,
            items=[],
        )


@router.post(
    "/disaster-messages",
    response_model=DisasterMessageCollectionResponse,
)
async def collect_disaster_messages(
    request: DisasterMessageCollectionRequest | None = None,
    pipeline: DisasterMessagePipeline = Depends(
        get_disaster_message_pipeline
    ),
    settings: Settings = Depends(get_settings),
) -> DisasterMessageCollectionResponse:
    request = request or DisasterMessageCollectionRequest()
    started_at = datetime.now(timezone.utc)
    source = "safetydata.go.kr"

    try:
        raw_items = await run_with_timeout(
            asyncio.to_thread(
                pipeline.run,
                page_no=request.page_no,
                num_of_rows=request.num_of_rows,
                start_date=request.start_date,
                region=request.region,
            ),
            settings.disaster_collection_timeout_seconds,
        )
        items = [
            DisasterMessage.model_validate(item)
            for item in raw_items
        ]
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status="SUCCESS",
            success_count=len(items),
            failure_count=0,
        )
        return DisasterMessageCollectionResponse(
            collection_run=collection_run,
            items=items,
        )
    except TimeoutError:
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status="FAILED",
            success_count=0,
            failure_count=1,
            error_code="COLLECTION_TIMEOUT",
            error_message="재난문자 수집 제한 시간을 초과했습니다.",
        )
        return DisasterMessageCollectionResponse(
            collection_run=collection_run,
            items=[],
        )
    except Exception as exc:
        collection_run = build_collection_run(
            source=source,
            started_at=started_at,
            status="FAILED",
            success_count=0,
            failure_count=1,
            error_code="COLLECTION_FAILED",
            error_message=str(exc),
        )
        return DisasterMessageCollectionResponse(
            collection_run=collection_run,
            items=[],
        )
