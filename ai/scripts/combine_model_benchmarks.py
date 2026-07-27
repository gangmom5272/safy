import argparse
from pathlib import Path

from app.services.model_comparison import (
    ModelComparisonRecord,
    ModelRuntimeProfile,
    load_model_comparison_report,
    save_model_comparison_report,
    summarize_by_model,
)


def combine_reports(
    *,
    input_paths: list[Path],
    output_path: Path,
) -> tuple[
    list[ModelComparisonRecord],
    list[ModelRuntimeProfile],
]:
    combined_records: list[ModelComparisonRecord] = []
    combined_profiles: list[ModelRuntimeProfile] = []

    for input_path in input_paths:
        records, profiles = load_model_comparison_report(input_path)
        combined_records.extend(records)
        combined_profiles.extend(profiles)

    save_model_comparison_report(
        combined_records,
        output_path,
        runtime_profiles=combined_profiles,
    )

    return (
        combined_records,
        combined_profiles,
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("모델별 벤치마크 보고서를 하나의 비교 보고서로 합칩니다.")
    )
    parser.add_argument(
        "input_paths",
        type=Path,
        nargs="+",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
    )

    args = parser.parse_args()

    records, _ = combine_reports(
        input_paths=args.input_paths,
        output_path=args.output,
    )

    summaries = summarize_by_model(records)

    for model_key, summary in sorted(summaries.items()):
        print("model:", model_key)
        print(
            "  sampleCount:",
            summary.sample_count,
        )
        print(
            "  meanAbsoluteError:",
            summary.mean_absolute_error,
        )
        print(
            "  meanInferenceTimeMs:",
            summary.mean_inference_time_ms,
        )
        print(
            "  peakGpuMemoryMb:",
            summary.peak_gpu_memory_mb,
        )

    print("output:", args.output)


if __name__ == "__main__":
    main()
