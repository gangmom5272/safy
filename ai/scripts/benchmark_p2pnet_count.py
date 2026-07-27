import argparse
from pathlib import Path

import torch

from app.services.model_benchmark import (
    benchmark_count_model,
)
from app.services.model_comparison import (
    ModelComparisonRecord,
    ModelRuntimeProfile,
    save_model_comparison_report,
    summarize_by_model,
)
from app.services.model_evaluation import (
    load_evaluation_manifest,
)
from app.services.p2pnet_benchmark_adapter import (
    load_p2pnet_predictor,
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
    p2pnet_root: Path,
    weight_path: Path,
    limit: int | None,
    warmup_iterations: int,
    repetitions_per_sample: int,
) -> tuple[
    list[ModelComparisonRecord],
    ModelRuntimeProfile,
]:
    samples = load_evaluation_manifest(manifest_path)

    if limit is not None:
        samples = samples[:limit]

    device = "cuda" if torch.cuda.is_available() else "cpu"

    def load_predictor():
        return load_p2pnet_predictor(
            p2pnet_root=p2pnet_root,
            weight_path=weight_path,
            device=device,
            threshold=0.5,
        )

    records, runtime_profile = benchmark_count_model(
        samples=samples,
        model_name="P2PNet",
        model_version="SHTechA",
        load_predictor=load_predictor,
        device=device,
        warmup_iterations=(warmup_iterations),
        repetitions_per_sample=(repetitions_per_sample),
        synchronize=_synchronize_cuda,
        reset_peak_memory=(_reset_peak_gpu_memory),
        get_peak_memory_mb=(_get_peak_gpu_memory_mb),
    )

    save_model_comparison_report(
        records,
        output_path,
        runtime_profiles=[runtime_profile],
    )

    return records, runtime_profile


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("P2PNet 사람 계수 후보 모델을 반복 측정합니다.")
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
    )
    parser.add_argument(
        "--p2pnet-root",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--weight-path",
        type=Path,
        required=True,
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

    records, runtime_profile = run_benchmark(
        manifest_path=args.manifest_path,
        output_path=args.output,
        p2pnet_root=args.p2pnet_root,
        weight_path=args.weight_path,
        limit=args.limit,
        warmup_iterations=args.warmup,
        repetitions_per_sample=(args.repetitions),
    )

    summary = summarize_by_model(records)["P2PNet:SHTechA"]

    print(
        "sampleCount:",
        summary.sample_count,
    )
    print(
        "meanAbsoluteError:",
        summary.mean_absolute_error,
    )
    print(
        "initializationTimeMs:",
        runtime_profile.initialization_time_ms,
    )
    print(
        "meanInferenceTimeMs:",
        summary.mean_inference_time_ms,
    )
    print(
        "peakGpuMemoryMb:",
        summary.peak_gpu_memory_mb,
    )
    print("output:", args.output)


if __name__ == "__main__":
    main()
