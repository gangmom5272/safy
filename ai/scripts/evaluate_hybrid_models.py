import argparse
from pathlib import Path

import torch

from app.services.hybrid_model_comparison import (
    HybridModelComparisonRecord,
    build_p2pnet_runner,
    build_yolo_runner,
    compare_samples,
    load_hybrid_evaluation_samples,
    save_hybrid_model_comparison_report,
    summarize_hybrid_comparisons,
)


def run_evaluation(
    *,
    manifest_path: Path,
    p2pnet_root: Path,
    p2pnet_weight_path: Path,
    output_path: Path,
    device: str,
    threshold: float,
    source: str | None = None,
    split: str | None = None,
    limit: int | None = None,
) -> list[HybridModelComparisonRecord]:
    samples = load_hybrid_evaluation_samples(
        manifest_path,
        source=source,
        split=split,
    )

    if limit is not None:
        if limit <= 0:
            raise ValueError(
                "평가 표본 제한은 1 이상이어야 합니다.",
            )
        samples = samples[:limit]

    yolo_runner = build_yolo_runner()
    p2pnet_runner = build_p2pnet_runner(
        p2pnet_root=p2pnet_root,
        weight_path=p2pnet_weight_path,
        device=device,
        threshold=threshold,
    )
    records = compare_samples(
        samples=samples,
        yolo_runner=yolo_runner,
        p2pnet_runner=p2pnet_runner,
    )
    save_hybrid_model_comparison_report(
        records=records,
        output_path=output_path,
    )

    return records


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "동일한 평가 표본에서 YOLO와 P2PNet 결과를 비교하고 "
            "선택 전 원시 보고서를 저장합니다."
        ),
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
        "--p2pnet-weight-path",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--device",
        choices=[
            "cpu",
            "cuda",
        ],
        default=("cuda" if torch.cuda.is_available() else "cpu"),
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.5,
    )
    parser.add_argument(
        "--source",
    )
    parser.add_argument(
        "--split",
    )
    parser.add_argument(
        "--limit",
        type=int,
    )
    arguments = parser.parse_args()

    records = run_evaluation(
        manifest_path=arguments.manifest_path,
        p2pnet_root=arguments.p2pnet_root,
        p2pnet_weight_path=(arguments.p2pnet_weight_path),
        output_path=arguments.output,
        device=arguments.device,
        threshold=arguments.threshold,
        source=arguments.source,
        split=arguments.split,
        limit=arguments.limit,
    )
    summaries = summarize_hybrid_comparisons(records)

    print("selectionStatus: UNDECIDED")
    print(f"sampleCount: {len(records)}")
    for model_key, summary in sorted(summaries.items()):
        print(f"model: {model_key}")
        print(f"  completedCount: {summary.completed_count}")
        print(f"  failedCount: {summary.failed_count}")
        print(f"  meanAbsoluteError: {summary.mean_absolute_error}")
        print(f"  rootMeanSquaredError: {summary.root_mean_squared_error}")
        print(f"  meanProcessingTimeMs: {summary.mean_processing_time_ms}")
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
