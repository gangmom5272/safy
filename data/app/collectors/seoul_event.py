from typing import Any
from urllib.parse import quote

import httpx2
from pydantic import ValidationError

from app.schemas.seoul_event import (
    SeoulEventBatchRaw,
    SeoulEventRaw,
)


class SeoulEventAPIError(RuntimeError):
    pass


class SeoulEventCollector:
    def __init__(
        self,
        base_url: str,
        api_key: str,
        timeout_seconds: float = 10.0,
    ) -> None:
        if not base_url:
            raise ValueError(
                "서울 실시간 도시데이터 API 주소가 필요합니다."
            )

        if not api_key:
            raise ValueError(
                "서울 실시간 도시데이터 API 인증키가 필요합니다."
            )

        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._timeout_seconds = timeout_seconds

    def fetch(
        self,
        area_name: str,
        start_index: int = 1,
        end_index: int = 5,
    ) -> SeoulEventBatchRaw:
        encoded_area_name = quote(area_name, safe="")
        request_url = (
            f"{self._base_url}/{self._api_key}"
            f"/json/citydata/{start_index}/{end_index}"
            f"/{encoded_area_name}"
        )

        try:
            with httpx2.Client(
                timeout=self._timeout_seconds,
            ) as client:
                response = client.get(request_url)
                response.raise_for_status()
                payload: dict[str, Any] = response.json()
        except httpx2.TimeoutException as exc:
            raise SeoulEventAPIError(
                "서울 실시간 도시데이터 API 요청 시간이 초과되었습니다."
            ) from exc
        except httpx2.HTTPError as exc:
            raise SeoulEventAPIError(
                "서울 실시간 도시데이터 API 통신에 실패했습니다."
            ) from exc
        except ValueError as exc:
            raise SeoulEventAPIError(
                "서울 실시간 도시데이터 API 응답을 "
                "JSON으로 해석할 수 없습니다."
            ) from exc

        result = payload.get("RESULT")

        if isinstance(result, dict):
            result_code = result.get("CODE")
            result_message = result.get("MESSAGE")

            if result_code != "INFO-000":
                raise SeoulEventAPIError(
                    f"서울 실시간 도시데이터 API 오류: "
                    f"code={result_code}, "
                    f"message={result_message}"
                )

        citydata = payload.get("CITYDATA")

        if not isinstance(citydata, dict):
            raise SeoulEventAPIError(
                "서울 실시간 도시데이터 API 응답에 "
                "CITYDATA가 없습니다."
            )

        event_items = citydata.get("EVENT_STTS") or []

        if not isinstance(event_items, list):
            raise SeoulEventAPIError(
                "서울 실시간 도시데이터 API EVENT_STTS가 "
                "목록이 아닙니다."
            )

        try:
            events = [
                SeoulEventRaw.model_validate(item)
                for item in event_items
            ]

            return SeoulEventBatchRaw(
                area_name=citydata.get("AREA_NM", ""),
                area_code=citydata.get("AREA_CD", ""),
                events=events,
            )
        except ValidationError as exc:
            raise SeoulEventAPIError(
                "서울 실시간 도시데이터 API 데이터 형식이 "
                "올바르지 않습니다."
            ) from exc