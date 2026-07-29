import argparse
import json
from pathlib import Path

import torch
from torch.utils.data import Subset

from app.services.p2pnet_finetune_dataset import (
    P2PNetManifestDataset,
)
from app.services.p2pnet_finetuning import (
    build_p2pnet_optimizer,
    build_p2pnet_training_data_loader,
    build_p2pnet_validation_data_loader,
    evaluate_p2pnet_count,
    load_p2pnet_training_components,
    save_p2pnet_checkpoint,
    train_p2pnet_epoch,
)


MODEL_VERSION = "safy-p2pnet-v1"


def select_validation_dataset(
    dataset,
    *,
    validation_source: str | None = None,
    validation_limit: int | None = None,
):
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
            "검증 데이터셋에 samples 목록이 필요합니다.",
        )

    selected_indices = [
        index
        for index, sample in enumerate(samples)
        if (validation_source is None or sample.source == validation_source)
    ]

    if not selected_indices:
        raise ValueError(
            "조건에 맞는 검증 표본이 없습니다.",
        )

    if validation_limit is not None:
        if validation_limit <= 0:
            raise ValueError(
                "검증 샘플 제한은 1 이상이어야 합니다.",
            )

        selected_indices = selected_indices[:validation_limit]

    return Subset(
        dataset,
        selected_indices,
    )


