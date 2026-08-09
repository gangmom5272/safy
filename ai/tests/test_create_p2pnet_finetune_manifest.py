from pathlib import Path
from types import SimpleNamespace

from scripts import create_p2pnet_finetune_manifest


def test_run_creation_returns_team_split_summary(
    tmp_path: Path,
    monkeypatch,
) -> None:
    samples = [
        SimpleNamespace(source="TEAM_CONSENTED", split="TRAIN", scenario="SPARSE"),
        SimpleNamespace(source="TEAM_CONSENTED", split="VALIDATION", scenario="EMPTY"),
        SimpleNamespace(
            source="JHU-CROWD++ v2.0", split="VALIDATION", scenario="CROWDED"
        ),
    ]
    output_path = tmp_path / "manifest.csv"

    monkeypatch.setattr(
        create_p2pnet_finetune_manifest,
        "load_head_point_manifest",
        lambda _: samples,
    )
    monkeypatch.setattr(
        create_p2pnet_finetune_manifest,
        "assign_team_validation_split",
        lambda loaded_samples, **_: loaded_samples,
    )
    monkeypatch.setattr(
        create_p2pnet_finetune_manifest,
        "save_p2pnet_finetune_manifest",
        lambda **_: output_path,
    )

    report = create_p2pnet_finetune_manifest.run_creation(
        source_path=tmp_path / "source.csv",
        output_path=output_path,
    )

    assert report == {
        "sampleCount": 3,
        "teamSampleCount": 2,
        "teamSplitCounts": {"TRAIN": 1, "VALIDATION": 1},
        "teamValidationScenarioCounts": {"EMPTY": 1},
        "output": output_path.as_posix(),
    }
