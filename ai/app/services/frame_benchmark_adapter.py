from typing import Protocol, cast

from app.services.runtime_performance import (
    AnalysisStatus,
)


class ApiResponse(Protocol):
    def raise_for_status(self) -> None:
        pass

    def json(self) -> dict[str, object]:
        pass


class FrameApiClient(Protocol):
    def post(
        self,
        url: str,
        *,
        files: dict[str, object],
    ) -> ApiResponse:
        pass


def analyze_frame_via_api(
    image_bytes: bytes,
    *,
    client: FrameApiClient,
) -> AnalysisStatus:
    response = client.post(
        "/api/v1/analyze/frame",
        files={
            "image": (
                "benchmark-frame.jpg",
                image_bytes,
                "image/jpeg",
            )
        },
    )
    response.raise_for_status()

    payload = response.json()
    analysis_status = payload.get("analysisStatus")

    allowed_statuses = {
        "COMPLETED",
        "INVALID_FRAME",
        "FAILED",
    }

    if analysis_status not in allowed_statuses:
        raise ValueError("알 수 없는 프레임 분석 상태입니다")

    return cast(
        AnalysisStatus,
        analysis_status,
    )
