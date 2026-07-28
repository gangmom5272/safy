import pytest

from app.services.frame_benchmark_adapter import (
    analyze_frame_via_api,
)


class FakeResponse:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return {
            "analysisStatus": "COMPLETED",
        }


class FakeClient:
    def __init__(self) -> None:
        self.url: str | None = None
        self.files: dict[str, object] | None = None

    def post(
        self,
        url: str,
        *,
        files: dict[str, object],
    ) -> FakeResponse:
        self.url = url
        self.files = files
        return FakeResponse()


def test_analyze_frame_via_api_returns_status() -> None:
    client = FakeClient()

    status = analyze_frame_via_api(
        client=client,
        image_bytes=b"image-bytes",
    )

    assert status == "COMPLETED"
    assert client.url == "/api/v1/analyze/frame"
    assert client.files == {
        "image": (
            "benchmark-frame.jpg",
            b"image-bytes",
            "image/jpeg",
        )
    }


def test_analyze_frame_via_api_rejects_unknown_status(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = FakeClient()

    monkeypatch.setattr(
        FakeResponse,
        "json",
        lambda self: {
            "analysisStatus": "UNKNOWN",
        },
    )

    with pytest.raises(
        ValueError,
        match="알 수 없는 프레임 분석 상태입니다",
    ):
        analyze_frame_via_api(
            client=client,
            image_bytes=b"image-bytes",
        )
