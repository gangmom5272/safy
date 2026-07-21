from typing import Any
import httpx2
import pytest

import app.collectors.seoul_event as collector_module
from app.collectors.seoul_event import (
    SeoulEventAPIError,
    SeoulEventCollector,
)


class FakeResponse:
    def __init__(self, payload: dict[str, Any]) -> None:
        self._payload = payload

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, Any]:
        return self._payload


class FakeClient:
    def __init__(self, payload: dict[str, Any]) -> None:
        self._payload = payload
        self.request_url = ""

    def __enter__(self) -> "FakeClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(self, url: str) -> FakeResponse:
        self.request_url = url
        return FakeResponse(self._payload)


class TimeoutClient:
    def __enter__(self) -> "TimeoutClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(self, url: str) -> FakeResponse:
        raise httpx2.TimeoutException(
            "요청 시간이 초과되었습니다."
        )



class ConnectionErrorClient:
    def __enter__(self) -> "ConnectionErrorClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(self, url: str) -> FakeResponse:
        raise httpx2.ConnectError(
            "서버에 연결할 수 없습니다."
        )


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


def test_fetch_seoul_events(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "CITYDATA": {
            "AREA_NM": "광화문·덕수궁",
            "AREA_CD": "POI001",
            "EVENT_STTS": [
                {
                    "EVENT_NM": "광화문 문화행사",
                    "EVENT_PERIOD": "2026-07-21~2026-07-25",
                    "EVENT_PLACE": "광화문광장",
                    "EVENT_X": 126.9760053,
                    "EVENT_Y": 37.5726241,
                    "PAY_YN": None,
                    "THUMBNAIL": None,
                    "URL": "https://example.com/event",
                    "EVENT_ETC_DETAIL": None,
                }
            ],
        }
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    result = collector.fetch("광화문·덕수궁")

    assert fake_client.request_url.endswith(
        "/test-api-key/json/citydata/1/5/"
        "%EA%B4%91%ED%99%94%EB%AC%B8%C2%B7%EB%8D%95%EC%88%98%EA%B6%81"
    )
    assert result.area_name == "광화문·덕수궁"
    assert result.area_code == "POI001"
    assert len(result.events) == 1
    assert result.events[0].event_name == "광화문 문화행사"


def test_fetch_returns_empty_events(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "CITYDATA": {
            "AREA_NM": "광화문·덕수궁",
            "AREA_CD": "POI001",
            "EVENT_STTS": [],
        }
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    result = collector.fetch("광화문·덕수궁")

    assert result.area_name == "광화문·덕수궁"
    assert result.area_code == "POI001"
    assert result.events == []


def test_fetch_raises_error_when_api_result_fails(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "RESULT": {
            "CODE": "INFO-100",
            "MESSAGE": "인증키가 유효하지 않습니다.",
        }
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="invalid-api-key",
    )

    with pytest.raises(
        SeoulEventAPIError,
        match="INFO-100",
    ):
        collector.fetch("광화문·덕수궁")


def test_fetch_wraps_timeout_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: TimeoutClient(),
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        SeoulEventAPIError,
        match="시간이 초과",
    ):
        collector.fetch("광화문·덕수궁")


def test_fetch_wraps_http_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: ConnectionErrorClient(),
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        SeoulEventAPIError,
        match="통신에 실패",
    ):
        collector.fetch("광화문·덕수궁")


def test_fetch_wraps_invalid_json_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: InvalidJSONClient(),
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        SeoulEventAPIError,
        match="JSON",
    ):
        collector.fetch("광화문·덕수궁")


def test_fetch_wraps_invalid_event_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "CITYDATA": {
            "AREA_NM": "광화문·덕수궁",
            "AREA_CD": "POI001",
            "EVENT_STTS": [
                {
                    "EVENT_NM": "필수 필드가 누락된 행사",
                }
            ],
        }
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        SeoulEventAPIError,
        match="데이터 형식",
    ):
        collector.fetch("광화문·덕수궁")


def test_fetch_raises_error_when_citydata_is_missing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "unexpected": {
            "message": "예상하지 못한 응답",
        }
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = SeoulEventCollector(
        base_url="http://openapi.seoul.go.kr:8088",
        api_key="test-api-key",
    )

    with pytest.raises(
        SeoulEventAPIError,
        match="CITYDATA",
    ):
        collector.fetch("광화문·덕수궁")