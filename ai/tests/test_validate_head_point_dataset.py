from pathlib import Path

from app.services.head_point_dataset import (
    HeadPointDatasetSummary,
)
from scripts import validate_head_point_dataset


def test_run_validation_prints_summary(
    monkeypatch,
    capsys,
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    expected_summary = HeadPointDatasetSummary(
        sample_count=10,
        person_count=50,
        split_counts={
            "TRAIN": 8,
            "VALIDATION": 1,
            "TEST": 1,
        },
        scenario_counts={
            "SPARSE": 3,
            "NORMAL": 3,
            "LOCAL_DENSE": 2,
            "CROWDED": 2,
        },
        source_counts={
            "TEAM_CONSENTED": 5,
            "JHU-CROWD++ v2.0": 5,
        },
    )

    monkeypatch.setattr(
        validate_head_point_dataset,
        "validate_head_point_dataset",
        lambda _: expected_summary,
    )

    summary = validate_head_point_dataset.run_validation(manifest_path)

    output = capsys.readouterr().out

    assert summary == expected_summary
    assert "sampleCount: 10" in output
    assert "personCount: 50" in output
    assert "TRAIN: 8" in output
    assert "CROWDED: 2" in output
    assert "TEAM_CONSENTED: 5" in output
