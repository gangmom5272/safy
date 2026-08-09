import argparse
from collections import Counter
from pathlib import Path

from app.services.head_point_dataset import load_head_point_manifest
from app.services.p2pnet_finetune_split import (
    TEAM_CONSENTED_SOURCE,
    assign_team_validation_split,
    save_p2pnet_finetune_manifest,
)


def run_creation(
    *,
    source_path: Path,
    output_path: Path,
    validation_ratio: float = 0.2,
    random_seed: int = 42,
) -> dict:
    samples = load_head_point_manifest(source_path)
    split_samples = assign_team_validation_split(
        samples,
        validation_ratio=validation_ratio,
        random_seed=random_seed,
    )
    save_p2pnet_finetune_manifest(
        samples=split_samples,
        output_path=output_path,
    )

    team_samples = [
        sample for sample in split_samples if sample.source == TEAM_CONSENTED_SOURCE
    ]
    team_validation_samples = [
        sample for sample in team_samples if sample.split == "VALIDATION"
    ]

    return {
        "sampleCount": len(split_samples),
        "teamSampleCount": len(team_samples),
        "teamSplitCounts": dict(Counter(sample.split for sample in team_samples)),
        "teamValidationScenarioCounts": dict(
            Counter(sample.scenario for sample in team_validation_samples)
        ),
        "output": output_path.as_posix(),
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="팀 사진 일부를 검증용으로 분리한 P2PNet 미세 조정 manifest를 생성합니다.",
    )
    parser.add_argument("source_path", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--validation-ratio", type=float, default=0.2)
    parser.add_argument("--random-seed", type=int, default=42)
    arguments = parser.parse_args()

    report = run_creation(
        source_path=arguments.source_path,
        output_path=arguments.output,
        validation_ratio=arguments.validation_ratio,
        random_seed=arguments.random_seed,
    )

    print(f"sampleCount: {report['sampleCount']}")
    print(f"teamSampleCount: {report['teamSampleCount']}")
    print(f"teamSplitCounts: {report['teamSplitCounts']}")
    print(f"teamValidationScenarioCounts: {report['teamValidationScenarioCounts']}")
    print(f"output: {report['output']}")


if __name__ == "__main__":
    main()
