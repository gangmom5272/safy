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
