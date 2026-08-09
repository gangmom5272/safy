from argparse import ArgumentParser
from pathlib import Path

import torch

from app.services.model_evaluation import (
    EvaluationSummary,
    save_evaluation_report,
    summarize_evaluations,
)
from app.services.p2pnet_benchmark_adapter import (
    load_p2pnet_detector,
)
from app.services.p2pnet_congestion import (
    evaluate_p2pnet_manifest,
)


P2PNET_MODEL_NAME = "P2PNet"
P2PNET_MODEL_VERSION = "SHTechA"


def run_evaluation(
    *,
    manifest_path: Path,
    output_path: Path,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
    threshold: float,
) -> EvaluationSummary:
    detector = load_p2pnet_detector(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
        threshold=threshold,
    )
    records = evaluate_p2pnet_manifest(
        manifest_path=manifest_path,
        detector=detector,
    )

    save_evaluation_report(
        records=records,
        output_path=output_path,
        model_name=P2PNET_MODEL_NAME,
        model_version=P2PNET_MODEL_VERSION,
    )

    return summarize_evaluations(
        records,
    )


def main() -> None:
    parser = ArgumentParser(
        description=(
            "P2PNet 머리 점으로 사람 수와 "
            "C·L 혼잡도를 평가하고 JSON "
            "보고서를 저장합니다."
        ),
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
        help="평가 표본 CSV manifest 경로",
    )
    parser.add_argument(
        "--p2pnet-root",
        type=Path,
        required=True,
        help="P2PNet 외부 코드 루트 경로",
    )
    parser.add_argument(
        "--weight-path",
        type=Path,
        required=True,
        help="P2PNet 가중치 파일 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(
            "runs/p2pnet-congestion-report.json",
        ),
        help="평가 결과 JSON 저장 경로",
    )
    parser.add_argument(
        "--device",
        choices=[
            "cpu",
            "cuda",
        ],
        help=("추론 장치. 생략하면 CUDA 사용 가능 여부에 따라 자동 선택합니다."),
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.5,
        help="P2PNet 머리 점 선택 임계값",
    )

    arguments = parser.parse_args()

    device = arguments.device

    if device is None:
        device = "cuda" if torch.cuda.is_available() else "cpu"

    summary = run_evaluation(
        manifest_path=arguments.manifest_path,
        output_path=arguments.output,
        p2pnet_root=arguments.p2pnet_root,
        weight_path=arguments.weight_path,
        device=device,
        threshold=arguments.threshold,
    )

    print(f"device: {device}")
    print(
        "sampleCount:",
        summary.sample_count,
    )
    print(
        "totalGroundTruthCount:",
        summary.total_ground_truth_count,
    )
    print(
        "totalPredictedCount:",
        summary.total_predicted_count,
    )
    print(
        "meanAbsoluteError:",
        summary.mean_absolute_error,
    )
    print(
        "meanProcessingTimeMs:",
        summary.mean_processing_time_ms,
    )
    print(
        "output:",
        arguments.output,
    )


if __name__ == "__main__":
    main()
