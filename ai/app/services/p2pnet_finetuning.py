from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace

import torch
from torch.utils.data import (
    DataLoader,
    Dataset,
    WeightedRandomSampler,
)

from app.services.head_point_dataset import (
    HeadPointSample,
)
from app.services.p2pnet_benchmark_adapter import (
    _import_build_model,
)


TEAM_CONSENTED_SOURCE = "TEAM_CONSENTED"
DEFAULT_TEAM_SAMPLING_RATIO = 0.3


@dataclass(frozen=True)
class P2PNetEpochSummary:
    batch_count: int
    mean_total_loss: float
    mean_losses: dict[str, float]


@dataclass(frozen=True)
class P2PNetValidationSummary:
    sample_count: int
    mean_absolute_error: float
    root_mean_squared_error: float


def build_p2pnet_training_arguments() -> SimpleNamespace:
    return SimpleNamespace(
        backbone="vgg16_bn",
        row=2,
        line=2,
        set_cost_class=1.0,
        set_cost_point=0.05,
        point_loss_coef=0.0002,
        eos_coef=0.5,
    )


def normalize_p2pnet_loss_weights(
    criterion,
) -> None:
    weight_dict = getattr(
        criterion,
        "weight_dict",
        None,
    )

    if not isinstance(
        weight_dict,
        dict,
    ):
        return

    plural_weight = weight_dict.pop(
        "loss_points",
        None,
    )

    if plural_weight is None:
        return

    if "loss_point" in weight_dict and weight_dict["loss_point"] != plural_weight:
        raise ValueError(
            "P2PNet 좌표 손실 가중치가 서로 일치하지 않습니다.",
        )

    weight_dict["loss_point"] = plural_weight


def load_p2pnet_training_components(
    *,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
):
    device_object = torch.device(
        device,
    )
    build_model = _import_build_model(
        p2pnet_root,
    )
    arguments = build_p2pnet_training_arguments()

    model, criterion = build_model(
        arguments,
        training=True,
    )

    normalize_p2pnet_loss_weights(
        criterion,
    )

    checkpoint = torch.load(
        weight_path,
        map_location="cpu",
        weights_only=False,
    )

    if not isinstance(checkpoint, dict) or "model" not in checkpoint:
        raise ValueError(
            "P2PNet 가중치에 model 항목이 필요합니다.",
        )

    model.load_state_dict(
        checkpoint["model"],
    )

    model.to(
        device_object,
    )
    criterion.to(
        device_object,
    )

    model.train()
    criterion.train()

    return (
        model,
        criterion,
        device_object,
    )


def build_p2pnet_optimizer(
    model: torch.nn.Module,
    *,
    learning_rate: float = 1e-5,
    backbone_learning_rate: float = 1e-6,
    weight_decay: float = 1e-4,
) -> torch.optim.AdamW:
    if learning_rate <= 0:
        raise ValueError(
            "학습률은 양수여야 합니다.",
        )

    if backbone_learning_rate <= 0:
        raise ValueError(
            "백본 학습률은 양수여야 합니다.",
        )

    if weight_decay < 0:
        raise ValueError(
            "가중치 감쇠는 음수일 수 없습니다.",
        )

    head_parameters = [
        parameter
        for name, parameter in model.named_parameters()
        if (parameter.requires_grad and not name.startswith("backbone."))
    ]
    backbone_parameters = [
        parameter
        for name, parameter in model.named_parameters()
        if (parameter.requires_grad and name.startswith("backbone."))
    ]

    if not head_parameters:
        raise ValueError(
            "학습할 P2PNet 헤드 파라미터가 없습니다.",
        )

    parameter_groups: list[dict] = [
        {
            "params": head_parameters,
            "lr": learning_rate,
        },
    ]

    if backbone_parameters:
        parameter_groups.append(
            {
                "params": backbone_parameters,
                "lr": backbone_learning_rate,
            }
        )

    return torch.optim.AdamW(
        parameter_groups,
        lr=learning_rate,
        weight_decay=weight_decay,
    )


