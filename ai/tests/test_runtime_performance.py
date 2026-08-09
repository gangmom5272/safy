import pytest
import json
from pathlib import Path

from app.services.runtime_performance import (
    benchmark_frame_sequence,
    save_runtime_performance_report,
    summarize_runtime_performance,
)


def test_summarize_runtime_performance() -> None:
    result = summarize_runtime_performance(
        latencies_ms=[
            10.0,
            20.0,
            30.0,
            40.0,
        ],
        statuses=[
            "COMPLETED",
            "COMPLETED",
            "INVALID_FRAME",
            "FAILED",
        ],
        total_duration_ms=100.0,
        peak_gpu_memory_mb=256.5,
    )

    assert result.request_count == 4
    assert result.completed_count == 2
    assert result.invalid_frame_count == 1
    assert result.failed_count == 1
    assert result.total_duration_ms == 100.0
    assert result.mean_latency_ms == 25.0
    assert result.median_latency_ms == 25.0
    assert result.p95_latency_ms == 40.0
    assert result.max_latency_ms == 40.0
    assert result.throughput_fps == 40.0
    assert result.peak_gpu_memory_mb == 256.5


def test_summarize_rejects_empty_measurements() -> None:
    with pytest.raises(
        ValueError,
        match="성능 측정 기록이 한 건 이상 필요합니다",
    ):
        summarize_runtime_performance(
            latencies_ms=[],
            statuses=[],
            total_duration_ms=100.0,
            peak_gpu_memory_mb=0.0,
        )


def test_summarize_rejects_mismatched_measurements() -> None:
    with pytest.raises(
        ValueError,
        match=("지연시간과 분석 상태의 개수가 일치해야 합니다"),
    ):
        summarize_runtime_performance(
            latencies_ms=[
                10.0,
                20.0,
            ],
            statuses=[
                "COMPLETED",
            ],
            total_duration_ms=30.0,
            peak_gpu_memory_mb=0.0,
        )


def test_summarize_rejects_non_positive_duration() -> None:
    with pytest.raises(
        ValueError,
        match="전체 측정시간은 0보다 커야 합니다",
    ):
        summarize_runtime_performance(
            latencies_ms=[
                10.0,
            ],
            statuses=[
                "COMPLETED",
            ],
            total_duration_ms=0.0,
            peak_gpu_memory_mb=0.0,
        )


def test_benchmark_frame_sequence_measures_each_frame() -> None:
    clock_values = iter(
        [
            0.0,
            0.0,
            0.01,
            0.01,
            0.03,
            0.03,
        ]
    )
    analyzed_frames: list[bytes] = []

    def fake_clock() -> float:
        return next(clock_values)

    def fake_analyze_frame(
        image_bytes: bytes,
    ) -> str:
        analyzed_frames.append(image_bytes)

        if image_bytes == b"first":
            return "COMPLETED"

        return "INVALID_FRAME"

    result = benchmark_frame_sequence(
        frame_bytes=[
            b"first",
            b"second",
        ],
        analyze_frame=fake_analyze_frame,
        clock=fake_clock,
        peak_gpu_memory_reader=lambda: 128.0,
    )

    assert analyzed_frames == [
        b"first",
        b"second",
    ]
    assert result.request_count == 2
    assert result.completed_count == 1
    assert result.invalid_frame_count == 1
    assert result.failed_count == 0
    assert result.total_duration_ms == 30.0
    assert result.mean_latency_ms == 15.0
    assert result.median_latency_ms == 15.0
    assert result.p95_latency_ms == 20.0
    assert result.max_latency_ms == 20.0
    assert result.throughput_fps == 66.67
    assert result.peak_gpu_memory_mb == 128.0


def test_benchmark_excludes_warmup_from_summary() -> None:
    clock_values = iter(
        [
            0.0,
            0.0,
            0.01,
            0.01,
        ]
    )
    analyzed_frames: list[bytes] = []

    def fake_clock() -> float:
        return next(clock_values)

    def fake_analyze_frame(
        image_bytes: bytes,
    ) -> str:
        analyzed_frames.append(image_bytes)
        return "COMPLETED"

    result = benchmark_frame_sequence(
        frame_bytes=[
            b"measured",
        ],
        analyze_frame=fake_analyze_frame,
        warmup_frame_bytes=b"warmup",
        warmup_iterations=2,
        clock=fake_clock,
    )

    assert analyzed_frames == [
        b"warmup",
        b"warmup",
        b"measured",
    ]
    assert result.request_count == 1
    assert result.completed_count == 1
    assert result.total_duration_ms == 10.0
    assert result.mean_latency_ms == 10.0
    assert result.throughput_fps == 100.0


def test_save_runtime_performance_report(
    tmp_path: Path,
) -> None:
    single_request = summarize_runtime_performance(
        latencies_ms=[
            120.0,
        ],
        statuses=[
            "COMPLETED",
        ],
        total_duration_ms=120.0,
        peak_gpu_memory_mb=200.0,
    )
    continuous_frames = summarize_runtime_performance(
        latencies_ms=[
            10.0,
            20.0,
            30.0,
        ],
        statuses=[
            "COMPLETED",
            "INVALID_FRAME",
            "FAILED",
        ],
        total_duration_ms=60.0,
        peak_gpu_memory_mb=256.0,
    )
    output_path = tmp_path / "report.json"

    save_runtime_performance_report(
        single_request=single_request,
        continuous_frames=continuous_frames,
        output_path=output_path,
        model_name="YOLO",
        model_version="yolo11n",
        device="cuda",
        warmup_iterations=1,
        python_version="3.11.9",
        torch_version="2.13.0+cu130",
        cuda_runtime="13.0",
        gpu_name=("NVIDIA GeForce RTX 4050 Laptop GPU"),
        gpu_total_memory_mb=6140.56,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert report["model"] == {
        "name": "YOLO",
        "version": "yolo11n",
    }
    assert report["environment"] == {
        "device": "cuda",
        "warmupIterations": 1,
        "pythonVersion": "3.11.9",
        "torchVersion": "2.13.0+cu130",
        "cudaRuntime": "13.0",
        "gpuName": ("NVIDIA GeForce RTX 4050 Laptop GPU"),
        "gpuTotalMemoryMb": 6140.56,
    }
    assert report["singleRequest"]["requestCount"] == 1
    assert report["singleRequest"]["meanLatencyMs"] == 120.0
    assert report["continuousFrames"]["requestCount"] == 3
    assert report["continuousFrames"]["p95LatencyMs"] == 30.0
    assert report["continuousFrames"]["throughputFps"] == 50.0
