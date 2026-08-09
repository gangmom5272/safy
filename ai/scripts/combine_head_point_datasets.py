import argparse
from pathlib import Path

from app.services.head_point_dataset import (
    combine_head_point_manifests,
    validate_head_point_dataset,
)


def run_combination(
    *,
    manifest_paths: list[Path],
    output_path: Path,
) -> Path:
    combined_path = combine_head_point_manifests(
        manifest_paths=manifest_paths,
        output_path=output_path,
    )

    summary = validate_head_point_dataset(combined_path)

    print(f"sampleCount: {summary.sample_count}")
    print(f"personCount: {summary.person_count}")
    print(f"output: {combined_path}")

    return combined_path


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("여러 머리 좌표 데이터셋 manifest를 결합합니다.")
    )
    parser.add_argument(
        "manifest_paths",
        nargs="+",
        type=Path,
        help="결합할 manifest 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="결합 manifest 출력 경로",
    )

    arguments = parser.parse_args()

    run_combination(
        manifest_paths=(arguments.manifest_paths),
        output_path=arguments.output,
    )


if __name__ == "__main__":
    main()
