import argparse
from collections.abc import Callable
from pathlib import Path

import torch

from app.services.head_point_dataset import load_head_point_manifest
from app.services.p2pnet_benchmark_adapter import load_p2pnet_detector
from app.services.person_detector import detect_people
from app.services.service_like_model_validation import (
    DetectionResult,
    ServiceLikeEvaluation,
    build_service_like_evaluation,
    load_ground_truth_centers,
    load_spatial_reviews,
    render_model_comparison,
    save_service_like_evaluation_report,
)


Detector = Callable[[bytes], DetectionResult]
DEFAULT_THRESHOLDS = [0.5, 0.55, 0.6, 0.65, 0.7]


def parse_thresholds(value: str) -> list[float]:
    try:
        thresholds = [float(item.strip()) for item in value.split(",") if item.strip()]
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            "thresholds는 쉼표로 구분한 실수여야 합니다.",
        ) from error

    if not thresholds or any(
        threshold < 0.0 or threshold > 1.0 for threshold in thresholds
    ):
        raise argparse.ArgumentTypeError(
            "thresholds는 0과 1 사이 값이 한 개 이상 필요합니다.",
        )

    return sorted(set(thresholds))


def run_evaluation(
    *,
    manifest_path: Path,
    spatial_review_path: Path,
    output_path: Path,
    visualization_directory: Path,
    thresholds: list[float],
    p2pnet_root: Path | None = None,
    weight_path: Path | None = None,
    device: str = "cpu",
    yolo_detect: Detector = detect_people,
    p2pnet_detect: Detector | None = None,
) -> ServiceLikeEvaluation:
    reviews = load_spatial_reviews(spatial_review_path)
    all_samples = load_head_point_manifest(manifest_path)
    samples = [sample for sample in all_samples if sample.sample_id in reviews]

    if len(samples) != len(reviews):
        missing_ids = sorted(set(reviews) - {sample.sample_id for sample in samples})
        raise ValueError(
            "공간 분포 검수 표본이 manifest에 없습니다: " + ", ".join(missing_ids),
        )

    if p2pnet_detect is None:
        if p2pnet_root is None or weight_path is None:
            raise ValueError(
                "P2PNet 평가에는 p2pnet_root와 weight_path가 필요합니다.",
            )

        p2pnet_detect = load_p2pnet_detector(
            p2pnet_root=p2pnet_root,
            weight_path=weight_path,
            device=device,
            threshold=min(thresholds),
        )

    yolo_results: dict[str, DetectionResult] = {}
    p2pnet_results: dict[str, DetectionResult] = {}

    for sample in samples:
        image_bytes = sample.image_path.read_bytes()
        yolo_results[sample.sample_id] = yolo_detect(image_bytes)
        p2pnet_results[sample.sample_id] = p2pnet_detect(image_bytes)

    evaluation = build_service_like_evaluation(
        samples=samples,
        reviews=reviews,
        yolo_results=yolo_results,
        p2pnet_results=p2pnet_results,
        thresholds=thresholds,
    )
    save_service_like_evaluation_report(
        evaluation=evaluation,
        output_path=output_path,
        metadata={
            "manifestPath": manifest_path.as_posix(),
            "spatialReviewPath": spatial_review_path.as_posix(),
            "p2pnetRoot": (p2pnet_root.as_posix() if p2pnet_root is not None else None),
            "weightPath": (weight_path.as_posix() if weight_path is not None else None),
            "device": device,
            "confidenceThresholds": thresholds,
            "operationalRuleChanged": False,
        },
    )

    visualization_directory.mkdir(parents=True, exist_ok=True)

    for sample in samples:
        comparison = render_model_comparison(
            image_bytes=sample.image_path.read_bytes(),
            ground_truth_centers=load_ground_truth_centers(sample),
            yolo_centers=(yolo_results[sample.sample_id].person_centers),
            p2pnet_centers=(p2pnet_results[sample.sample_id].person_centers),
            labels=("GROUND_TRUTH", "YOLO", "P2PNET"),
        )
        (visualization_directory / f"{sample.sample_id}.comparison.png").write_bytes(
            comparison
        )

    return evaluation


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "독립 실서비스 유사 표본에서 YOLO/P2PNet 탐지 오류와 "
            "8x8 C·L 과대평가를 분리 검증합니다."
        ),
    )
    parser.add_argument("manifest_path", type=Path)
    parser.add_argument("--spatial-review", type=Path, required=True)
    parser.add_argument("--p2pnet-root", type=Path, required=True)
    parser.add_argument("--weight-path", type=Path, required=True)
    parser.add_argument(
        "--device",
        default="cuda" if torch.cuda.is_available() else "cpu",
    )
    parser.add_argument(
        "--thresholds",
        type=parse_thresholds,
        default=DEFAULT_THRESHOLDS,
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--visualization-directory",
        type=Path,
        required=True,
    )
    arguments = parser.parse_args()

    evaluation = run_evaluation(
        manifest_path=arguments.manifest_path,
        spatial_review_path=arguments.spatial_review,
        output_path=arguments.output,
        visualization_directory=arguments.visualization_directory,
        thresholds=arguments.thresholds,
        p2pnet_root=arguments.p2pnet_root,
        weight_path=arguments.weight_path,
        device=arguments.device,
    )

    print(f"sampleCount: {evaluation.summary.sample_count}")
    print(
        f"detectionErrorSampleCount: {evaluation.summary.detection_error_sample_count}"
    )
    print(
        "scoringOverestimationSampleCount: "
        f"{evaluation.summary.scoring_overestimation_sample_count}"
    )
    print(f"output: {arguments.output}")
    print(f"visualizations: {arguments.visualization_directory}")


if __name__ == "__main__":
    main()
