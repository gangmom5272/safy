import json
from pathlib import Path
from types import SimpleNamespace

import torch

from app.services.p2pnet_finetuning import P2PNetValidationSummary
from scripts import compare_p2pnet_finetune_checkpoints


def test_compare_checkpoints_combines_team_and_external_metrics(
    tmp_path: Path,
    monkeypatch,
) -> None:
    checkpoint_directory = tmp_path / "checkpoints"
    checkpoint_directory.mkdir()
    (checkpoint_directory / "epoch-001.pth").write_bytes(b"checkpoint")
    (checkpoint_directory / "training-report.json").write_text(
        json.dumps(
            {
                "baselineValidation": {
                    "sampleCount": 2,
                    "meanAbsoluteError": 10.0,
                    "rootMeanSquaredError": 11.0,
                },
                "epochs": [
                    {
                        "epoch": 1,
                        "validation": {
                            "sampleCount": 2,
                            "meanAbsoluteError": 5.0,
                            "rootMeanSquaredError": 6.0,
                        },
                    }
                ],
            }
        ),
        encoding="utf-8",
    )

    class FakeDataset:
        def __init__(self, **_) -> None:
            self.samples = [SimpleNamespace(source="JHU-CROWD++ v2.0")]

        def __len__(self) -> int:
            return 1

        def __getitem__(self, index: int):
            return index

    summaries = iter(
        [
            P2PNetValidationSummary(1, 8.0, 9.0),
            P2PNetValidationSummary(1, 7.0, 8.0),
        ]
    )
    monkeypatch.setattr(
        compare_p2pnet_finetune_checkpoints,
        "P2PNetManifestDataset",
        FakeDataset,
    )
    monkeypatch.setattr(
        compare_p2pnet_finetune_checkpoints,
        "build_p2pnet_validation_data_loader",
        lambda *_, **__: object(),
    )
    monkeypatch.setattr(
        compare_p2pnet_finetune_checkpoints,
        "_load_model",
        lambda **_: (torch.nn.Identity(), torch.device("cpu"), None),
    )
    monkeypatch.setattr(
        compare_p2pnet_finetune_checkpoints,
        "evaluate_p2pnet_count",
        lambda **_: next(summaries),
    )

    output_path = tmp_path / "comparison.json"
    report = compare_p2pnet_finetune_checkpoints.compare_checkpoints(
        manifest_path=tmp_path / "manifest.csv",
        p2pnet_root=tmp_path / "p2pnet",
        baseline_weight_path=tmp_path / "baseline.pth",
        checkpoint_directory=checkpoint_directory,
        output_path=output_path,
        device="cpu",
    )

    assert [record["name"] for record in report["records"]] == [
        "baseline",
        "epoch-001",
    ]
    assert report["records"][1]["teamValidation"]["meanAbsoluteError"] == 5.0
    assert report["records"][1]["externalValidation"]["meanAbsoluteError"] == 7.0
    assert json.loads(output_path.read_text(encoding="utf-8")) == report
