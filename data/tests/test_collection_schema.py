from app.schemas.collection import (
    SeoulCongestionCollectionRequest,
)


def test_parse_seoul_congestion_collection_request() -> None:
    request = SeoulCongestionCollectionRequest(
        area_codes=[
            " POI001 ",
            "POI009",
        ]
    )

    assert request.area_codes == [
        "POI001",
        "POI009",
    ]
    assert request.max_concurrency == 15


def test_accepts_current_seoul_area_count() -> None:
    request = SeoulCongestionCollectionRequest(
        area_codes=[
            f"POI{index:03d}"
            for index in range(1, 123)
        ]
    )

    assert len(request.area_codes) == 122
