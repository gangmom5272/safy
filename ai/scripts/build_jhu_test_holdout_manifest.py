import argparse
from collections import Counter
from pathlib import Path

from app.services.jhu_test_holdout import (
    DEFAULT_QUALITATIVE_SUBSET_COUNTS,
    build_test_holdout_manifest,
    load_test_split_samples,
    select_balanced_qualitative_subset,
)
from app.services.p2pnet_finetune_split import save_p2pnet_finetune_manifest


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "JHU-CROWD++ TEST 분할 전체를 138번 독립 holdout manifest로 만들고, "
            "정성 검수용 균형 subset manifest를 함께 생성합니다."
        ),
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
        help="safy-head-point-v1 manifest 경로",
    )
    parser.add_argument(
        "--holdout-output",
        type=Path,
        required=True,
        help="전체 TEST holdout manifest 저장 경로",
    )
    parser.add_argument(
        "--subset-output",
        type=Path,
        required=True,
        help="균형 검수 subset manifest 저장 경로",
    )
    parser.add_argument(
        "--random-seed",
        type=int,
        default=42,
    )
    arguments = parser.parse_args()

    build_test_holdout_manifest(
        source_manifest_path=arguments.manifest_path,
        output_path=arguments.holdout_output,
    )

    test_samples = load_test_split_samples(arguments.manifest_path)

    qualitative_subset = select_balanced_qualitative_subset(
        test_samples,
        counts_per_scenario=DEFAULT_QUALITATIVE_SUBSET_COUNTS,
        random_seed=arguments.random_seed,
    )

    save_p2pnet_finetune_manifest(
        samples=qualitative_subset,
        output_path=arguments.subset_output,
    )

    print(f"holdoutSampleCount: {len(test_samples)}")
    print(
        "holdoutScenarioCounts:",
        dict(Counter(sample.scenario for sample in test_samples)),
    )
    print(f"holdoutOutput: {arguments.holdout_output}")
    print(f"qualitativeSubsetSampleCount: {len(qualitative_subset)}")
    print(
        "qualitativeSubsetScenarioCounts:",
        dict(Counter(sample.scenario for sample in qualitative_subset)),
    )
    print(f"qualitativeSubsetOutput: {arguments.subset_output}")


if __name__ == "__main__":
    main()
