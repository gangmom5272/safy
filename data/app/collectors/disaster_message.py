from datetime import date
from typing import Any

import httpx2

from app.schemas.disaster_message import DisasterMessageRaw


class DisasterMessageAPIError(RuntimeError):
    pass


class DisasterMessageCollector:
    def __init__(
        self,
        base_url: str,
        api_key: str,
        timeout_seconds: float = 10.0,
    ) -> None:
        if not base_url:
            raise ValueError("재난문자 API 주소가 필요합니다.")

        if not api_key:
            raise ValueError("재난문자 API 인증키가 필요합니다.")

        self._base_url = base_url
        self._api_key = api_key
        self._timeout_seconds = timeout_seconds

    def fetch(
        self,
        page_no: int = 1,
        num_of_rows: int = 10,
        start_date: date | None = None,
        region: str | None = None,
    ) -> list[DisasterMessageRaw]:
        params: dict[str, str | int] = {
            "serviceKey": self._api_key,
            "returnType": "json",
            "pageNo": page_no,
            "numOfRows": num_of_rows,
        }

        if start_date is not None:
            params["crtDt"] = start_date.strftime("%Y%m%d")

        if region:
            params["rgnNm"] = region

        with httpx2.Client(
            timeout=self._timeout_seconds,
        ) as client:
            response = client.get(
                self._base_url,
                params=params,
            )
            response.raise_for_status()
            payload: dict[str, Any] = response.json()

        header = payload.get("header") or {}
        result_code = header.get("resultCode")

        if result_code != "00":
            result_message = header.get("resultMsg")
            error_message = header.get("errorMsg")

            raise DisasterMessageAPIError(
                f"재난문자 API 오류: "
                f"code={result_code}, "
                f"message={result_message}, "
                f"detail={error_message}"
            )

        body = payload.get("body")

        if not isinstance(body, list):
            raise DisasterMessageAPIError(
                "재난문자 API body가 목록이 아닙니다."
            )

        return [
            DisasterMessageRaw.model_validate(item)
            for item in body
        ]