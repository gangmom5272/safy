import asyncio
from typing import Any
from urllib.parse import quote

import httpx2
from pydantic import ValidationError

from app.schemas.seoul_congestion import (
    SeoulCongestionBatchRaw,
    SeoulCongestionCollectionFailure,
    SeoulCongestionRaw,
)


class SeoulCongestionAPIError(RuntimeError):
    pass


class SeoulCongestionCollector:
    def __init__(
        self,
        base_url: str,
        api_key: str,
        timeout_seconds: float = 10.0,
    ) -> None:
        if not base_url:
            raise ValueError(
                "서울 실시간 인구 API 주소가 필요합니다."
            )

        if not api_key:
            raise ValueError(
                "서울 실시간 인구 API 인증키가 필요합니다."
            )

        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._timeout_seconds = timeout_seconds

    def fetch(
        self,
        area: str,
        start_index: int = 1,
        end_index: int = 5,
    ) -> SeoulCongestionRaw:
        request_url = self._build_request_url(
            area,
            start_index,
            end_index,
        )

        try:
            with httpx2.Client(
                timeout=self._timeout_seconds,
            ) as client:
                response = client.get(request_url)
                response.raise_for_status()
                payload: dict[str, Any] = response.json()
        except httpx2.TimeoutException as exc:
            raise SeoulCongestionAPIError(
                "서울 실시간 인구 API 요청 시간이 "
                "초과되었습니다."
            ) from exc
        except httpx2.HTTPError as exc:
            raise SeoulCongestionAPIError(
                "서울 실시간 인구 API 통신에 실패했습니다."
            ) from exc
        except ValueError as exc:
            raise SeoulCongestionAPIError(
                "서울 실시간 인구 API 응답을 "
                "JSON으로 해석할 수 없습니다."
            ) from exc

        return self._parse_payload(payload)

    async def fetch_many(
        self,
        areas: list[str],
        max_concurrency: int = 5,
    ) -> SeoulCongestionBatchRaw:
        normalized_areas = [
            area.strip()
            for area in areas
            if area.strip()
        ]

        if not normalized_areas:
            raise ValueError(
                "수집할 장소 코드가 필요합니다."
            )

        if max_concurrency < 1:
            raise ValueError(
                "최대 동시 요청 수는 1 이상이어야 합니다."
            )

        semaphore = asyncio.Semaphore(max_concurrency)
        async with httpx2.AsyncClient(
            timeout=self._timeout_seconds,
        ) as client:
            results = await asyncio.gather(
                *(
                    self._fetch_one_async(
                        client,
                        semaphore,
                        area,
                    )
                    for area in normalized_areas
                )
            )

        items: list[SeoulCongestionRaw] = []
        failures: list[
            SeoulCongestionCollectionFailure
        ] = []

        for item, failure in results:
            if item is not None:
                items.append(item)

            if failure is not None:
                failures.append(failure)

        return SeoulCongestionBatchRaw(
            requested_count=len(normalized_areas),
            success_count=len(items),
            failure_count=len(failures),
            items=items,
            failures=failures,
        )

    async def _fetch_one_async(
        self,
        client: httpx2.AsyncClient,
        semaphore: asyncio.Semaphore,
        area: str,
    ) -> tuple[
        SeoulCongestionRaw | None,
        SeoulCongestionCollectionFailure | None,
    ]:
        request_url = self._build_request_url(area)

        try:
            async with semaphore:
                response = await client.get(request_url)

            response.raise_for_status()
            payload: dict[str, Any] = response.json()
            item = self._parse_payload(payload)

            return item, None
        except httpx2.TimeoutException:
            error = "요청 시간이 초과되었습니다."
        except httpx2.HTTPError:
            error = "API 통신에 실패했습니다."
        except ValueError:
            error = "JSON 응답을 해석할 수 없습니다."
        except SeoulCongestionAPIError as exc:
            error = str(exc)

        return None, SeoulCongestionCollectionFailure(
            area=area,
            error=error,
        )

    def _build_request_url(
        self,
        area: str,
        start_index: int = 1,
        end_index: int = 5,
    ) -> str:
        encoded_area = quote(area.strip(), safe="")

        return (
            f"{self._base_url}/{self._api_key}"
            f"/json/citydata_ppltn"
            f"/{start_index}/{end_index}/{encoded_area}"
        )

    def _parse_payload(
        self,
        payload: dict[str, Any],
    ) -> SeoulCongestionRaw:
        result = payload.get("RESULT") or {}
        result_code = result.get(
            "RESULT.CODE",
            result.get("CODE"),
        )

        if result_code != "INFO-000":
            result_message = result.get(
                "RESULT.MESSAGE",
                result.get("MESSAGE"),
            )

            raise SeoulCongestionAPIError(
                f"서울 실시간 인구 API 오류: "
                f"code={result_code}, "
                f"message={result_message}"
            )

        service_data = payload.get(
            "SeoulRtd.citydata_ppltn"
        )

        if not isinstance(service_data, list):
            raise SeoulCongestionAPIError(
                "서울 실시간 인구 API 응답 구조가 "
                "올바르지 않습니다."
            )

        if not service_data:
            raise SeoulCongestionAPIError(
                "서울 실시간 인구 API 데이터가 없습니다."
            )

        try:
            return SeoulCongestionRaw.model_validate(
                service_data[0]
            )
        except ValidationError as exc:
            raise SeoulCongestionAPIError(
                "서울 실시간 인구 API 데이터 형식이 "
                "올바르지 않습니다."
            ) from exc
