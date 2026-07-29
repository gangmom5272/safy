import argparse
from pathlib import Path

from app.services.head_point_dataset import (
    HeadPointDatasetSummary,
    validate_head_point_dataset,
)


def run_validation(
    manifest_path: Path,
) -> HeadPointDatasetSummary:
    summary = validate_head_point_dataset(manifest_path)

    print(f"sampleCount: {summary.sample_count}")
    print(f"personCount: {summary.person_count}")

    print("splitCounts:")
    for name, count in sorted(summary.split_counts.items()):
        print(f"  {name}: {count}")

    print("scenarioCounts:")
    for name, count in sorted(summary.scenario_counts.items()):
        print(f"  {name}: {count}")

    print("sourceCounts:")
    for name, count in sorted(summary.source_counts.items()):
        print(f"  {name}: {count}")

    return summary


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("SAFY 머리 좌표 학습 데이터셋을 검증합니다.")
    )
    parser.add_argument(
        "manifest",
        type=Path,
        help="검증할 학습 데이터 manifest CSV",
    )

    args = parser.parse_args()

    run_validation(
        args.manifest,
    )


if __name__ == "__main__":
    main()
