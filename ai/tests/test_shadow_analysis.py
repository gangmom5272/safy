import asyncio
from dataclasses import dataclass, field
from pathlib import Path

from app.services.shadow_analysis import (
    ModelAnalysisSnapshot,
    P2PNetShadowAnalysisService,
    ShadowAnalysisConfig,
)


@dataclass(frozen=True)
class FakeRunnerResult:
    analysis_status: str = "COMPLETED"
    person_count: int | None = 8
    visual_congestion_score: float | None = 4.5
    local_peak_congestion_score: float | None = 60.0
    processing_time_ms: int = 120
    failure_codes: list[str] = field(
        default_factory=list,
    )


def build_config(
    *,
    enabled: bool = True,
    timeout_seconds: float = 1.0,
) -> ShadowAnalysisConfig:
    return ShadowAnalysisConfig(
        enabled=enabled,
        p2pnet_root=Path("p2pnet"),
        weight_path=Path("epoch-003.pth"),
        device="cpu",
        threshold=0.5,
        timeout_seconds=timeout_seconds,
    )


def build_official_snapshot() -> ModelAnalysisSnapshot:
    return ModelAnalysisSnapshot(
        model_name="YOLO",
        model_version="yolo11n",
        model_weight_id="yolo11n.pt",
        analysis_status="COMPLETED",
        person_count=5,
        visual_congestion_score=3.0,
        local_peak_congestion_score=45.0,
        processing_time_ms=12,
        failure_codes=[],
    )


def test_shadow_analysis_is_disabled_by_default(
    monkeypatch,
) -> None:
    monkeypatch.delenv(
        "P2PNET_SHADOW_ENABLED",
        raising=False,
    )

    config = ShadowAnalysisConfig.from_environment()

    assert config.enabled is False


def test_shadow_analysis_records_model_differences() -> None:
    records: list[dict] = []
    factory_calls = 0

    def runner_factory(_):
        nonlocal factory_calls
        factory_calls += 1
        return lambda _: FakeRunnerResult()

    service = P2PNetShadowAnalysisService(
        config=build_config(),
        runner_factory=runner_factory,
        record_sink=records.append,
    )

    first_record = asyncio.run(
        service.analyze_and_log(
            image_bytes=b"frame",
            official=build_official_snapshot(),
            analysis_id="analysis-1",
        )
    )
    asyncio.run(
        service.analyze_and_log(
            image_bytes=b"frame-2",
            official=build_official_snapshot(),
            analysis_id="analysis-2",
        )
    )

    assert first_record is not None
    assert first_record.official.person_count == 5
    assert first_record.shadow.person_count == 8
    assert first_record.differences.person_count == 3
    assert first_record.differences.visual_congestion_score == 1.5
    assert first_record.differences.local_peak_congestion_score == 15.0
    assert records[0]["selectionStatus"] == ("YOLO_PRIMARY_P2PNET_SHADOW")
    assert records[0]["automaticFallback"] == "DISABLED"
    assert "personConfidences" not in records[0]["official"]
    assert factory_calls == 1


def test_shadow_failure_does_not_change_official_result() -> None:
    records: list[dict] = []

    def runner_factory(_):
        raise RuntimeError("sensitive loader error")

    service = P2PNetShadowAnalysisService(
        config=build_config(),
        runner_factory=runner_factory,
        record_sink=records.append,
    )
    official = build_official_snapshot()

    record = asyncio.run(
        service.analyze_and_log(
            image_bytes=b"frame",
            official=official,
        )
    )

    assert record is not None
    assert record.official == official
    assert record.shadow.analysis_status == "FAILED"
    assert record.shadow.failure_codes == [
        "MODEL_INFERENCE_FAILED",
    ]
    assert record.differences.person_count is None
    assert "sensitive loader error" not in str(records[0])


def test_shadow_timeout_is_recorded_as_isolated_failure() -> None:
    records: list[dict] = []

    def slow_runner(_: bytes) -> FakeRunnerResult:
        import time

        time.sleep(0.05)
        return FakeRunnerResult()

    service = P2PNetShadowAnalysisService(
        config=build_config(
            timeout_seconds=0.001,
        ),
        runner_factory=lambda _: slow_runner,
        record_sink=records.append,
    )

    record = asyncio.run(
        service.analyze_and_log(
            image_bytes=b"frame",
            official=build_official_snapshot(),
        )
    )

    assert record is not None
    assert record.shadow.analysis_status == "FAILED"
    assert record.shadow.failure_codes == [
        "MODEL_INFERENCE_FAILED",
    ]
    assert records[0]["official"]["personCount"] == 5


def test_disabled_service_does_not_load_runner() -> None:
    def runner_factory(_):
        raise AssertionError(
            "disabled shadow runner must not load",
        )

    service = P2PNetShadowAnalysisService(
        config=build_config(enabled=False),
        runner_factory=runner_factory,
    )

    record = asyncio.run(
        service.analyze_and_log(
            image_bytes=b"frame",
            official=build_official_snapshot(),
        )
    )

    assert record is None
