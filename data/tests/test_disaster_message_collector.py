from datetime import date
from typing import Any
import httpx2
import pytest

import app.collectors.disaster_message as collector_module
from app.collectors.disaster_message import (
    DisasterMessageAPIError,
    DisasterMessageCollector,
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
        self.request_params: dict[str, str | int] = {}

    def __enter__(self) -> "FakeClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(
        self,
        url: str,
        params: dict[str, str | int],
    ) -> FakeResponse:
        self.request_url = url
        self.request_params = params
        return FakeResponse(self._payload)


def test_fetch_disaster_messages_builds_request(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "header": {
            "resultCode": "00",
            "resultMsg": "NORMAL SERVICE",
            "errorMsg": None,
        },
        "body": [
            {
                "SN": "205355",
                "CRT_DT": "2023/09/19 12:22:17",
                "MSG_CN": "테스트 메시지",
                "RCPTN_RGN_NM": "경기도 김포시 ",
                "EMRG_STEP_NM": "안전안내",
                "DST_SE_NM": "기타",
                "REG_YMD": "2023-09-19",
                "MDFCN_YMD": "2023-09-19",
            }
        ],
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = DisasterMessageCollector(
        base_url="https://example.com/disaster-messages",
        api_key="test-api-key",
    )

    messages = collector.fetch(
        page_no=2,
        num_of_rows=20,
        start_date=date(2026, 7, 20),
        region="서울특별시",
    )

    assert fake_client.request_params == {
        "serviceKey": "test-api-key",
        "returnType": "json",
        "pageNo": 2,
        "numOfRows": 20,
        "crtDt": "20260720",
        "rgnNm": "서울특별시",
    }
    assert len(messages) == 1
    assert messages[0].serial_number == "205355"


def test_fetch_raises_error_when_api_result_fails(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "header": {
            "resultCode": "32",
            "resultMsg": "UNREGISTERED IP ERROR",
            "errorMsg": "등록되지 않은 IP",
        },
        "body": None,
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = DisasterMessageCollector(
        base_url="https://example.com/disaster-messages",
        api_key="test-api-key",
    )

    with pytest.raises(
        DisasterMessageAPIError,
        match="code=32",
    ):
        collector.fetch()


class TimeoutClient:
    def __enter__(self) -> "TimeoutClient":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def get(
        self,
        url: str,
        params: dict[str, str | int],
    ) -> FakeResponse:
        raise httpx2.TimeoutException(
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

    collector = DisasterMessageCollector(
        base_url="https://example.com/disaster-messages",
        api_key="test-api-key",
    )

    with pytest.raises(
        DisasterMessageAPIError,
        match="시간이 초과",
    ):
        collector.fetch()

class ConnectionErrorClient(TimeoutClient):
    def get(
        self,
        url: str,
        params: dict[str, str | int],
    ) -> FakeResponse:
        raise httpx2.ConnectError(
            "서버에 연결할 수 없습니다."
        )


def test_fetch_wraps_http_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: ConnectionErrorClient(),
    )

    collector = DisasterMessageCollector(
        base_url="https://example.com/disaster-messages",
        api_key="test-api-key",
    )

    with pytest.raises(
        DisasterMessageAPIError,
        match="통신에 실패",
    ):
        collector.fetch()


class InvalidJSONResponse(FakeResponse):
    def json(self) -> dict[str, Any]:
        raise ValueError("잘못된 JSON 응답")


class InvalidJSONClient(TimeoutClient):
    def get(
        self,
        url: str,
        params: dict[str, str | int],
    ) -> InvalidJSONResponse:
        return InvalidJSONResponse({})


def test_fetch_wraps_invalid_json_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: InvalidJSONClient(),
    )

    collector = DisasterMessageCollector(
        base_url="https://example.com/disaster-messages",
        api_key="test-api-key",
    )

    with pytest.raises(
        DisasterMessageAPIError,
        match="JSON",
    ):
        collector.fetch()

def test_fetch_raises_error_when_body_is_not_list(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "header": {
            "resultCode": "00",
            "resultMsg": "NORMAL SERVICE",
            "errorMsg": None,
        },
        "body": {},
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = DisasterMessageCollector(
        base_url="https://example.com/disaster-messages",
        api_key="test-api-key",
    )

    with pytest.raises(
        DisasterMessageAPIError,
        match="body가 목록이 아닙니다",
    ):
        collector.fetch()

def test_fetch_wraps_invalid_item_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "header": {
            "resultCode": "00",
            "resultMsg": "NORMAL SERVICE",
            "errorMsg": None,
        },
        "body": [
            {
                "SN": "205355",
                "MSG_CN": "필수 필드가 누락된 데이터",
            }
        ],
    }
    fake_client = FakeClient(payload)

    monkeypatch.setattr(
        collector_module.httpx2,
        "Client",
        lambda timeout: fake_client,
    )

    collector = DisasterMessageCollector(
        base_url="https://example.com/disaster-messages",
        api_key="test-api-key",
    )

    with pytest.raises(
        DisasterMessageAPIError,
        match="데이터 형식",
    ):
        collector.fetch()