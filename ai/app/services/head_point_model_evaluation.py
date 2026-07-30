import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from app.services.congestion import CongestionResult, calculate_congestion
from app.services.head_point_dataset import (
    HeadPointSample,
    load_head_point_annotation,
    load_head_point_manifest,
)


LOW_DENSITY_SCENARIOS = frozenset(
    {
        "EMPTY",
        "SPARSE",
        "NORMAL",
        "LOCAL_DENSE",
    }
)

# 대형 오탐(large false positive) 기준은 문서화된 운영 임계값이 없어 이 평가를
# 위해 새로 정의한 값이다. 저밀도 시나리오에서 예측치가 정답보다 20명 이상
# 많고 동시에 3배 이상인 경우로 정의한다. 실제 데이터 분포를 본 뒤 조정한다.
LARGE_FALSE_POSITIVE_ABSOLUTE_THRESHOLD = 20
LARGE_FALSE_POSITIVE_RATIO_THRESHOLD = 3.0

OVERALL_SCENARIO_KEY = "ALL"


class CountDetector(Protocol):
    def __call__(self, image_bytes: bytes) -> object: ...


@dataclass(frozen=True)
class HeadPointEvaluationRecord:
    sample_id: str
    scenario: str
    ground_truth_count: int
    predicted_count: int
    ground_truth_visual_congestion_score: float
    ground_truth_local_peak_congestion_score: float
    predicted_visual_congestion_score: float
    predicted_local_peak_congestion_score: float
    processing_time_ms: int
    is_large_false_positive: bool

    @property
    def absolute_error(self) -> int:
        return abs(self.ground_truth_count - self.predicted_count)

    @property
    def c_absolute_error(self) -> float:
        return abs(
            self.ground_truth_visual_congestion_score
            - self.predicted_visual_congestion_score
        )

    @property
    def l_absolute_error(self) -> float:
        return abs(
            self.ground_truth_local_peak_congestion_score
            - self.predicted_local_peak_congestion_score
        )


@dataclass(frozen=True)
class ScenarioSummary:
    scenario: str
    sample_count: int
    mean_absolute_error: float
    root_mean_squared_error: float
    mean_c_absolute_error: float
    mean_l_absolute_error: float
    mean_processing_time_ms: float
    large_false_positive_count: int


@dataclass(frozen=True)
class HoldoutEvaluationSummary:
    model_name: str
    model_version: str
    overall: ScenarioSummary
    by_scenario: dict[str, ScenarioSummary]
    macro_mean_absolute_error: float
    macro_root_mean_squared_error: float


def compute_ground_truth_congestion(
    sample: HeadPointSample,
) -> CongestionResult:
    annotation = load_head_point_annotation(
        annotation_path=sample.annotation_path,
        image_width=sample.image_width,
        image_height=sample.image_height,
    )

    normalized_points = [
        (
            point_x / sample.image_width,
            point_y / sample.image_height,
        )
        for point_x, point_y in annotation.points
    ]

    return calculate_congestion(normalized_points)


def determine_large_false_positive(
    *,
    scenario: str,
    ground_truth_count: int,
    predicted_count: int,
) -> bool:
    if scenario not in LOW_DENSITY_SCENARIOS:
        return False

    excess = predicted_count - ground_truth_count

    if excess < LARGE_FALSE_POSITIVE_ABSOLUTE_THRESHOLD:
        return False

    if ground_truth_count == 0:
        return True

    return predicted_count >= ground_truth_count * LARGE_FALSE_POSITIVE_RATIO_THRESHOLD


def evaluate_head_point_sample(
    *,
    sample: HeadPointSample,
    detect: CountDetector,
) -> HeadPointEvaluationRecord:
    ground_truth_congestion = compute_ground_truth_congestion(sample)

    detection_result = detect(sample.image_path.read_bytes())

    predicted_person_centers = detection_result.person_centers
    predicted_congestion = calculate_congestion(predicted_person_centers)

    predicted_count = detection_result.person_count

    return HeadPointEvaluationRecord(
        sample_id=sample.sample_id,
        scenario=sample.scenario,
        ground_truth_count=sample.person_count,
        predicted_count=predicted_count,
        ground_truth_visual_congestion_score=(
            ground_truth_congestion.visual_congestion_score
        ),
        ground_truth_local_peak_congestion_score=(
            ground_truth_congestion.local_peak_congestion_score
        ),
        predicted_visual_congestion_score=(
            predicted_congestion.visual_congestion_score
        ),
        predicted_local_peak_congestion_score=(
            predicted_congestion.local_peak_congestion_score
        ),
        processing_time_ms=detection_result.processing_time_ms,
        is_large_false_positive=determine_large_false_positive(
            scenario=sample.scenario,
            ground_truth_count=sample.person_count,
            predicted_count=predicted_count,
        ),
    )


def evaluate_head_point_manifest(
    *,
    manifest_path: Path,
    detect: CountDetector,
    split: str | None = None,
) -> list[HeadPointEvaluationRecord]:
    samples = load_head_point_manifest(manifest_path)

    if split is not None:
        samples = [sample for sample in samples if sample.split == split]

    if not samples:
        raise ValueError("평가할 표본이 한 건 이상 필요합니다.")

    return [
        evaluate_head_point_sample(sample=sample, detect=detect) for sample in samples
    ]


