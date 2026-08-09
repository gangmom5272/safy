import csv
import os
import random
from collections import defaultdict
from dataclasses import replace
from pathlib import Path

from app.services.head_point_dataset import (
    HeadPointSample,
)


TEAM_CONSENTED_SOURCE = "TEAM_CONSENTED"


def assign_team_validation_split(
    samples: list[HeadPointSample],
    *,
    validation_ratio: float = 0.2,
    random_seed: int = 42,
) -> list[HeadPointSample]:
    if not samples:
        raise ValueError(
            "머리 좌표 표본은 한 건 이상 필요합니다.",
        )

    if not 0.0 < validation_ratio < 1.0:
        raise ValueError(
            "검증 비율은 0과 1 사이여야 합니다.",
        )

    team_training_by_scenario: dict[
        str,
        list[HeadPointSample],
    ] = defaultdict(
        list,
    )

    for sample in samples:
        if sample.source == TEAM_CONSENTED_SOURCE and sample.split == "TRAIN":
            team_training_by_scenario[sample.scenario].append(
                sample,
            )

    random_generator = random.Random(
        random_seed,
    )
    validation_sample_ids: set[str] = set()

    for scenario in sorted(
        team_training_by_scenario,
    ):
        scenario_samples = sorted(
            team_training_by_scenario[scenario],
            key=lambda sample: sample.sample_id,
        )
        random_generator.shuffle(
            scenario_samples,
        )

        if len(scenario_samples) <= 1:
            validation_count = 0
        else:
            validation_count = max(
                1,
                round(len(scenario_samples) * validation_ratio),
            )
            validation_count = min(
                validation_count,
                len(scenario_samples) - 1,
            )

        validation_sample_ids.update(
            sample.sample_id for sample in scenario_samples[:validation_count]
        )

    return [
        (
            replace(
                sample,
                split="VALIDATION",
            )
            if sample.sample_id in validation_sample_ids
            else sample
        )
        for sample in samples
    ]


def save_p2pnet_finetune_manifest(
    *,
    samples: list[HeadPointSample],
    output_path: Path,
) -> Path:
    if not samples:
        raise ValueError(
            "저장할 머리 좌표 표본이 한 건 이상 필요합니다.",
        )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    fieldnames = [
        "sample_id",
        "source",
        "source_group",
        "split",
        "image_path",
        "annotation_path",
        "image_width",
        "image_height",
        "person_count",
        "scenario",
        "license",
        "consent_confirmed",
        "image_sha256",
        "label_format_version",
    ]

    with output_path.open(
        "w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fieldnames,
        )
        writer.writeheader()

        for sample in samples:
            relative_image_path = Path(
                os.path.relpath(
                    sample.image_path.resolve(),
                    output_path.parent.resolve(),
                )
            ).as_posix()
            relative_annotation_path = Path(
                os.path.relpath(
                    sample.annotation_path.resolve(),
                    output_path.parent.resolve(),
                )
            ).as_posix()

            writer.writerow(
                {
                    "sample_id": (sample.sample_id),
                    "source": sample.source,
                    "source_group": (sample.source_group),
                    "split": sample.split,
                    "image_path": (relative_image_path),
                    "annotation_path": (relative_annotation_path),
                    "image_width": (sample.image_width),
                    "image_height": (sample.image_height),
                    "person_count": (sample.person_count),
                    "scenario": (sample.scenario),
                    "license": sample.license,
                    "consent_confirmed": (str(sample.consent_confirmed).lower()),
                    "image_sha256": (sample.image_sha256),
                    "label_format_version": (sample.label_format_version),
                }
            )

    return output_path