def collate_p2pnet_samples(
    batch: list[
        tuple[
            torch.Tensor,
            list[
                dict[
                    str,
                    torch.Tensor,
                ]
            ],
        ]
    ],
) -> tuple[
    torch.Tensor,
    list[
        dict[
            str,
            torch.Tensor,
        ]
    ],
]:
    if not batch:
        raise ValueError(
            "P2PNet 학습 배치는 비어 있을 수 없습니다.",
        )

    image_batches: list[torch.Tensor] = []
    flattened_targets: list[
        dict[
            str,
            torch.Tensor,
        ]
    ] = []

    for images, targets in batch:
        if images.ndim == 3:
            images = images.unsqueeze(0)

        if images.ndim != 4:
            raise ValueError(
                "P2PNet 이미지는 3차원 또는 4차원 텐서여야 합니다.",
            )

        if images.shape[0] != len(targets):
            raise ValueError(
                "이미지 수와 정답 정보 수가 일치해야 합니다.",
            )

        image_batches.append(
            images,
        )
        flattened_targets.extend(
            targets,
        )

    return (
        torch.cat(
            image_batches,
            dim=0,
        ),
        flattened_targets,
    )


def train_p2pnet_batch(
    *,
    model: torch.nn.Module,
    criterion: torch.nn.Module,
    optimizer: torch.optim.Optimizer,
    images: torch.Tensor,
    targets: list[
        dict[
            str,
            torch.Tensor,
        ]
    ],
    device: torch.device,
    max_grad_norm: float = 0.1,
) -> dict[str, float]:
    if max_grad_norm < 0:
        raise ValueError(
            "최대 그래디언트 크기는 음수일 수 없습니다.",
        )

    model.train()
    criterion.train()

    images = images.to(
        device,
    )
    device_targets = [
        {key: value.to(device) for key, value in target.items()} for target in targets
    ]

    outputs = model(
        images,
    )
    loss_dict = criterion(
        outputs,
        device_targets,
    )

    weight_dict = getattr(
        criterion,
        "weight_dict",
        None,
    )

    if not isinstance(
        weight_dict,
        dict,
    ):
        raise ValueError(
            "P2PNet 손실함수에 weight_dict가 필요합니다.",
        )

    weighted_losses = [
        loss_value * weight_dict[loss_name]
        for loss_name, loss_value in loss_dict.items()
        if loss_name in weight_dict
    ]

    if not weighted_losses:
        raise ValueError(
            "가중치를 적용할 P2PNet 손실값이 없습니다.",
        )

    total_loss = sum(
        weighted_losses,
    )

    if not bool(
        torch.isfinite(total_loss).item(),
    ):
        raise ValueError(
            "P2PNet 학습 손실값이 유효하지 않습니다.",
        )

    optimizer.zero_grad(
        set_to_none=True,
    )
    total_loss.backward()

    if max_grad_norm > 0:
        torch.nn.utils.clip_grad_norm_(
            model.parameters(),
            max_grad_norm,
        )

    optimizer.step()

    metrics = {
        loss_name: float(
            loss_value.detach().cpu().item(),
        )
        for loss_name, loss_value in loss_dict.items()
    }
    metrics["totalLoss"] = float(
        total_loss.detach().cpu().item(),
    )

    return metrics


