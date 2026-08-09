import argparse
import json
from dataclasses import asdict
from pathlib import Path

import torch

from app.services.head_point_dataset import load_head_point_manifest
from app.services.p2pnet_benchmark_adapter import load_p2pnet_detector
from app.services.p2pnet_confidence_signal import (
    LOW_CONFIDENCE_BORDERLINE_FRACTION_THRESHOLD,
    determine_low_confidence_signal,
    summarize_point_confidences,
)


def collect_gate_signals(
    *,
    manifest_path: Path,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
    threshold: float,
    borderline_fraction_threshold: float,
    output_path: Path,
) -> dict:
    samples = load_head_point_manifest(manifest_path)

    detect = load_p2pnet_detector(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
        threshold=threshold,
    )

    records = []

    for sample in samples:
        result = detect(sample.image_path.read_bytes())
        summary = summarize_point_confidences(
            sample_id=sample.sample_id,
            confidences=result.confidences,
        )

        records.append(
            {
                "sampleId": sample.sample_id,
                "scenario": sample.scenario,
                "groundTruthCount": sample.person_count,
                "p2pnetCount": result.person_count,
                "lowConfidenceSignal": determine_low_confidence_signal(
                    confidences=result.confidences,
                    threshold=borderline_fraction_threshold,
                ),
                "confidenceSummary": asdict(summary),
            }
        )

    payload = {
        "manifestPath": manifest_path.as_posix(),
        "weightPath": weight_path.as_posix(),
        "device": device,
        "pointThreshold": threshold,
        "borderlineFractionThreshold": (borderline_fraction_threshold),
        "sampleCount": len(records),
        "lowConfidenceSignalCount": sum(
            1 for record in records if record["lowConfidenceSignal"]
        ),
        "records": records,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    return payload


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "홀드아웃 전체에 P2PNet을 실행해 표본별 예측 인원수와 "
            "140번 저밀도 게이트 신호를 저장한다."
        ),
    )
    parser.add_argument("manifest_path", type=Path)
    parser.add_argument("--p2pnet-root", type=Path, required=True)
    parser.add_argument("--weight-path", type=Path, required=True)
    parser.add_argument(
        "--device",
        default="cuda" if torch.cuda.is_available() else "cpu",
    )
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument(
        "--borderline-fraction-threshold",
        type=float,
        default=LOW_CONFIDENCE_BORDERLINE_FRACTION_THRESHOLD,
    )
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    payload = collect_gate_signals(
        manifest_path=arguments.manifest_path,
        p2pnet_root=arguments.p2pnet_root,
        weight_path=arguments.weight_path,
        device=arguments.device,
        threshold=arguments.threshold,
        borderline_fraction_threshold=(arguments.borderline_fraction_threshold),
        output_path=arguments.output,
    )

    print(f"sampleCount: {payload['sampleCount']}")
    print(f"lowConfidenceSignalCount: {payload['lowConfidenceSignalCount']}")
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
