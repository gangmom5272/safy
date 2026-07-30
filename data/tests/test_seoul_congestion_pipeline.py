import asyncio

from app.schemas.seoul_congestion import (
    SeoulCongestionBatchRaw,
    SeoulCongestionCollectionFailure,
    SeoulCongestionRaw,
)
from app.services.seoul_congestion_pipeline import (
    SeoulCongestionPipeline,
    determine_collection_status,
)


class FakeCollector:
    async def fetch_many(
        self,
        areas: list[str],
        max_concurrency: int,
    ) -> SeoulCongestionBatchRaw:
        assert areas == ["POI009", "POI999"]
        assert max_concurrency == 2

        return SeoulCongestionBatchRaw(
            requested_count=2,
            success_count=1,
            failure_count=1,
            items=[
                SeoulCongestionRaw.model_validate(
                    {
                        "AREA_NM": " 광화문·덕수궁 ",
                        "AREA_CD": " POI009 ",
                        "AREA_CONGEST_LVL": " 약간 붐빔 ",
                        "AREA_CONGEST_MSG": (
                            " 사람이 다소 몰려 있습니다. "
                        ),
                        "AREA_PPLTN_MIN": "22,000",
                        "AREA_PPLTN_MAX": "24000",
                        "REPLACE_YN": "N",
                        "PPLTN_TIME": (
                            "2026-07-30 14:20"
                        ),
                    }
                )
            ],
            failures=[
                SeoulCongestionCollectionFailure(
                    area="POI999",
                    error="해당하는 데이터가 없습니다.",
                )
            ],
        )


class InvalidPopulationCollector:
    async def fetch_many(
        self,
        areas: list[str],
        max_concurrency: int,
    ) -> SeoulCongestionBatchRaw:
        return SeoulCongestionBatchRaw(
            requested_count=1,
            success_count=1,
            failure_count=0,
            items=[
                SeoulCongestionRaw.model_validate(
                    {
                        "AREA_NM": "광화문·덕수궁",
                        "AREA_CD": "POI009",
                        "AREA_CONGEST_LVL": "약간 붐빔",
                        "AREA_CONGEST_MSG": (
                            "사람이 다소 몰려 있습니다."
                        ),
                        "AREA_PPLTN_MIN": "확인 불가",
                        "AREA_PPLTN_MAX": "24000",
                        "REPLACE_YN": "N",
                        "PPLTN_TIME": (
                            "2026-07-30 14:20"
                        ),
                    }
                )
            ],
            failures=[],
        )


def test_determine_collection_status() -> None:
    assert determine_collection_status(
        1,
        0,
    ) == "SUCCESS"

    assert determine_collection_status(
        0,
        1,
    ) == "FAILED"

    assert determine_collection_status(
        1,
        1,
    ) == "PARTIAL_SUCCESS"


def test_pipeline_normalizes_partial_result() -> None:
    pipeline = SeoulCongestionPipeline(
        FakeCollector(),
    )

    result = asyncio.run(
        pipeline.run(
            areas=["POI009", "POI999"],
            max_concurrency=2,
        )
    )

    assert result.status == "PARTIAL_SUCCESS"
    assert result.requested_count == 2
    assert result.success_count == 1
    assert result.failure_count == 1
    assert result.items[0].area_code == "POI009"
    assert result.items[0].population_min == 22000
    assert result.items[0].population_max == 24000
    assert result.items[0].observed_at.isoformat() == (
        "2026-07-30T14:20:00+09:00"
    )
    assert result.failures[0].area == "POI999"


def test_pipeline_handles_normalization_failure() -> None:
    pipeline = SeoulCongestionPipeline(
        InvalidPopulationCollector(),
    )

    result = asyncio.run(
        pipeline.run(
            areas=["POI009"],
            max_concurrency=1,
        )
    )

    assert result.status == "FAILED"
    assert result.requested_count == 1
    assert result.success_count == 0
    assert result.failure_count == 1
    assert result.items == []
    assert result.failures[0].area == "POI009"
    assert "정규화에 실패" in (
        result.failures[0].error
    )
