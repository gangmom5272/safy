import argparse
import platform
from functools import partial
from pathlib import Path

import torch
from fastapi.testclient import TestClient

from app.main import app
from app.services.frame_benchmark_adapter import (
    FrameApiClient,
    analyze_frame_via_api,
)
from app.services.model import (
    MODEL_NAME,
    MODEL_VERSION,
)
from app.services.model_evaluation import (
    load_evaluation_manifest,
)
from app.services.runtime_performance import (
    RuntimePerformanceSummary,
    benchmark_frame_sequence,
    save_runtime_performance_report,
)


def _synchronize_cuda() -> None:
    if torch.cuda.is_available():
        torch.cuda.synchronize()


def _reset_peak_gpu_memory() -> None:
    if torch.cuda.is_available():
        torch.cuda.reset_peak_memory_stats()


def _get_peak_gpu_memory_mb() -> float:
    if not torch.cuda.is_available():
        return 0.0

    return torch.cuda.max_memory_allocated() / 1024 / 1024


def run_benchmark(
    *,
    manifest_path: Path,
    output_path: Path,
    client: FrameApiClient,
    limit: int | None,
    warmup_iterations: int,
    repetitions_per_frame: int,
    device: str,
) -> tuple[
    RuntimePerformanceSummary,
    RuntimePerformanceSummary,
]:
    samples = load_evaluation_manifest(
        manifest_path,
    )

    if limit is not None:
        samples = samples[:limit]

    if not samples:
        raise ValueError("성능 측정 표본이 한 건 이상 필요합니다")

    if repetitions_per_frame < 1:
        raise ValueError("프레임 반복 횟수는 1 이상이어야 합니다")

    frame_bytes = [sample.image_path.read_bytes() for sample in samples]
    first_frame_bytes = frame_bytes[0]

    analyze_frame = partial(
        analyze_frame_via_api,
        client=client,
    )

    single_request = benchmark_frame_sequence(
        frame_bytes=[
            first_frame_bytes,
        ],
        analyze_frame=analyze_frame,
        synchronize=_synchronize_cuda,
        reset_peak_gpu_memory=(_reset_peak_gpu_memory),
        peak_gpu_memory_reader=(_get_peak_gpu_memory_mb),
    )

    continuous_frame_bytes = [
        image_bytes for _ in range(repetitions_per_frame) for image_bytes in frame_bytes
    ]

    continuous_frames = benchmark_frame_sequence(
        frame_bytes=continuous_frame_bytes,
        analyze_frame=analyze_frame,
        warmup_frame_bytes=first_frame_bytes,
        warmup_iterations=warmup_iterations,
        synchronize=_synchronize_cuda,
        reset_peak_gpu_memory=(_reset_peak_gpu_memory),
        peak_gpu_memory_reader=(_get_peak_gpu_memory_mb),
    )

    if device == "cuda" and torch.cuda.is_available():
        gpu_name = torch.cuda.get_device_name(0)
        gpu_total_memory_mb = (
            torch.cuda.get_device_properties(0).total_memory / 1024 / 1024
        )
    else:
        gpu_name = None
        gpu_total_memory_mb = 0.0

    save_runtime_performance_report(
        single_request=single_request,
        continuous_frames=continuous_frames,
        output_path=output_path,
        model_name=MODEL_NAME,
        model_version=MODEL_VERSION,
        device=device,
        warmup_iterations=warmup_iterations,
        python_version=platform.python_version(),
        torch_version=str(torch.__version__),
        cuda_runtime=torch.version.cuda,
        gpu_name=gpu_name,
        gpu_total_memory_mb=(gpu_total_memory_mb),
    )

    return (
        single_request,
        continuous_frames,
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("프레임 분석 API의 단일 요청 및 연속 처리 성능을 측정합니다.")
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--limit",
        type=int,
    )
    parser.add_argument(
        "--warmup",
        type=int,
        default=1,
    )
    parser.add_argument(
        "--repetitions",
        type=int,
        default=3,
    )

    args = parser.parse_args()
    device = "cuda" if torch.cuda.is_available() else "cpu"

    with TestClient(app) as client:
        (
            single_request,
            continuous_frames,
        ) = run_benchmark(
            manifest_path=args.manifest_path,
            output_path=args.output,
            client=client,
            limit=args.limit,
            warmup_iterations=args.warmup,
            repetitions_per_frame=(args.repetitions),
            device=device,
        )

    print("device:", device)
    print(
        "singleRequestLatencyMs:",
        single_request.mean_latency_ms,
    )
    print(
        "continuousRequestCount:",
        continuous_frames.request_count,
    )
    print(
        "meanLatencyMs:",
        continuous_frames.mean_latency_ms,
    )
    print(
        "p95LatencyMs:",
        continuous_frames.p95_latency_ms,
    )
    print(
        "throughputFps:",
        continuous_frames.throughput_fps,
    )
    print(
        "peakGpuMemoryMb:",
        continuous_frames.peak_gpu_memory_mb,
    )
    print("output:", args.output)


if __name__ == "__main__":
    main()
