from typing import Any

import pytest
import asyncio
import app.collectors.seoul_congestion as collector_module
from app.collectors.seoul_congestion import (
    SeoulCongestionAPIError,
    SeoulCongestionCollector,
)


class FakeResponse:
    def __init__(
        self,
        payload: dict[str, Any],
    ) -> None:
        self._payload = payload

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, Any]:
        return self._payload


class FakeClient:
    def __init__(
        self,
        payload: dict[str, Any],
    ) -> None:
        self._payload = payload
        self.request_url = ""

    def __enter__(self) -> "FakeClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(self, url: str) -> FakeResponse:
        self.request_url = url
        return FakeResponse(self._payload)


class FakeAsyncClient:
    def __init__(
        self,
        payloads: dict[str, dict[str, Any]],
    ) -> None:
        self._payloads = payloads
        self.request_urls: list[str] = []

    async def __aenter__(self) -> "FakeAsyncClient":
        return self

    async def __aexit__(
        self,
        *args: object,
    ) -> None:
        return None

    async def get(self, url: str) -> FakeResponse:
        self.request_urls.append(url)
        area = url.rsplit("/", maxsplit=1)[-1]

        return FakeResponse(self._payloads[area])


def test_fetch_seoul_congestion(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "SeoulRtd.citydata_ppltn": [
            {
                "AREA_NM": "광화문·덕수궁",
                "AREA_CD": "POI009",
                "AREA_CONGEST_LVL": "약간 붐빔",
                "AREA_CONGEST_MSG": (
                    "사람이 다소 몰려 있습니다."
                ),
                "AREA_PPLTN_MIN": "22000",
                "AREA_PPLTN_MAX": "24000",
                "REPLACE_YN": "N",
                "PPLTN_TIME": "2026-07-30 14:20",
            }
        ],
        "RESULT": {
            "CODE": "INFO-000",
            "MESSAGE": "정상 처리되었습니다.",
        },
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = SeoulCongestionCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    result = collector.fetch("광화문·덕수궁")

    assert fake_client.request_url.endswith(
        "/test-api-key/json/citydata_ppltn/1/5/"
        "%EA%B4%91%ED%99%94%EB%AC%B8%C2%B7"
        "%EB%8D%95%EC%88%98%EA%B6%81"
    )
    assert result.area_code == "POI009"
    assert result.congestion_level == "약간 붐빔"
    assert result.population_min == "22000"


def test_fetch_many_returns_partial_success(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    success_payload = {
        "SeoulRtd.citydata_ppltn": [
            {
                "AREA_NM": "광화문·덕수궁",
                "AREA_CD": "POI009",
                "AREA_CONGEST_LVL": "약간 붐빔",
                "AREA_CONGEST_MSG": (
                    "사람이 다소 몰려 있습니다."
                ),
                "AREA_PPLTN_MIN": "22000",
                "AREA_PPLTN_MAX": "24000",
                "REPLACE_YN": "N",
                "PPLTN_TIME": "2026-07-30 14:20",
            }
        ],
        "RESULT": {
            "CODE": "INFO-000",
            "MESSAGE": "정상 처리되었습니다.",
        },
    }
    failure_payload = {
        "SeoulRtd.citydata_ppltn": [],
        "RESULT": {
            "CODE": "INFO-200",
            "MESSAGE": "해당하는 데이터가 없습니다.",
        },
    }
    fake_client = FakeAsyncClient(
        {
            "POI009": success_payload,
            "POI999": failure_payload,
        }
    )

    monkeypatch.setattr(
        collector_module.httpx2,
        "AsyncClient",
        lambda timeout: fake_client,
    )

    collector = SeoulCongestionCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    result = asyncio.run(
        collector.fetch_many(
            ["POI009", "POI999"],
            max_concurrency=2,
        )
    )

    assert result.requested_count == 2
    assert result.success_count == 1
    assert result.failure_count == 1
    assert result.items[0].area_code == "POI009"
    assert result.failures[0].area == "POI999"
    assert "INFO-200" in result.failures[0].error


class TimeoutClient:
    def __enter__(self) -> "TimeoutClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(self, url: str) -> FakeResponse:
        raise collector_module.httpx2.TimeoutException(
            "요청 시간이 초과되었습니다."
        )


def test_fetch_wraps_timeout_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: TimeoutClient(),
    )

    collector = SeoulCongestionCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        SeoulCongestionAPIError,
        match="시간이 초과",
    ):
        collector.fetch("POI009")


class InvalidJSONResponse:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, Any]:
        raise ValueError("잘못된 JSON 응답")


class InvalidJSONClient:
    def __enter__(self) -> "InvalidJSONClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(self, url: str) -> InvalidJSONResponse:
        return InvalidJSONResponse()


def test_fetch_wraps_invalid_json_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: InvalidJSONClient(),
    )

    collector = SeoulCongestionCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        SeoulCongestionAPIError,
        match="JSON",
    ):
        collector.fetch("POI009")


def test_fetch_many_rejects_empty_areas() -> None:
    collector = SeoulCongestionCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        ValueError,
        match="장소 코드",
    ):
        asyncio.run(
            collector.fetch_many(["", " "])
        )


def test_fetch_many_rejects_invalid_max_concurrency() -> None:
    collector = SeoulCongestionCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        ValueError,
        match="동시 요청 수",
    ):
        asyncio.run(
            collector.fetch_many(
                ["POI009"],
                max_concurrency=0,
            )
        )
