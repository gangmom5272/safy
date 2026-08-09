import argparse
import gc
import json
from pathlib import Path

import torch
from torch.utils.data import Subset

from app.services.p2pnet_benchmark_adapter import _load_model
from app.services.p2pnet_finetune_dataset import P2PNetManifestDataset
from app.services.p2pnet_finetuning import (
    build_p2pnet_validation_data_loader,
    evaluate_p2pnet_count,
)


def compare_checkpoints(
    *,
    manifest_path: Path,
    p2pnet_root: Path,
    baseline_weight_path: Path,
    checkpoint_directory: Path,
    output_path: Path,
    device: str,
    validation_source: str = "JHU-CROWD++ v2.0",
    threshold: float = 0.5,
) -> dict:
    training_report_path = checkpoint_directory / "training-report.json"
    training_report = json.loads(training_report_path.read_text(encoding="utf-8"))
    team_by_epoch = {
        record["epoch"]: record["validation"] for record in training_report["epochs"]
    }

    dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="VALIDATION",
        training=False,
    )
    selected_indices = [
        index
        for index, sample in enumerate(dataset.samples)
        if sample.source == validation_source
    ]

    if not selected_indices:
        raise ValueError("조건에 맞는 외부 검증 표본이 없습니다.")

    data_loader = build_p2pnet_validation_data_loader(
        Subset(dataset, selected_indices),
        num_workers=0,
    )
    checkpoint_paths = sorted(checkpoint_directory.glob("epoch-*.pth"))
    candidates = [
        ("baseline", baseline_weight_path, None),
        *[
            (
                checkpoint_path.stem,
                checkpoint_path,
                int(checkpoint_path.stem.removeprefix("epoch-")),
            )
            for checkpoint_path in checkpoint_paths
        ],
    ]

    records = []
    for name, weight_path, epoch in candidates:
        model, device_object, _ = _load_model(
            p2pnet_root=p2pnet_root,
            weight_path=weight_path,
            device=device,
        )
        summary = evaluate_p2pnet_count(
            model=model,
            data_loader=data_loader,
            device=device_object,
            threshold=threshold,
        )
        records.append(
            {
                "name": name,
                "weightPath": weight_path.as_posix(),
                "epoch": epoch,
                "teamValidation": (
                    training_report["baselineValidation"]
                    if epoch is None
                    else team_by_epoch[epoch]
                ),
                "externalValidation": {
                    "source": validation_source,
                    "sampleCount": summary.sample_count,
                    "meanAbsoluteError": summary.mean_absolute_error,
                    "rootMeanSquaredError": summary.root_mean_squared_error,
                },
            }
        )
        del model
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

    report = {"records": records}
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return report


def main() -> None:
    parser = argparse.ArgumentParser(
        description="P2PNet 미세 조정 에포크별 체크포인트를 외부 검증셋에서 비교합니다.",
    )
    parser.add_argument("manifest_path", type=Path)
    parser.add_argument("--p2pnet-root", type=Path, required=True)
    parser.add_argument("--baseline-weight-path", type=Path, required=True)
    parser.add_argument("--checkpoint-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--device", default="cuda" if torch.cuda.is_available() else "cpu"
    )
    parser.add_argument("--validation-source", default="JHU-CROWD++ v2.0")
    parser.add_argument("--threshold", type=float, default=0.5)
    arguments = parser.parse_args()

    report = compare_checkpoints(
        manifest_path=arguments.manifest_path,
        p2pnet_root=arguments.p2pnet_root,
        baseline_weight_path=arguments.baseline_weight_path,
        checkpoint_directory=arguments.checkpoint_directory,
        output_path=arguments.output,
        device=arguments.device,
        validation_source=arguments.validation_source,
        threshold=arguments.threshold,
    )

    for record in report["records"]:
        print(
            f"{record['name']}: "
            f"teamMAE={record['teamValidation']['meanAbsoluteError']:.2f} "
            f"externalMAE={record['externalValidation']['meanAbsoluteError']:.2f} "
            f"externalRMSE={record['externalValidation']['rootMeanSquaredError']:.2f}"
        )
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
