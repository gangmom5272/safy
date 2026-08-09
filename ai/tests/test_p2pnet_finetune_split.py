import csv
from pathlib import Path
from dataclasses import replace

from app.services.head_point_dataset import (
    HeadPointSample,
)
from app.services.p2pnet_finetune_split import (
    assign_team_validation_split,
    save_p2pnet_finetune_manifest,
)


def make_sample(
    *,
    sample_id: str,
    source: str,
    split: str,
    scenario: str,
) -> HeadPointSample:
    return HeadPointSample(
        sample_id=sample_id,
        source=source,
        source_group=sample_id,
        split=split,
        image_path=Path(
            f"{sample_id}.jpg",
        ),
        annotation_path=Path(
            f"{sample_id}.csv",
        ),
        image_width=640,
        image_height=360,
        person_count=1,
        scenario=scenario,
        license="test",
        consent_confirmed=(source == "TEAM_CONSENTED"),
        image_sha256="a" * 64,
        label_format_version="head-point-v1",
    )


def test_assign_team_validation_split_is_stratified_and_deterministic() -> None:
    samples = [
        *[
            make_sample(
                sample_id=f"team-empty-{index}",
                source="TEAM_CONSENTED",
                split="TRAIN",
                scenario="EMPTY",
            )
            for index in range(5)
        ],
        *[
            make_sample(
                sample_id=f"team-sparse-{index}",
                source="TEAM_CONSENTED",
                split="TRAIN",
                scenario="SPARSE",
            )
            for index in range(5)
        ],
        make_sample(
            sample_id="jhu-validation-1",
            source="JHU-CROWD++ v2.0",
            split="VALIDATION",
            scenario="CROWDED",
        ),
    ]

    first_result = assign_team_validation_split(
        samples,
        validation_ratio=0.2,
        random_seed=42,
    )
    second_result = assign_team_validation_split(
        samples,
        validation_ratio=0.2,
        random_seed=42,
    )

    first_splits = {sample.sample_id: sample.split for sample in first_result}
    second_splits = {sample.sample_id: sample.split for sample in second_result}

    assert first_splits == second_splits

    team_validation_samples = [
        sample
        for sample in first_result
        if (sample.source == "TEAM_CONSENTED" and sample.split == "VALIDATION")
    ]

    assert (
        len(
            team_validation_samples,
        )
        == 2
    )
    assert {sample.scenario for sample in team_validation_samples} == {
        "EMPTY",
        "SPARSE",
    }

    assert first_splits["jhu-validation-1"] == "VALIDATION"


def test_save_p2pnet_finetune_manifest(
    tmp_path: Path,
) -> None:
    samples = [
        make_sample(
            sample_id="team-train-1",
            source="TEAM_CONSENTED",
            split="TRAIN",
            scenario="SPARSE",
        ),
        make_sample(
            sample_id="team-validation-1",
            source="TEAM_CONSENTED",
            split="VALIDATION",
            scenario="EMPTY",
        ),
    ]
    samples = [
        replace(
            sample,
            image_path=(tmp_path / f"{sample.sample_id}.jpg"),
            annotation_path=(tmp_path / f"{sample.sample_id}.csv"),
        )
        for sample in samples
    ]
    output_path = tmp_path / "fine-tune" / "manifest.csv"

    save_p2pnet_finetune_manifest(
        samples=samples,
        output_path=output_path,
    )

    with output_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        records = list(csv.DictReader(stream))

    assert [
        (
            record["sample_id"],
            record["split"],
        )
        for record in records
    ] == [
        (
            "team-train-1",
            "TRAIN",
        ),
        (
            "team-validation-1",
            "VALIDATION",
        ),
    ]

    assert records[0]["image_path"] == "../team-train-1.jpg"
    assert records[0]["annotation_path"] == "../team-train-1.csv"
