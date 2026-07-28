import csv
import json
from dataclasses import dataclass
from pathlib import Path

from app.services.congestion import (
    calculate_congestion,
)
from app.services.person_detector import detect_people


@dataclass(frozen=True)
class EvaluationSample:
    sample_id: str
    scenario: str
    image_path: Path
    ground_truth_count: int
    source: str
    license: str


@dataclass(frozen=True)
class EvaluationRecord:
    sample_id: str
    scenario: str
    ground_truth_count: int
    predicted_count: int
    visual_congestion_score: float
    local_peak_congestion_score: float
    processing_time_ms: int

    @property
    def absolute_error(self) -> int:
        return abs(self.ground_truth_count - self.predicted_count)


@dataclass(frozen=True)
class EvaluationSummary:
    sample_count: int
    total_ground_truth_count: int
    total_predicted_count: int
    mean_absolute_error: float
    mean_processing_time_ms: float


def summarize_evaluations(
    records: list[EvaluationRecord],
) -> EvaluationSummary:
    if not records:
        raise ValueError("평가 기록이 한 건 이상 필요합니다.")

    sample_count = len(records)

    total_ground_truth_count = sum(record.ground_truth_count for record in records)
    total_predicted_count = sum(record.predicted_count for record in records)
    total_absolute_error = sum(record.absolute_error for record in records)
    total_processing_time_ms = sum(record.processing_time_ms for record in records)

    return EvaluationSummary(
        sample_count=sample_count,
        total_ground_truth_count=(total_ground_truth_count),
        total_predicted_count=(total_predicted_count),
        mean_absolute_error=round(
            total_absolute_error / sample_count,
            2,
        ),
        mean_processing_time_ms=round(
            total_processing_time_ms / sample_count,
            2,
        ),
    )


def load_evaluation_manifest(
    manifest_path: Path,
) -> list[EvaluationSample]:
    samples: list[EvaluationSample] = []
    sample_ids: set[str] = set()

    with manifest_path.open(
        "r",
        encoding="utf-8",
        newline="",
    ) as manifest_file:
        rows = csv.DictReader(manifest_file)

        for row in rows:
            sample_id = row["sample_id"]

            if sample_id in sample_ids:
                raise ValueError("sample_id는 중복될 수 없습니다.")

            ground_truth_count = int(row["ground_truth_count"])

            if ground_truth_count < 0:
                raise ValueError("정답 인원수는 0 이상이어야 합니다.")

            samples.append(
                EvaluationSample(
                    sample_id=sample_id,
                    scenario=row["scenario"],
                    image_path=(manifest_path.parent / row["image_path"]),
                    ground_truth_count=(ground_truth_count),
                    source=row["source"],
                    license=row["license"],
                )
            )
            sample_ids.add(sample_id)

    return samples


def evaluate_sample(
    sample: EvaluationSample,
) -> EvaluationRecord:
    image_bytes = sample.image_path.read_bytes()

    detection_result = detect_people(image_bytes)

    congestion_result = calculate_congestion(
        detection_result.person_centers,
    )

    return EvaluationRecord(
        sample_id=sample.sample_id,
        scenario=sample.scenario,
        ground_truth_count=(sample.ground_truth_count),
        predicted_count=(detection_result.person_count),
        visual_congestion_score=(congestion_result.visual_congestion_score),
        local_peak_congestion_score=(congestion_result.local_peak_congestion_score),
        processing_time_ms=(detection_result.processing_time_ms),
    )


def evaluate_manifest(
    manifest_path: Path,
) -> list[EvaluationRecord]:
    samples = load_evaluation_manifest(
        manifest_path,
    )

    return [evaluate_sample(sample) for sample in samples]


def _summary_to_dict(
    summary: EvaluationSummary,
) -> dict[str, int | float]:
    return {
        "sampleCount": summary.sample_count,
        "totalGroundTruthCount": (summary.total_ground_truth_count),
        "totalPredictedCount": (summary.total_predicted_count),
        "meanAbsoluteError": (summary.mean_absolute_error),
        "meanProcessingTimeMs": (summary.mean_processing_time_ms),
    }


def save_evaluation_report(
    records: list[EvaluationRecord],
    output_path: Path,
    *,
    model_name: str = "YOLO",
    model_version: str = "yolo11n",
) -> None:
    summary = summarize_evaluations(records)

    records_by_scenario: dict[
        str,
        list[EvaluationRecord],
    ] = {}

    for record in records:
        records_by_scenario.setdefault(
            record.scenario,
            [],
        ).append(record)

    scenario_summaries = {
        scenario: _summary_to_dict(summarize_evaluations(scenario_records))
        for scenario, scenario_records in sorted(records_by_scenario.items())
    }

    report = {
        "model": {
            "name": model_name,
            "version": model_version,
        },
        "summary": _summary_to_dict(summary),
        "scenarioSummaries": scenario_summaries,
        "records": [
            {
                "sampleId": record.sample_id,
                "scenario": record.scenario,
                "groundTruthCount": (record.ground_truth_count),
                "predictedCount": (record.predicted_count),
                "absoluteError": (record.absolute_error),
                "visualCongestionScore": (record.visual_congestion_score),
                "localPeakCongestionScore": (record.local_peak_congestion_score),
                "processingTimeMs": (record.processing_time_ms),
            }
            for record in records
        ],
    }

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    output_path.write_text(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
