import asyncio
from datetime import date, datetime, timezone

from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_disaster_message_pipeline,
    get_seoul_congestion_pipeline,
    get_seoul_event_pipeline,
)
from app.api.routes.collections import build_collection_run
from app.core.config import Settings, get_settings
from app.main import app
from app.schemas.collection import CollectionFailure
from app.schemas.seoul_congestion import (
    SeoulCongestion,
    SeoulCongestionBatch,
)
from app.schemas.seoul_event import (
    SeoulEvent,
    SeoulEventBatch,
)


class FakeCongestionPipeline:
    async def run(
        self,
        areas: list[str],
        max_concurrency: int,
    ) -> SeoulCongestionBatch:
        assert areas == ["POI001"]
        assert max_concurrency == 15

        return SeoulCongestionBatch(
            status="SUCCESS",
            requested_count=1,
            success_count=1,
            failure_count=0,
            items=[
                SeoulCongestion(
                    area_name="강남역",
                    area_code="POI001",
                    congestion_level="붐빔",
                    congestion_message="사람이 많습니다.",
                    population_min=42000,
                    population_max=44000,
                    is_replaced=False,
                    observed_at=datetime(
                        2026,
                        7,
                        30,
                        12,
                        0,
                        tzinfo=timezone.utc,
                    ),
                )
            ],
            failures=[],
        )


class FakeEventPipeline:
    async def run_many(
        self,
        area_codes: list[str],
        target_date: date | None,
        max_concurrency: int,
    ) -> SeoulEventBatch:
        assert area_codes == ["POI009"]
        assert target_date == date(2026, 7, 30)
        assert max_concurrency == 15

        return SeoulEventBatch(
            status="SUCCESS",
            requested_count=1,
            success_count=1,
            failure_count=0,
            items=[
                SeoulEvent(
                    external_id="event-id",
                    area_name="광화문·덕수궁",
                    area_code="POI009",
                    name="문화행사",
                    period="2026-07-01~2026-07-31",
                    start_date=date(2026, 7, 1),
                    end_date=date(2026, 7, 31),
                    place="광화문광장",
                    longitude=126.976,
                    latitude=37.572,
                    is_paid=False,
                    thumbnail_url=None,
                    detail_url=None,
                    details=None,
                )
            ],
            failures=[],
        )


class FakeDisasterMessagePipeline:
    def run(
        self,
        page_no: int,
        num_of_rows: int,
        start_date: date | None,
        region: str | None,
    ) -> list[dict[str, object]]:
        assert page_no == 1
        assert num_of_rows == 100
        assert start_date is None
        assert region is None

        return [
            {
                "external_id": "205355",
                "sent_at": datetime(
                    2026,
                    7,
                    30,
                    12,
                    0,
                    tzinfo=timezone.utc,
                ),
                "message": "안전 안내",
                "regions": ["서울특별시"],
                "emergency_level": "안전안내",
                "disaster_type": "기타",
                "source": "safetydata.go.kr",
            }
        ]


class SlowCongestionPipeline:
    async def run(
        self,
        areas: list[str],
        max_concurrency: int,
    ) -> SeoulCongestionBatch:
        await asyncio.sleep(1)
        raise AssertionError("타임아웃 전에 완료되면 안 됩니다.")


def test_collect_seoul_congestions() -> None:
    app.dependency_overrides[
        get_seoul_congestion_pipeline
    ] = lambda: FakeCongestionPipeline()

    try:
        response = TestClient(app).post(
            "/api/v1/collections/seoul-congestions",
            json={"area_codes": ["POI001"]},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    assert payload["collection_run"]["status"] == "SUCCESS"
    assert payload["collection_run"]["success_count"] == 1
    assert payload["items"][0]["area_code"] == "POI001"
    assert payload["items"][0]["latitude"] is None


def test_collect_seoul_events() -> None:
    app.dependency_overrides[
        get_seoul_event_pipeline
    ] = lambda: FakeEventPipeline()

    try:
        response = TestClient(app).post(
            "/api/v1/collections/seoul-events",
            json={
                "area_codes": ["POI009"],
                "target_date": "2026-07-30",
            },
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    assert payload["collection_run"]["status"] == "SUCCESS"
    assert payload["items"][0]["external_id"] == "event-id"
    assert payload["items"][0]["longitude"] == 126.976


def test_collect_disaster_messages() -> None:
    app.dependency_overrides[
        get_disaster_message_pipeline
    ] = lambda: FakeDisasterMessagePipeline()

    try:
        response = TestClient(app).post(
            "/api/v1/collections/disaster-messages",
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    assert payload["collection_run"]["status"] == "SUCCESS"
    assert payload["items"][0]["external_id"] == "205355"
    assert payload["items"][0]["regions"] == ["서울특별시"]


def test_collection_run_keeps_error_details_as_json() -> None:
    collection_run = build_collection_run(
        source="seoul-citydata-ppltn",
        started_at=datetime.now(timezone.utc),
        status="PARTIAL_SUCCESS",
        success_count=1,
        failure_count=1,
        failures=[
            CollectionFailure(
                area="POI077",
                error="요청 시간 초과",
            )
        ],
    )

    assert collection_run.error_details == {
        "failures": [
            {
                "area": "POI077",
                "error": "요청 시간 초과",
            }
        ]
    }


def test_collect_seoul_congestions_handles_timeout() -> None:
    app.dependency_overrides[
        get_seoul_congestion_pipeline
    ] = lambda: SlowCongestionPipeline()
    app.dependency_overrides[get_settings] = lambda: Settings(
        collection_timeout_seconds=0.01
    )

    try:
        response = TestClient(app).post(
            "/api/v1/collections/seoul-congestions",
            json={"area_codes": ["POI001", "POI002"]},
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    assert payload["collection_run"]["status"] == "FAILED"
    assert payload["collection_run"]["failure_count"] == 2
    assert payload["collection_run"]["error_code"] == (
        "COLLECTION_TIMEOUT"
    )
    assert payload["items"] == []