def run_training(
    *,
    manifest_path: Path,
    p2pnet_root: Path,
    weight_path: Path,
    output_directory: Path,
    device: str,
    epochs: int = 10,
    batch_size: int = 1,
    patch_size: int = 128,
    patch_count: int = 4,
    team_sampling_ratio: float = 0.3,
    samples_per_epoch: int | None = None,
    validation_source: str | None = None,
    validation_limit: int | None = None,
    learning_rate: float = 1e-5,
    backbone_learning_rate: float = 1e-6,
    weight_decay: float = 1e-4,
    max_grad_norm: float = 0.1,
    threshold: float = 0.5,
    random_seed: int = 42,
    num_workers: int = 0,
) -> dict:
    if epochs <= 0:
        raise ValueError(
            "학습 에포크는 1 이상이어야 합니다.",
        )

    if validation_limit is not None and validation_limit <= 0:
        raise ValueError(
            "검증 샘플 제한은 1 이상이어야 합니다.",
        )

    output_directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    training_dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="TRAIN",
        training=True,
        patch_size=patch_size,
        patch_count=patch_count,
        random_seed=random_seed,
    )
    validation_dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="VALIDATION",
        training=False,
        random_seed=random_seed,
    )

    validation_data_source = select_validation_dataset(
        validation_dataset,
        validation_source=(validation_source),
        validation_limit=(validation_limit),
    )

    (
        model,
        criterion,
        device_object,
    ) = load_p2pnet_training_components(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
    )

    optimizer = build_p2pnet_optimizer(
        model,
        learning_rate=learning_rate,
        backbone_learning_rate=(backbone_learning_rate),
        weight_decay=weight_decay,
    )

    training_data_loader = build_p2pnet_training_data_loader(
        training_dataset,
        batch_size=batch_size,
        team_sampling_ratio=(team_sampling_ratio),
        samples_per_epoch=(samples_per_epoch),
        random_seed=random_seed,
        num_workers=num_workers,
    )
    validation_data_loader = build_p2pnet_validation_data_loader(
        validation_data_source,
        num_workers=num_workers,
    )

    baseline_validation_summary = evaluate_p2pnet_count(
        model=model,
        data_loader=(validation_data_loader),
        device=device_object,
        threshold=threshold,
    )

    report: dict = {
        "modelName": "P2PNet",
        "modelVersion": MODEL_VERSION,
        "baseWeightPath": (weight_path.as_posix()),
        "manifestPath": (manifest_path.as_posix()),
        "device": device_object.type,
        "configuration": {
            "epochs": epochs,
            "batchSize": batch_size,
            "patchSize": patch_size,
            "patchCount": patch_count,
            "teamSamplingRatio": (team_sampling_ratio),
            "samplesPerEpoch": (samples_per_epoch),
            "validationSource": (validation_source),
            "validationLimit": (validation_limit),
            "learningRate": (learning_rate),
            "backboneLearningRate": (backbone_learning_rate),
            "weightDecay": weight_decay,
            "maxGradNorm": max_grad_norm,
            "threshold": threshold,
            "randomSeed": random_seed,
        },
        "baselineValidation": {
            "sampleCount": (baseline_validation_summary.sample_count),
            "meanAbsoluteError": (baseline_validation_summary.mean_absolute_error),
            "rootMeanSquaredError": (
                baseline_validation_summary.root_mean_squared_error
            ),
        },
        "baselineValidationMae": (baseline_validation_summary.mean_absolute_error),
        "epochs": [],
        "bestEpoch": None,
        "bestValidationMae": None,
        "bestMaeImprovement": None,
    }

    best_validation_mae = float("inf")

    for epoch in range(
        1,
        epochs + 1,
    ):
        training_summary = train_p2pnet_epoch(
            model=model,
            criterion=criterion,
            optimizer=optimizer,
            data_loader=(training_data_loader),
            device=device_object,
            max_grad_norm=max_grad_norm,
        )
        validation_summary = evaluate_p2pnet_count(
            model=model,
            data_loader=(validation_data_loader),
            device=device_object,
            threshold=threshold,
        )

        epoch_record = {
            "epoch": epoch,
            "training": {
                "batchCount": (training_summary.batch_count),
                "meanTotalLoss": (training_summary.mean_total_loss),
                "meanLosses": (training_summary.mean_losses),
            },
            "validation": {
                "sampleCount": (validation_summary.sample_count),
                "meanAbsoluteError": (validation_summary.mean_absolute_error),
                "rootMeanSquaredError": (validation_summary.root_mean_squared_error),
            },
        }
        report["epochs"].append(
            epoch_record,
        )

        save_p2pnet_checkpoint(
            output_path=(output_directory / "last.pth"),
            model=model,
            optimizer=optimizer,
            epoch=epoch,
            summary=training_summary,
            model_version=MODEL_VERSION,
            base_weight_path=weight_path,
        )

        save_p2pnet_checkpoint(
            output_path=(output_directory / f"epoch-{epoch:03d}.pth"),
            model=model,
            optimizer=optimizer,
            epoch=epoch,
            summary=training_summary,
            model_version=MODEL_VERSION,
            base_weight_path=weight_path,
            include_optimizer=False,
        )

        if validation_summary.mean_absolute_error < best_validation_mae:
            best_validation_mae = validation_summary.mean_absolute_error
            report["bestEpoch"] = epoch
            report["bestValidationMae"] = best_validation_mae
            report["bestMaeImprovement"] = (
                baseline_validation_summary.mean_absolute_error - best_validation_mae
            )

            save_p2pnet_checkpoint(
                output_path=(output_directory / "best.pth"),
                model=model,
                optimizer=optimizer,
                epoch=epoch,
                summary=training_summary,
                model_version=(MODEL_VERSION),
                base_weight_path=(weight_path),
            )

        print(
            f"epoch: {epoch} "
            f"trainingLoss: "
            f"{training_summary.mean_total_loss:.6f} "
            f"validationMAE: "
            f"{validation_summary.mean_absolute_error:.2f}"
        )

    report_path = output_directory / "training-report.json"
    report_path.write_text(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    return report


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("SAFY 머리 좌표 데이터셋으로 P2PNet을 미세 조정합니다."),
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
    )
    parser.add_argument(
        "--p2pnet-root",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--weight-path",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--device",
        default=("cuda" if torch.cuda.is_available() else "cpu"),
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=10,
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=1,
    )
    parser.add_argument(
        "--patch-size",
        type=int,
        default=128,
    )
    parser.add_argument(
        "--patch-count",
        type=int,
        default=4,
    )
    parser.add_argument(
        "--team-sampling-ratio",
        type=float,
        default=0.3,
    )
    parser.add_argument(
        "--samples-per-epoch",
        type=int,
    )
    parser.add_argument(
        "--validation-source",
    )
    parser.add_argument(
        "--validation-limit",
        type=int,
    )
    parser.add_argument(
        "--learning-rate",
        type=float,
        default=1e-5,
    )
    parser.add_argument(
        "--backbone-learning-rate",
        type=float,
        default=1e-6,
    )
    parser.add_argument(
        "--weight-decay",
        type=float,
        default=1e-4,
    )
    parser.add_argument(
        "--max-grad-norm",
        type=float,
        default=0.1,
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.5,
    )
    parser.add_argument(
        "--random-seed",
        type=int,
        default=42,
    )
    parser.add_argument(
        "--num-workers",
        type=int,
        default=0,
    )

    arguments = parser.parse_args()

    report = run_training(
        manifest_path=(arguments.manifest_path),
        p2pnet_root=(arguments.p2pnet_root),
        weight_path=(arguments.weight_path),
        output_directory=(arguments.output),
        device=arguments.device,
        epochs=arguments.epochs,
        batch_size=arguments.batch_size,
        patch_size=arguments.patch_size,
        patch_count=arguments.patch_count,
        team_sampling_ratio=(arguments.team_sampling_ratio),
        samples_per_epoch=(arguments.samples_per_epoch),
        validation_source=(arguments.validation_source),
        validation_limit=(arguments.validation_limit),
        learning_rate=(arguments.learning_rate),
        backbone_learning_rate=(arguments.backbone_learning_rate),
        weight_decay=(arguments.weight_decay),
        max_grad_norm=(arguments.max_grad_norm),
        threshold=arguments.threshold,
        random_seed=(arguments.random_seed),
        num_workers=(arguments.num_workers),
    )

    print(f"bestEpoch: {report['bestEpoch']}")
    print(f"bestValidationMae: {report['bestValidationMae']}")
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