def train_p2pnet_epoch(
    *,
    model: torch.nn.Module,
    criterion: torch.nn.Module,
    optimizer: torch.optim.Optimizer,
    data_loader: Iterable[
        tuple[
            torch.Tensor,
            list[
                dict[
                    str,
                    torch.Tensor,
                ]
            ],
        ]
    ],
    device: torch.device,
    max_grad_norm: float = 0.1,
) -> P2PNetEpochSummary:
    batch_count = 0
    total_loss_sum = 0.0
    loss_sums: dict[str, float] = {}
    loss_counts: dict[str, int] = {}

    for images, targets in data_loader:
        metrics = train_p2pnet_batch(
            model=model,
            criterion=criterion,
            optimizer=optimizer,
            images=images,
            targets=targets,
            device=device,
            max_grad_norm=max_grad_norm,
        )

        batch_count += 1
        total_loss_sum += metrics["totalLoss"]

        for loss_name, loss_value in metrics.items():
            if loss_name == "totalLoss":
                continue

            loss_sums[loss_name] = (
                loss_sums.get(
                    loss_name,
                    0.0,
                )
                + loss_value
            )
            loss_counts[loss_name] = (
                loss_counts.get(
                    loss_name,
                    0,
                )
                + 1
            )

    if batch_count == 0:
        raise ValueError(
            "P2PNet 학습 데이터 로더가 비어 있습니다.",
        )

    mean_losses = {
        loss_name: (loss_sum / loss_counts[loss_name])
        for loss_name, loss_sum in loss_sums.items()
    }

    return P2PNetEpochSummary(
        batch_count=batch_count,
        mean_total_loss=(total_loss_sum / batch_count),
        mean_losses=mean_losses,
    )


def evaluate_p2pnet_count(
    *,
    model: torch.nn.Module,
    data_loader: Iterable[
        tuple[
            torch.Tensor,
            list[
                dict[
                    str,
                    torch.Tensor,
                ]
            ],
        ]
    ],
    device: torch.device,
    threshold: float = 0.5,
) -> P2PNetValidationSummary:
    if not 0.0 < threshold < 1.0:
        raise ValueError(
            "P2PNet 임계값은 0과 1 사이여야 합니다.",
        )

    model.eval()

    sample_count = 0
    absolute_error_sum = 0.0
    squared_error_sum = 0.0

    with torch.inference_mode():
        for images, targets in data_loader:
            images = images.to(
                device,
            )

            if images.shape[0] != len(targets):
                raise ValueError(
                    "검증 이미지 수와 정답 정보 수가 일치해야 합니다.",
                )

            outputs = model(
                images,
            )
            scores = torch.nn.functional.softmax(
                outputs["pred_logits"],
                dim=-1,
            )[
                :,
                :,
                1,
            ]
            predicted_counts = (scores > threshold).sum(
                dim=1,
            )

            for predicted_count, target in zip(
                predicted_counts,
                targets,
                strict=True,
            ):
                ground_truth_count = int(
                    target["point"].shape[0],
                )
                error = (
                    int(
                        predicted_count.item(),
                    )
                    - ground_truth_count
                )

                sample_count += 1
                absolute_error_sum += abs(
                    error,
                )
                squared_error_sum += error * error

    if sample_count == 0:
        raise ValueError(
            "P2PNet 검증 데이터 로더가 비어 있습니다.",
        )

    return P2PNetValidationSummary(
        sample_count=sample_count,
        mean_absolute_error=(absolute_error_sum / sample_count),
        root_mean_squared_error=(squared_error_sum / sample_count) ** 0.5,
    )


def save_p2pnet_checkpoint(
    *,
    output_path: Path,
    model: torch.nn.Module,
    optimizer: torch.optim.Optimizer,
    epoch: int,
    summary: P2PNetEpochSummary,
    model_version: str,
    base_weight_path: Path,
    include_optimizer: bool = True,
) -> None:
    if epoch <= 0:
        raise ValueError(
            "체크포인트 에포크는 1 이상이어야 합니다.",
        )

    if not model_version.strip():
        raise ValueError(
            "모델 버전은 비어 있을 수 없습니다.",
        )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    checkpoint = {
        "epoch": epoch,
        "model": model.state_dict(),
        "modelVersion": model_version,
        "baseWeightPath": base_weight_path.as_posix(),
        "trainingSummary": {
            "batchCount": summary.batch_count,
            "meanTotalLoss": summary.mean_total_loss,
            "meanLosses": summary.mean_losses,
        },
    }

    if include_optimizer:
        checkpoint["optimizer"] = optimizer.state_dict()

    torch.save(
        checkpoint,
        output_path,
    )


