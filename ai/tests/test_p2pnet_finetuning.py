from pathlib import Path

import pytest
import torch
from torch import nn
from torch.utils.data import (
    SequentialSampler,
    WeightedRandomSampler,
)

from app.services import p2pnet_finetuning
from app.services.head_point_dataset import (
    HeadPointSample,
)
from app.services.p2pnet_finetuning import (
    P2PNetEpochSummary,
    P2PNetValidationSummary,
    build_p2pnet_optimizer,
    build_p2pnet_training_arguments,
    build_p2pnet_training_data_loader,
    build_p2pnet_validation_data_loader,
    build_source_balanced_sample_weights,
    build_source_balanced_sampler,
    collate_p2pnet_samples,
    evaluate_p2pnet_count,
    load_p2pnet_training_components,
    normalize_p2pnet_loss_weights,
    save_p2pnet_checkpoint,
    train_p2pnet_batch,
    train_p2pnet_epoch,
)


def make_sample(
    sample_id: str,
    source: str,
) -> HeadPointSample:
    return HeadPointSample(
        sample_id=sample_id,
        source=source,
        source_group=sample_id,
        split="TRAIN",
        image_path=Path(f"{sample_id}.jpg"),
        annotation_path=Path(f"{sample_id}.csv"),
        image_width=640,
        image_height=360,
        person_count=1,
        scenario="SPARSE",
        license="test",
        consent_confirmed=True,
        image_sha256="a" * 64,
        label_format_version="head-point-v1",
    )


def test_source_balanced_weights_reserve_team_sampling_ratio() -> None:
    samples = [
        make_sample(
            "jhu-1",
            "JHU-CROWD++ v2.0",
        ),
        make_sample(
            "jhu-2",
            "JHU-CROWD++ v2.0",
        ),
        make_sample(
            "jhu-3",
            "JHU-CROWD++ v2.0",
        ),
        make_sample(
            "jhu-4",
            "JHU-CROWD++ v2.0",
        ),
        make_sample(
            "team-1",
            "TEAM_CONSENTED",
        ),
    ]

    weights = build_source_balanced_sample_weights(
        samples,
        team_sampling_ratio=0.3,
    )

    assert len(weights) == 5
    assert sum(weights) == pytest.approx(1.0)

    assert sum(weights[:4]) == pytest.approx(0.7)
    assert weights[0] == pytest.approx(0.7 / 4)
    assert weights[4] == pytest.approx(0.3)


def test_build_source_balanced_sampler_uses_replacement() -> None:
    samples = [
        make_sample(
            "jhu-1",
            "JHU-CROWD++ v2.0",
        ),
        make_sample(
            "jhu-2",
            "JHU-CROWD++ v2.0",
        ),
        make_sample(
            "team-1",
            "TEAM_CONSENTED",
        ),
    ]

    sampler = build_source_balanced_sampler(
        samples,
        team_sampling_ratio=0.3,
        samples_per_epoch=12,
        random_seed=42,
    )

    assert isinstance(
        sampler,
        WeightedRandomSampler,
    )
    assert sampler.num_samples == 12
    assert sampler.replacement is True


def test_build_p2pnet_training_arguments() -> None:
    arguments = build_p2pnet_training_arguments()

    assert arguments.backbone == "vgg16_bn"
    assert arguments.row == 2
    assert arguments.line == 2
    assert arguments.set_cost_class == 1.0
    assert arguments.set_cost_point == 0.05
    assert arguments.point_loss_coef == 0.0002
    assert arguments.eos_coef == 0.5


def test_load_p2pnet_training_components(
    tmp_path: Path,
    monkeypatch,
) -> None:
    weight_path = tmp_path / "initial.pth"

    source_model = nn.Linear(
        2,
        1,
    )

    with torch.no_grad():
        source_model.weight.fill_(0.25)
        source_model.bias.fill_(0.5)

    torch.save(
        {
            "model": (source_model.state_dict()),
        },
        weight_path,
    )

    criterion = nn.Identity()
    captured_arguments: dict[
        str,
        object,
    ] = {}

    def fake_build_model(
        arguments,
        training: bool = False,
    ):
        captured_arguments["arguments"] = arguments
        captured_arguments["training"] = training

        return (
            nn.Linear(
                2,
                1,
            ),
            criterion,
        )

    monkeypatch.setattr(
        p2pnet_finetuning,
        "_import_build_model",
        lambda _: fake_build_model,
        raising=False,
    )

    (
        model,
        loaded_criterion,
        device_object,
    ) = load_p2pnet_training_components(
        p2pnet_root=tmp_path,
        weight_path=weight_path,
        device="cpu",
    )

    assert captured_arguments["training"] is True
    assert captured_arguments["arguments"].backbone == "vgg16_bn"

    assert device_object.type == "cpu"
    assert model.training is True
    assert loaded_criterion is criterion

    assert torch.equal(
        model.weight,
        source_model.weight,
    )
    assert torch.equal(
        model.bias,
        source_model.bias,
    )


