import argparse
from pathlib import Path

import torch

from app.services.head_point_model_evaluation import (
    evaluate_head_point_manifest,
    save_holdout_evaluation_report,
    summarize_by_scenario_and_macro,
)
from app.services.p2pnet_benchmark_adapter import load_p2pnet_detector
from app.services.person_detector import detect_people


def _build_detector(
    *,
    model: str,
    p2pnet_root: Path | None,
    weight_path: Path | None,
    device: str,
    threshold: float,
):
    if model == "yolo":
        return "YOLO", "yolo11n", detect_people

    if p2pnet_root is None or weight_path is None:
        raise ValueError("P2PNet 평가에는 --p2pnet-root와 --weight-path가 필요합니다.")

    detector = load_p2pnet_detector(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
        threshold=threshold,
    )

    return "P2PNet", weight_path.stem, detector


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "JHU-CROWD++ TEST holdout(또는 균형 subset) manifest로 YOLO/P2PNet을 "
            "평가하고 시나리오별·macro 요약 보고서를 저장합니다."
        ),
    )
    parser.add_argument("manifest_path", type=Path)
    parser.add_argument("--model", choices=["yolo", "p2pnet"], required=True)
    parser.add_argument("--p2pnet-root", type=Path)
    parser.add_argument("--weight-path", type=Path)
    parser.add_argument(
        "--device",
        default="cuda" if torch.cuda.is_available() else "cpu",
    )
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    model_name, model_version, detect = _build_detector(
        model=arguments.model,
        p2pnet_root=arguments.p2pnet_root,
        weight_path=arguments.weight_path,
        device=arguments.device,
        threshold=arguments.threshold,
    )

    records = evaluate_head_point_manifest(
        manifest_path=arguments.manifest_path,
        detect=detect,
    )

    summary = summarize_by_scenario_and_macro(
        model_name=model_name,
        model_version=model_version,
        records=records,
    )

    save_holdout_evaluation_report(
        summary=summary,
        records=records,
        output_path=arguments.output,
    )

    print(f"model: {model_name}:{model_version}")
    print(f"overallSampleCount: {summary.overall.sample_count}")
    print(f"overallMeanAbsoluteError: {summary.overall.mean_absolute_error}")
    print(f"macroMeanAbsoluteError: {summary.macro_mean_absolute_error}")
    print(f"macroRootMeanSquaredError: {summary.macro_root_mean_squared_error}")
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
