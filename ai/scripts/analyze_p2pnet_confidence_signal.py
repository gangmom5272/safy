import argparse
import json
import random
from dataclasses import asdict
from pathlib import Path

import torch

from app.services.head_point_dataset import load_head_point_manifest
from app.services.p2pnet_benchmark_adapter import load_p2pnet_detector
from app.services.p2pnet_confidence_signal import (
    ConfidenceSummary,
    compare_groups,
    summarize_point_confidences,
)


LOW_DENSITY_SCENARIOS = (
    "EMPTY",
    "SPARSE",
    "NORMAL",
    "LOCAL_DENSE",
)


def select_comparison_sample_ids(
    *,
    report_path: Path,
    random_seed: int,
) -> tuple[list[str], list[str]]:
    report = json.loads(report_path.read_text(encoding="utf-8"))
    records = report["records"]

    flagged_records = [record for record in records if record["isLargeFalsePositive"]]
    flagged_ids = [record["sampleId"] for record in flagged_records]

    flagged_scenario_counts: dict[str, int] = {}
    for record in flagged_records:
        scenario = record["scenario"]
        flagged_scenario_counts[scenario] = flagged_scenario_counts.get(scenario, 0) + 1

    normal_ids_by_scenario: dict[str, list[str]] = {}
    for record in records:
        if record["isLargeFalsePositive"]:
            continue
        if record["scenario"] not in LOW_DENSITY_SCENARIOS:
            continue
        normal_ids_by_scenario.setdefault(record["scenario"], []).append(
            record["sampleId"]
        )

    random_generator = random.Random(random_seed)
    normal_ids: list[str] = []

    for scenario in sorted(flagged_scenario_counts):
        requested_count = flagged_scenario_counts[scenario]
        candidates = sorted(normal_ids_by_scenario.get(scenario, []))
        random_generator.shuffle(candidates)
        normal_ids.extend(candidates[: min(requested_count, len(candidates))])

    return flagged_ids, normal_ids


def _summarize_samples(
    *,
    sample_ids: list[str],
    samples_by_id: dict,
    detect,
) -> list[ConfidenceSummary]:
    summaries = []

    for sample_id in sample_ids:
        sample = samples_by_id[sample_id]
        result = detect(sample.image_path.read_bytes())
        summaries.append(
            summarize_point_confidences(
                sample_id=sample_id,
                confidences=result.confidences,
            )
        )

    return summaries


def run_analysis(
    *,
    manifest_path: Path,
    report_path: Path,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
    threshold: float,
    random_seed: int,
    output_path: Path,
) -> dict:
    flagged_ids, normal_ids = select_comparison_sample_ids(
        report_path=report_path,
        random_seed=random_seed,
    )

    samples = load_head_point_manifest(manifest_path)
    samples_by_id = {sample.sample_id: sample for sample in samples}

    detect = load_p2pnet_detector(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
        threshold=threshold,
    )

    flagged_summaries = _summarize_samples(
        sample_ids=flagged_ids,
        samples_by_id=samples_by_id,
        detect=detect,
    )
    normal_summaries = _summarize_samples(
        sample_ids=normal_ids,
        samples_by_id=samples_by_id,
        detect=detect,
    )

    comparison = compare_groups(
        flagged_summaries=flagged_summaries,
        normal_summaries=normal_summaries,
    )

    report_payload = {
        "flaggedSampleIds": flagged_ids,
        "normalSampleIds": normal_ids,
        "flaggedSummaries": [asdict(summary) for summary in flagged_summaries],
        "normalSummaries": [asdict(summary) for summary in normal_summaries],
        "comparison": asdict(comparison),
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(report_payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    return report_payload


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "138번 홀드아웃에서 대형 오탐으로 판정된 표본과 정상 표본의 "
            "P2PNet 포인트별 confidence 분포를 비교한다."
        ),
    )
    parser.add_argument("manifest_path", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--p2pnet-root", type=Path, required=True)
    parser.add_argument("--weight-path", type=Path, required=True)
    parser.add_argument(
        "--device",
        default="cuda" if torch.cuda.is_available() else "cpu",
    )
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--random-seed", type=int, default=42)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    report_payload = run_analysis(
        manifest_path=arguments.manifest_path,
        report_path=arguments.report,
        p2pnet_root=arguments.p2pnet_root,
        weight_path=arguments.weight_path,
        device=arguments.device,
        threshold=arguments.threshold,
        random_seed=arguments.random_seed,
        output_path=arguments.output,
    )

    comparison = report_payload["comparison"]
    print(f"flaggedSampleCount: {comparison['flagged_sample_count']}")
    print(f"normalSampleCount: {comparison['normal_sample_count']}")
    print(f"flaggedMeanOfMeans: {comparison['flagged_mean_of_means']}")
    print(f"normalMeanOfMeans: {comparison['normal_mean_of_means']}")
    print(f"flaggedBandFractions: {comparison['flagged_band_fractions']}")
    print(f"normalBandFractions: {comparison['normal_band_fractions']}")
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
