import json
import math
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from statistics import mean, median
from typing import Literal


AnalysisStatus = Literal[
    "COMPLETED",
    "INVALID_FRAME",
    "FAILED",
]
FrameAnalyzer = Callable[[bytes], AnalysisStatus]
Clock = Callable[[], float]
VoidCallback = Callable[[], None]
MemoryReader = Callable[[], float]


@dataclass(frozen=True)
class RuntimePerformanceSummary:
    request_count: int
    completed_count: int
    invalid_frame_count: int
    failed_count: int
    total_duration_ms: float
    mean_latency_ms: float
    median_latency_ms: float
    p95_latency_ms: float
    max_latency_ms: float
    throughput_fps: float
    peak_gpu_memory_mb: float


def _do_nothing() -> None:
    return None


def _zero_memory() -> float:
    return 0.0


def summarize_runtime_performance(
    *,
    latencies_ms: list[float],
    statuses: list[AnalysisStatus],
    total_duration_ms: float,
    peak_gpu_memory_mb: float,
) -> RuntimePerformanceSummary:
    if not latencies_ms:
        raise ValueError("성능 측정 기록이 한 건 이상 필요합니다")

    if len(latencies_ms) != len(statuses):
        raise ValueError("지연시간과 분석 상태의 개수가 일치해야 합니다")

    if total_duration_ms <= 0:
        raise ValueError("전체 측정시간은 0보다 커야 합니다")

    sorted_latencies = sorted(latencies_ms)
    p95_index = max(
        0,
        math.ceil(len(sorted_latencies) * 0.95) - 1,
    )

    return RuntimePerformanceSummary(
        request_count=len(latencies_ms),
        completed_count=statuses.count(
            "COMPLETED",
        ),
        invalid_frame_count=statuses.count(
            "INVALID_FRAME",
        ),
        failed_count=statuses.count(
            "FAILED",
        ),
        total_duration_ms=round(
            total_duration_ms,
            2,
        ),
        mean_latency_ms=round(
            mean(latencies_ms),
            2,
        ),
        median_latency_ms=round(
            median(latencies_ms),
            2,
        ),
        p95_latency_ms=round(
            sorted_latencies[p95_index],
            2,
        ),
        max_latency_ms=round(
            max(latencies_ms),
            2,
        ),
        throughput_fps=round(
            len(latencies_ms) / (total_duration_ms / 1000),
            2,
        ),
        peak_gpu_memory_mb=round(
            peak_gpu_memory_mb,
            2,
        ),
    )


def benchmark_frame_sequence(
    *,
    frame_bytes: list[bytes],
    analyze_frame: FrameAnalyzer,
    warmup_frame_bytes: bytes | None = None,
    warmup_iterations: int = 0,
    clock: Clock = time.perf_counter,
    synchronize: VoidCallback = _do_nothing,
    reset_peak_gpu_memory: VoidCallback = _do_nothing,
    peak_gpu_memory_reader: MemoryReader = _zero_memory,
) -> RuntimePerformanceSummary:
    if warmup_iterations < 0:
        raise ValueError("워밍업 횟수는 0 이상이어야 합니다")

    if warmup_iterations > 0 and warmup_frame_bytes is None:
        raise ValueError("워밍업 프레임이 필요합니다")

    if warmup_frame_bytes is not None:
        for _ in range(warmup_iterations):
            analyze_frame(warmup_frame_bytes)
            synchronize()

    latencies_ms: list[float] = []
    statuses: list[AnalysisStatus] = []

    reset_peak_gpu_memory()
    synchronize()
    total_started_at = clock()

    for image_bytes in frame_bytes:
        synchronize()
        frame_started_at = clock()

        status = analyze_frame(image_bytes)

        synchronize()
        frame_finished_at = clock()

        latencies_ms.append((frame_finished_at - frame_started_at) * 1000)
        statuses.append(status)

    synchronize()
    total_finished_at = clock()

    return summarize_runtime_performance(
        latencies_ms=latencies_ms,
        statuses=statuses,
        total_duration_ms=(total_finished_at - total_started_at) * 1000,
        peak_gpu_memory_mb=(peak_gpu_memory_reader()),
    )


def _summary_to_dict(
    summary: RuntimePerformanceSummary,
) -> dict[str, int | float]:
    return {
        "requestCount": summary.request_count,
        "completedCount": summary.completed_count,
        "invalidFrameCount": (summary.invalid_frame_count),
        "failedCount": summary.failed_count,
        "totalDurationMs": (summary.total_duration_ms),
        "meanLatencyMs": summary.mean_latency_ms,
        "medianLatencyMs": (summary.median_latency_ms),
        "p95LatencyMs": summary.p95_latency_ms,
        "maxLatencyMs": summary.max_latency_ms,
        "throughputFps": summary.throughput_fps,
        "peakGpuMemoryMb": (summary.peak_gpu_memory_mb),
    }


def save_runtime_performance_report(
    *,
    single_request: RuntimePerformanceSummary,
    continuous_frames: RuntimePerformanceSummary,
    output_path: Path,
    model_name: str,
    model_version: str,
    device: str,
    warmup_iterations: int,
    python_version: str,
    torch_version: str,
    cuda_runtime: str | None,
    gpu_name: str | None,
    gpu_total_memory_mb: float,
) -> None:
    report = {
        "model": {
            "name": model_name,
            "version": model_version,
        },
        "environment": {
            "device": device,
            "warmupIterations": warmup_iterations,
            "pythonVersion": python_version,
            "torchVersion": torch_version,
            "cudaRuntime": cuda_runtime,
            "gpuName": gpu_name,
            "gpuTotalMemoryMb": round(
                gpu_total_memory_mb,
                2,
            ),
        },
        "singleRequest": _summary_to_dict(
            single_request,
        ),
        "continuousFrames": _summary_to_dict(
            continuous_frames,
        ),
    }

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    output_path.write_text(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