def _summarize_records(
    *,
    scenario: str,
    records: list[HeadPointEvaluationRecord],
) -> ScenarioSummary:
    sample_count = len(records)

    if sample_count == 0:
        raise ValueError("요약할 평가 기록이 한 건 이상 필요합니다.")

    mean_absolute_error = (
        sum(record.absolute_error for record in records) / sample_count
    )
    root_mean_squared_error = math.sqrt(
        sum(record.absolute_error**2 for record in records) / sample_count
    )
    mean_c_absolute_error = (
        sum(record.c_absolute_error for record in records) / sample_count
    )
    mean_l_absolute_error = (
        sum(record.l_absolute_error for record in records) / sample_count
    )
    mean_processing_time_ms = (
        sum(record.processing_time_ms for record in records) / sample_count
    )
    large_false_positive_count = sum(
        1 for record in records if record.is_large_false_positive
    )

    return ScenarioSummary(
        scenario=scenario,
        sample_count=sample_count,
        mean_absolute_error=round(mean_absolute_error, 2),
        root_mean_squared_error=round(root_mean_squared_error, 2),
        mean_c_absolute_error=round(mean_c_absolute_error, 2),
        mean_l_absolute_error=round(mean_l_absolute_error, 2),
        mean_processing_time_ms=round(mean_processing_time_ms, 2),
        large_false_positive_count=large_false_positive_count,
    )


def summarize_by_scenario_and_macro(
    *,
    model_name: str,
    model_version: str,
    records: list[HeadPointEvaluationRecord],
) -> HoldoutEvaluationSummary:
    if not records:
        raise ValueError("요약할 평가 기록이 한 건 이상 필요합니다.")

    records_by_scenario: dict[str, list[HeadPointEvaluationRecord]] = {}

    for record in records:
        records_by_scenario.setdefault(record.scenario, []).append(record)

    by_scenario = {
        scenario: _summarize_records(scenario=scenario, records=scenario_records)
        for scenario, scenario_records in sorted(records_by_scenario.items())
    }

    overall = _summarize_records(scenario=OVERALL_SCENARIO_KEY, records=records)

    scenario_summaries = list(by_scenario.values())
    macro_mean_absolute_error = sum(
        summary.mean_absolute_error for summary in scenario_summaries
    ) / len(scenario_summaries)
    macro_root_mean_squared_error = sum(
        summary.root_mean_squared_error for summary in scenario_summaries
    ) / len(scenario_summaries)

    return HoldoutEvaluationSummary(
        model_name=model_name,
        model_version=model_version,
        overall=overall,
        by_scenario=by_scenario,
        macro_mean_absolute_error=round(macro_mean_absolute_error, 2),
        macro_root_mean_squared_error=round(macro_root_mean_squared_error, 2),
    )


def _scenario_summary_to_dict(
    summary: ScenarioSummary,
) -> dict[str, str | int | float]:
    return {
        "scenario": summary.scenario,
        "sampleCount": summary.sample_count,
        "meanAbsoluteError": summary.mean_absolute_error,
        "rootMeanSquaredError": summary.root_mean_squared_error,
        "meanCAbsoluteError": summary.mean_c_absolute_error,
        "meanLAbsoluteError": summary.mean_l_absolute_error,
        "meanProcessingTimeMs": summary.mean_processing_time_ms,
        "largeFalsePositiveCount": summary.large_false_positive_count,
    }


def save_holdout_evaluation_report(
    *,
    summary: HoldoutEvaluationSummary,
    records: list[HeadPointEvaluationRecord],
    output_path: Path,
) -> Path:
    report = {
        "model": {
            "name": summary.model_name,
            "version": summary.model_version,
        },
        "overall": _scenario_summary_to_dict(summary.overall),
        "byScenario": {
            scenario: _scenario_summary_to_dict(scenario_summary)
            for scenario, scenario_summary in summary.by_scenario.items()
        },
        "macroMeanAbsoluteError": summary.macro_mean_absolute_error,
        "macroRootMeanSquaredError": summary.macro_root_mean_squared_error,
        "records": [
            {
                "sampleId": record.sample_id,
                "scenario": record.scenario,
                "groundTruthCount": record.ground_truth_count,
                "predictedCount": record.predicted_count,
                "absoluteError": record.absolute_error,
                "groundTruthVisualCongestionScore": (
                    record.ground_truth_visual_congestion_score
                ),
                "groundTruthLocalPeakCongestionScore": (
                    record.ground_truth_local_peak_congestion_score
                ),
                "predictedVisualCongestionScore": (
                    record.predicted_visual_congestion_score
                ),
                "predictedLocalPeakCongestionScore": (
                    record.predicted_local_peak_congestion_score
                ),
                "cAbsoluteError": record.c_absolute_error,
                "lAbsoluteError": record.l_absolute_error,
                "processingTimeMs": record.processing_time_ms,
                "isLargeFalsePositive": record.is_large_false_positive,
            }
            for record in records
        ],
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    return output_path