def test_build_p2pnet_optimizer_uses_lower_backbone_learning_rate() -> None:
    class DummyP2PNet(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.backbone = nn.Linear(
                2,
                2,
            )
            self.head = nn.Linear(
                2,
                1,
            )

    model = DummyP2PNet()

    optimizer = build_p2pnet_optimizer(
        model,
        learning_rate=1e-5,
        backbone_learning_rate=1e-6,
        weight_decay=1e-4,
    )

    assert isinstance(
        optimizer,
        torch.optim.AdamW,
    )
    assert len(optimizer.param_groups) == 2

    assert optimizer.param_groups[0]["lr"] == (1e-5)
    assert optimizer.param_groups[1]["lr"] == (1e-6)

    head_parameter_ids = {id(parameter) for parameter in model.head.parameters()}
    backbone_parameter_ids = {
        id(parameter) for parameter in model.backbone.parameters()
    }

    assert {
        id(parameter) for parameter in optimizer.param_groups[0]["params"]
    } == head_parameter_ids

    assert {
        id(parameter) for parameter in optimizer.param_groups[1]["params"]
    } == backbone_parameter_ids


def test_collate_p2pnet_samples_flattens_training_patches() -> None:
    first_images = torch.zeros(
        (
            2,
            3,
            128,
            128,
        )
    )
    second_images = torch.ones(
        (
            2,
            3,
            128,
            128,
        )
    )

    first_targets = [
        {
            "point": torch.empty(
                (
                    0,
                    2,
                )
            ),
            "labels": torch.empty(
                0,
                dtype=torch.long,
            ),
        },
        {
            "point": torch.tensor(
                [
                    [64.0, 64.0],
                ]
            ),
            "labels": torch.ones(
                1,
                dtype=torch.long,
            ),
        },
    ]
    second_targets = [
        {
            "point": torch.tensor(
                [
                    [32.0, 32.0],
                ]
            ),
            "labels": torch.ones(
                1,
                dtype=torch.long,
            ),
        },
        {
            "point": torch.empty(
                (
                    0,
                    2,
                )
            ),
            "labels": torch.empty(
                0,
                dtype=torch.long,
            ),
        },
    ]

    images, targets = collate_p2pnet_samples(
        [
            (
                first_images,
                first_targets,
            ),
            (
                second_images,
                second_targets,
            ),
        ]
    )

    assert images.shape == (
        4,
        3,
        128,
        128,
    )
    assert len(targets) == 4
    assert targets[1]["point"].tolist() == [
        [64.0, 64.0],
    ]
    assert targets[2]["point"].tolist() == [
        [32.0, 32.0],
    ]


def test_train_p2pnet_batch_updates_model_parameter() -> None:
    class DummyModel(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.scale = nn.Parameter(
                torch.tensor(1.0),
            )

        def forward(
            self,
            images: torch.Tensor,
        ) -> dict[str, torch.Tensor]:
            return {
                "prediction": (images.mean() * self.scale),
            }

    class DummyCriterion(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.weight_dict = {
                "loss_value": 1.0,
            }

        def forward(
            self,
            outputs,
            targets,
        ) -> dict[str, torch.Tensor]:
            return {
                "loss_value": (outputs["prediction"] ** 2),
            }

    model = DummyModel()
    criterion = DummyCriterion()
    optimizer = torch.optim.SGD(
        model.parameters(),
        lr=0.1,
    )

    images = torch.ones(
        (
            2,
            3,
            128,
            128,
        )
    )
    targets = [
        {
            "point": torch.empty(
                (
                    0,
                    2,
                )
            ),
            "labels": torch.empty(
                0,
                dtype=torch.long,
            ),
        },
        {
            "point": torch.empty(
                (
                    0,
                    2,
                )
            ),
            "labels": torch.empty(
                0,
                dtype=torch.long,
            ),
        },
    ]

    previous_scale = float(
        model.scale.detach(),
    )

    metrics = train_p2pnet_batch(
        model=model,
        criterion=criterion,
        optimizer=optimizer,
        images=images,
        targets=targets,
        device=torch.device("cpu"),
        max_grad_norm=0.1,
    )

    assert metrics["totalLoss"] == (pytest.approx(1.0))
    assert metrics["loss_value"] == (pytest.approx(1.0))
    assert (
        float(
            model.scale.detach(),
        )
        < previous_scale
    )


def test_build_p2pnet_training_data_loader_uses_balanced_sampler() -> None:
    class DummyDataset:
        def __init__(self) -> None:
            self.samples = [
                make_sample(
                    "jhu-1",
                    "JHU-CROWD++ v2.0",
                ),
                make_sample(
                    "jhu-2",
                    "JHU-CROWD++ v2.0",
                ),
                make_sample(
                    "team-1",
                    "TEAM_CONSENTED",
                ),
            ]

        def __len__(self) -> int:
            return len(
                self.samples,
            )

        def __getitem__(
            self,
            index: int,
        ):
            images = torch.full(
                (
                    1,
                    3,
                    8,
                    8,
                ),
                float(index),
            )
            targets = [
                {
                    "point": torch.empty(
                        (
                            0,
                            2,
                        )
                    ),
                    "labels": torch.empty(
                        0,
                        dtype=torch.long,
                    ),
                },
            ]

            return (
                images,
                targets,
            )

    dataset = DummyDataset()

    data_loader = build_p2pnet_training_data_loader(
        dataset,
        batch_size=2,
        team_sampling_ratio=0.3,
        samples_per_epoch=6,
        random_seed=42,
        num_workers=0,
    )

    assert isinstance(
        data_loader.sampler,
        WeightedRandomSampler,
    )
    assert len(data_loader.sampler) == 6
    assert data_loader.batch_size == 2

    images, targets = next(
        iter(data_loader),
    )

    assert images.shape == (
        2,
        3,
        8,
        8,
    )
    assert len(targets) == 2


def test_train_p2pnet_epoch_summarizes_batches(
    monkeypatch,
) -> None:
    batch_metrics = iter(
        [
            {
                "totalLoss": 3.0,
                "loss_ce": 2.0,
                "loss_points": 1.0,
            },
            {
                "totalLoss": 1.0,
                "loss_ce": 0.5,
                "loss_points": 0.5,
            },
        ]
    )

    monkeypatch.setattr(
        p2pnet_finetuning,
        "train_p2pnet_batch",
        lambda **_: next(
            batch_metrics,
        ),
    )

    data_loader = [
        (
            torch.zeros(
                (
                    1,
                    3,
                    128,
                    128,
                )
            ),
            [],
        ),
        (
            torch.ones(
                (
                    1,
                    3,
                    128,
                    128,
                )
            ),
            [],
        ),
    ]

    summary = train_p2pnet_epoch(
        model=nn.Identity(),
        criterion=nn.Identity(),
        optimizer=object(),
        data_loader=data_loader,
        device=torch.device("cpu"),
        max_grad_norm=0.1,
    )

    assert isinstance(
        summary,
        P2PNetEpochSummary,
    )
    assert summary.batch_count == 2
    assert summary.mean_total_loss == (pytest.approx(2.0))
    assert summary.mean_losses == {
        "loss_ce": pytest.approx(1.25),
        "loss_points": pytest.approx(0.75),
    }


def test_save_p2pnet_checkpoint(
    tmp_path: Path,
) -> None:
    model = nn.Linear(
        2,
        1,
    )
    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=1e-5,
    )
    summary = P2PNetEpochSummary(
        batch_count=3,
        mean_total_loss=1.25,
        mean_losses={
            "loss_ce": 1.0,
            "loss_points": 0.25,
        },
    )

    output_path = tmp_path / "checkpoints" / "epoch-2.pth"

    save_p2pnet_checkpoint(
        output_path=output_path,
        model=model,
        optimizer=optimizer,
        epoch=2,
        summary=summary,
        model_version="safy-p2pnet-v1",
        base_weight_path=Path(
            "weights/SHTechA.pth",
        ),
    )

    checkpoint = torch.load(
        output_path,
        map_location="cpu",
        weights_only=False,
    )

    assert checkpoint["epoch"] == 2
    assert checkpoint["modelVersion"] == ("safy-p2pnet-v1")
    assert checkpoint["baseWeightPath"] == ("weights/SHTechA.pth")
    assert checkpoint["trainingSummary"] == {
        "batchCount": 3,
        "meanTotalLoss": 1.25,
        "meanLosses": {
            "loss_ce": 1.0,
            "loss_points": 0.25,
        },
    }
    assert "model" in checkpoint
    assert "optimizer" in checkpoint

    model_only_path = tmp_path / "checkpoints" / "model-only.pth"
    save_p2pnet_checkpoint(
        output_path=model_only_path,
        model=model,
        optimizer=optimizer,
        epoch=2,
        summary=summary,
        model_version="safy-p2pnet-v1",
        base_weight_path=Path("weights/SHTechA.pth"),
        include_optimizer=False,
    )

    model_only_checkpoint = torch.load(
        model_only_path,
        map_location="cpu",
        weights_only=False,
    )

    assert "model" in model_only_checkpoint
    assert "optimizer" not in model_only_checkpoint


def test_evaluate_p2pnet_count_returns_mae_and_rmse() -> None:
    class FixedPredictionModel(nn.Module):
        def forward(
            self,
            images: torch.Tensor,
        ) -> dict[str, torch.Tensor]:
            batch_size = images.shape[0]

            logits = torch.tensor(
                [
                    [
                        [0.0, 5.0],
                        [0.0, 5.0],
                        [5.0, 0.0],
                    ],
                ],
                device=images.device,
            ).repeat(
                batch_size,
                1,
                1,
            )

            return {
                "pred_logits": logits,
                "pred_points": torch.zeros(
                    (
                        batch_size,
                        3,
                        2,
                    ),
                    device=images.device,
                ),
            }

    images = torch.zeros(
        (
            2,
            3,
            128,
            128,
        )
    )
    targets = [
        {
            "point": torch.tensor(
                [
                    [64.0, 64.0],
                ]
            ),
            "labels": torch.ones(
                1,
                dtype=torch.long,
            ),
        },
        {
            "point": torch.tensor(
                [
                    [32.0, 32.0],
                    [64.0, 64.0],
                    [96.0, 96.0],
                ]
            ),
            "labels": torch.ones(
                3,
                dtype=torch.long,
            ),
        },
    ]

    summary = evaluate_p2pnet_count(
        model=FixedPredictionModel(),
        data_loader=[
            (
                images,
                targets,
            ),
        ],
        device=torch.device("cpu"),
        threshold=0.5,
    )

    assert isinstance(
        summary,
        P2PNetValidationSummary,
    )
    assert summary.sample_count == 2
    assert summary.mean_absolute_error == (pytest.approx(1.0))
    assert summary.root_mean_squared_error == (pytest.approx(1.0))


def test_build_p2pnet_validation_data_loader_preserves_order() -> None:
    class DummyValidationDataset:
        def __len__(self) -> int:
            return 2

        def __getitem__(
            self,
            index: int,
        ):
            image = torch.full(
                (
                    3,
                    128,
                    128,
                ),
                float(index),
            )
            target = {
                "point": torch.empty(
                    (
                        0,
                        2,
                    )
                ),
                "labels": torch.empty(
                    0,
                    dtype=torch.long,
                ),
            }

            return (
                image,
                [target],
            )

    data_loader = build_p2pnet_validation_data_loader(
        DummyValidationDataset(),
        num_workers=0,
    )

    assert isinstance(
        data_loader.sampler,
        SequentialSampler,
    )
    assert data_loader.batch_size == 1

    batches = list(
        data_loader,
    )

    assert len(batches) == 2
    assert float(
        batches[0][0].mean(),
    ) == pytest.approx(0.0)
    assert float(
        batches[1][0].mean(),
    ) == pytest.approx(1.0)


def test_normalize_p2pnet_loss_weights_fixes_point_loss_key() -> None:
    class DummyCriterion:
        def __init__(self) -> None:
            self.weight_dict = {
                "loss_ce": 1.0,
                "loss_points": 0.0002,
            }

    criterion = DummyCriterion()

    normalize_p2pnet_loss_weights(
        criterion,
    )

    assert criterion.weight_dict == {
        "loss_ce": 1.0,
        "loss_point": 0.0002,
    }
