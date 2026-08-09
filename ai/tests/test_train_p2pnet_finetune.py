import json
from pathlib import Path
from types import SimpleNamespace

import torch
from torch import nn

from app.services.p2pnet_finetuning import (
    P2PNetEpochSummary,
    P2PNetValidationSummary,
)
from scripts import train_p2pnet_finetune


def test_run_training_saves_last_and_best_checkpoints(
    tmp_path: Path,
    monkeypatch,
) -> None:
    class FakeDataset:
        def __init__(
            self,
            *,
            manifest_path: Path,
            split: str,
            training: bool,
            **kwargs,
        ) -> None:
            self.split = split
            self.training = training
            self.samples = [
                SimpleNamespace(
                    source=(
                        "TEAM_CONSENTED" if split == "TRAIN" else "JHU-CROWD++ v2.0"
                    ),
                ),
            ]

        def __len__(self) -> int:
            return 1

    model = nn.Linear(
        2,
        1,
    )
    criterion = nn.Identity()
    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=1e-5,
    )

    monkeypatch.setattr(
        train_p2pnet_finetune,
        "P2PNetManifestDataset",
        FakeDataset,
    )
    monkeypatch.setattr(
        train_p2pnet_finetune,
        "load_p2pnet_training_components",
        lambda **_: (
            model,
            criterion,
            torch.device("cpu"),
        ),
    )
    monkeypatch.setattr(
        train_p2pnet_finetune,
        "build_p2pnet_optimizer",
        lambda *_, **__: optimizer,
    )
    monkeypatch.setattr(
        train_p2pnet_finetune,
        "build_p2pnet_training_data_loader",
        lambda *_, **__: object(),
    )
    monkeypatch.setattr(
        train_p2pnet_finetune,
        "build_p2pnet_validation_data_loader",
        lambda *_, **__: object(),
    )
    monkeypatch.setattr(
        train_p2pnet_finetune,
        "train_p2pnet_epoch",
        lambda **_: P2PNetEpochSummary(
            batch_count=2,
            mean_total_loss=1.5,
            mean_losses={
                "loss_ce": 1.0,
                "loss_points": 0.5,
            },
        ),
    )
    validation_summaries = iter(
        [
            P2PNetValidationSummary(
                sample_count=3,
                mean_absolute_error=3.0,
                root_mean_squared_error=3.5,
            ),
            P2PNetValidationSummary(
                sample_count=3,
                mean_absolute_error=2.0,
                root_mean_squared_error=2.5,
            ),
        ]
    )

    monkeypatch.setattr(
        train_p2pnet_finetune,
        "evaluate_p2pnet_count",
        lambda **_: next(
            validation_summaries,
        ),
    )

    saved_paths: list[Path] = []

    def fake_save_checkpoint(
        *,
        output_path: Path,
        **kwargs,
    ) -> None:
        saved_paths.append(
            output_path,
        )

    monkeypatch.setattr(
        train_p2pnet_finetune,
        "save_p2pnet_checkpoint",
        fake_save_checkpoint,
    )

    output_directory = tmp_path / "training-output"

    report = train_p2pnet_finetune.run_training(
        manifest_path=tmp_path / "manifest.csv",
        p2pnet_root=tmp_path / "p2pnet",
        weight_path=tmp_path / "SHTechA.pth",
        output_directory=output_directory,
        device="cpu",
        epochs=1,
        samples_per_epoch=2,
    )

    assert saved_paths == [
        output_directory / "last.pth",
        output_directory / "epoch-001.pth",
        output_directory / "best.pth",
    ]
    assert report["baselineValidationMae"] == 3.0
    assert report["bestEpoch"] == 1
    assert report["bestValidationMae"] == 2.0
    assert report["bestMaeImprovement"] == 1.0

    report_path = output_directory / "training-report.json"
    assert report_path.exists()

    saved_report = json.loads(
        report_path.read_text(
            encoding="utf-8",
        )
    )
    assert saved_report == report


def test_select_validation_dataset_filters_source() -> None:
    class DummyDataset:
        def __init__(self) -> None:
            self.samples = [
                SimpleNamespace(
                    source="JHU-CROWD++ v2.0",
                ),
                SimpleNamespace(
                    source="TEAM_CONSENTED",
                ),
                SimpleNamespace(
                    source="TEAM_CONSENTED",
                ),
            ]

        def __len__(self) -> int:
            return len(
                self.samples,
            )

        def __getitem__(
            self,
            index: int,
        ) -> int:
            return index

    subset = train_p2pnet_finetune.select_validation_dataset(
        DummyDataset(),
        validation_source=("TEAM_CONSENTED"),
        validation_limit=1,
    )

    assert len(subset) == 1
    assert subset[0] == 1
