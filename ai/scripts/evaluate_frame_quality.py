import argparse
from pathlib import Path

from app.services.frame_quality_evaluation import (
    FrameQualityEvaluationRecord,
    evaluate_frame_quality_sample,
    load_frame_quality_manifest,
    save_frame_quality_report,
)


def run_evaluation(
    *,
    manifest_path: Path,
    output_path: Path,
) -> list[FrameQualityEvaluationRecord]:
    samples = load_frame_quality_manifest(manifest_path)
    records = [evaluate_frame_quality_sample(sample) for sample in samples]

    save_frame_quality_report(
        records,
        output_path,
    )

    return records


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("프레임 품질 표본의 기대 결과와 실제 판정 결과를 비교합니다.")
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

    args = parser.parse_args()

    records = run_evaluation(
        manifest_path=args.manifest_path,
        output_path=args.output,
    )

    matched_count = sum(record.matches_expectation for record in records)

    print("sampleCount:", len(records))
    print("matchedCount:", matched_count)
    print(
        "mismatchedCount:",
        len(records) - matched_count,
    )
    print("output:", args.output)


if __name__ == "__main__":
    main()