def build_source_balanced_sample_weights(
    samples: list[HeadPointSample],
    team_sampling_ratio: float = (DEFAULT_TEAM_SAMPLING_RATIO),
) -> list[float]:
    if not samples:
        raise ValueError(
            "샘플 목록은 비어 있을 수 없습니다.",
        )

    if not 0.0 < team_sampling_ratio < 1.0:
        raise ValueError(
            "팀 데이터 샘플링 비율은 0과 1 사이여야 합니다.",
        )

    team_sample_count = sum(
        sample.source == TEAM_CONSENTED_SOURCE for sample in samples
    )
    external_sample_count = len(samples) - team_sample_count

    if team_sample_count == 0 or external_sample_count == 0:
        uniform_weight = 1.0 / len(samples)

        return [uniform_weight for _ in samples]

    team_sample_weight = team_sampling_ratio / team_sample_count
    external_sample_weight = (1.0 - team_sampling_ratio) / external_sample_count

    return [
        (
            team_sample_weight
            if sample.source == TEAM_CONSENTED_SOURCE
            else external_sample_weight
        )
        for sample in samples
    ]


def build_source_balanced_sampler(
    samples: list[HeadPointSample],
    team_sampling_ratio: float = (DEFAULT_TEAM_SAMPLING_RATIO),
    samples_per_epoch: int | None = None,
    random_seed: int = 42,
) -> WeightedRandomSampler:
    if samples_per_epoch is None:
        samples_per_epoch = len(samples)

    if samples_per_epoch <= 0:
        raise ValueError(
            "에포크당 샘플 수는 1 이상이어야 합니다.",
        )

    weights = build_source_balanced_sample_weights(
        samples,
        team_sampling_ratio=team_sampling_ratio,
    )

    generator = torch.Generator()
    generator.manual_seed(random_seed)

    return WeightedRandomSampler(
        weights=weights,
        num_samples=samples_per_epoch,
        replacement=True,
        generator=generator,
    )


def build_p2pnet_training_data_loader(
    dataset: Dataset,
    *,
    batch_size: int = 1,
    team_sampling_ratio: float = (DEFAULT_TEAM_SAMPLING_RATIO),
    samples_per_epoch: int | None = None,
    random_seed: int = 42,
    num_workers: int = 0,
) -> DataLoader:
    if batch_size <= 0:
        raise ValueError(
            "배치 크기는 1 이상이어야 합니다.",
        )

    if num_workers < 0:
        raise ValueError(
            "데이터 로더 작업자 수는 음수일 수 없습니다.",
        )

    samples = getattr(
        dataset,
        "samples",
        None,
    )

    if not isinstance(
        samples,
        list,
    ):
        raise ValueError(
            "P2PNet 데이터셋에 samples 목록이 필요합니다.",
        )

    sampler = build_source_balanced_sampler(
        samples,
        team_sampling_ratio=team_sampling_ratio,
        samples_per_epoch=samples_per_epoch,
        random_seed=random_seed,
    )

    return DataLoader(
        dataset,
        batch_size=batch_size,
        sampler=sampler,
        num_workers=num_workers,
        collate_fn=collate_p2pnet_samples,
        pin_memory=torch.cuda.is_available(),
    )


def build_p2pnet_validation_data_loader(
    dataset: Dataset,
    *,
    num_workers: int = 0,
) -> DataLoader:
    if num_workers < 0:
        raise ValueError(
            "데이터 로더 작업자 수는 음수일 수 없습니다.",
        )

    return DataLoader(
        dataset,
        batch_size=1,
        shuffle=False,
        num_workers=num_workers,
        collate_fn=collate_p2pnet_samples,
        pin_memory=torch.cuda.is_available(),
    )
