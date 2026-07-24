from argparse import ArgumentParser
from pathlib import Path

from app.services.model_evaluation import (
    EvaluationSummary,
    evaluate_manifest,
    save_evaluation_report,
    summarize_evaluations,
)


def run_evaluation(
    manifest_path: Path,
    output_path: Path,
) -> EvaluationSummary:
    records = evaluate_manifest(manifest_path)

    save_evaluation_report(
        records=records,
        output_path=output_path,
    )

    return summarize_evaluations(records)


def main() -> None:
    parser = ArgumentParser(
        description=(
            "YOLO 기준 모델의 사람 계수 성능을 평가하고 JSON 보고서를 저장합니다."
        ),
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
        help="평가 표본 CSV 매니페스트 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("runs/yolo-baseline-report.json"),
        help="평가 결과 JSON 저장 경로",
    )

    arguments = parser.parse_args()

    summary = run_evaluation(
        manifest_path=arguments.manifest_path,
        output_path=arguments.output,
    )

    print(f"sampleCount: {summary.sample_count}")
    print(f"totalGroundTruthCount: {summary.total_ground_truth_count}")
    print(f"totalPredictedCount: {summary.total_predicted_count}")
    print(f"meanAbsoluteError: {summary.mean_absolute_error}")
    print(f"meanProcessingTimeMs: {summary.mean_processing_time_ms}")
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
