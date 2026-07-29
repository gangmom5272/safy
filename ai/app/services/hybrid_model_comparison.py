import csv
import json
import logging
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from app.services.congestion import calculate_congestion
from app.services.head_point_dataset import load_head_point_manifest
from app.services.model import MODEL_NAME, MODEL_VERSION, MODEL_WEIGHT_ID, get_model
from app.services.model_evaluation import (
    EvaluationSample,
    load_evaluation_manifest,
)
from app.services.p2pnet_benchmark_adapter import (
    load_p2pnet_detector,
)
from app.services.p2pnet_congestion import analyze_p2pnet_congestion
from app.services.person_detector import detect_people


logger = logging.getLogger(__name__)

COMPLETED_STATUS = "COMPLETED"
FAILED_STATUS = "FAILED"
UNDECIDED_STATUS = "UNDECIDED"
UNDECIDED_REASON = "INSUFFICIENT_SAFY_EVALUATION_DATA"


@dataclass(frozen=True)
class AnalysisMetrics:
    person_count: int
    visual_congestion_score: float
    local_peak_congestion_score: float
    processing_time_ms: int


@dataclass(frozen=True)
class ModelRunResult:
    model_name: str
    model_version: str
    model_weight_id: str
    analysis_status: str
    person_count: int | None
    visual_congestion_score: float | None
    local_peak_congestion_score: float | None
    processing_time_ms: int
    failure_codes: list[str]

    @property
    def model_key(self) -> str:
        return f"{self.model_name}:{self.model_version}"


@dataclass(frozen=True)
class ModelRunner:
    model_name: str
    model_version: str
    model_weight_id: str
    failure_code: str
    analyze: Callable[[bytes], AnalysisMetrics]

    def run(self, image_bytes: bytes) -> ModelRunResult:
        try:
            metrics = self.analyze(image_bytes)
        except Exception:
            logger.exception("%s analysis failed", self.model_name)
            return ModelRunResult(
                model_name=self.model_name,
                model_version=self.model_version,
                model_weight_id=self.model_weight_id,
                analysis_status=FAILED_STATUS,
                person_count=None,
                visual_congestion_score=None,
                local_peak_congestion_score=None,
                processing_time_ms=0,
                failure_codes=[self.failure_code],
            )

        return ModelRunResult(
            model_name=self.model_name,
            model_version=self.model_version,
            model_weight_id=self.model_weight_id,
            analysis_status=COMPLETED_STATUS,
            person_count=metrics.person_count,
            visual_congestion_score=metrics.visual_congestion_score,
            local_peak_congestion_score=metrics.local_peak_congestion_score,
            processing_time_ms=metrics.processing_time_ms,
            failure_codes=[],
        )


@dataclass(frozen=True)
class HybridModelComparisonRecord:
    sample_id: str
    scenario: str
    ground_truth_count: int
    yolo: ModelRunResult
    p2pnet: ModelRunResult
    selection_status: str = UNDECIDED_STATUS

    @property
    def person_count_difference(self) -> int | None:
        if self.yolo.person_count is None or self.p2pnet.person_count is None:
            return None

        return abs(self.yolo.person_count - self.p2pnet.person_count)

    @property
    def visual_congestion_score_difference(self) -> float | None:
        if (
            self.yolo.visual_congestion_score is None
            or self.p2pnet.visual_congestion_score is None
        ):
            return None

        return round(
            abs(
                self.yolo.visual_congestion_score - self.p2pnet.visual_congestion_score
            ),
            2,
        )

    @property
    def local_peak_congestion_score_difference(self) -> float | None:
        if (
            self.yolo.local_peak_congestion_score is None
            or self.p2pnet.local_peak_congestion_score is None
        ):
            return None

        return round(
            abs(
                self.yolo.local_peak_congestion_score
                - self.p2pnet.local_peak_congestion_score
            ),
            2,
        )


@dataclass(frozen=True)
class HybridModelSummary:
    model_name: str
    model_version: str
    sample_count: int
    completed_count: int
    failed_count: int
    mean_absolute_error: float | None
    root_mean_squared_error: float | None
    mean_processing_time_ms: float | None


def build_yolo_runner() -> ModelRunner:
    get_model()

    def analyze(image_bytes: bytes) -> AnalysisMetrics:
        detection_result = detect_people(image_bytes)
        congestion_result = calculate_congestion(
            detection_result.person_centers,
        )

        return AnalysisMetrics(
            person_count=detection_result.person_count,
            visual_congestion_score=congestion_result.visual_congestion_score,
            local_peak_congestion_score=(congestion_result.local_peak_congestion_score),
            processing_time_ms=detection_result.processing_time_ms,
        )

    return ModelRunner(
        model_name=MODEL_NAME,
        model_version=MODEL_VERSION,
        model_weight_id=MODEL_WEIGHT_ID,
        failure_code="MODEL_INFERENCE_FAILED",
        analyze=analyze,
    )


def build_p2pnet_runner(
    *,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
    threshold: float = 0.5,
    model_version: str = "safy-p2pnet-v1",
) -> ModelRunner:
    detector = load_p2pnet_detector(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
        threshold=threshold,
    )

    def analyze(image_bytes: bytes) -> AnalysisMetrics:
        result = analyze_p2pnet_congestion(
            image_bytes=image_bytes,
            detector=detector,
        )

        return AnalysisMetrics(
            person_count=result.person_count,
            visual_congestion_score=result.visual_congestion_score,
            local_peak_congestion_score=result.local_peak_congestion_score,
            processing_time_ms=result.processing_time_ms,
        )

    return ModelRunner(
        model_name="P2PNet",
        model_version=model_version,
        model_weight_id=weight_path.name,
        failure_code="MODEL_INFERENCE_FAILED",
        analyze=analyze,
    )


def compare_sample(
    *,
    sample: EvaluationSample,
    yolo_runner: ModelRunner,
    p2pnet_runner: ModelRunner,
) -> HybridModelComparisonRecord:
    image_bytes = sample.image_path.read_bytes()

    return HybridModelComparisonRecord(
        sample_id=sample.sample_id,
        scenario=sample.scenario,
        ground_truth_count=sample.ground_truth_count,
        yolo=yolo_runner.run(image_bytes),
        p2pnet=p2pnet_runner.run(image_bytes),
    )


def compare_samples(
    *,
    samples: list[EvaluationSample],
    yolo_runner: ModelRunner,
    p2pnet_runner: ModelRunner,
) -> list[HybridModelComparisonRecord]:
    if not samples:
        raise ValueError("비교할 평가 표본이 한 건 이상 필요합니다.")

    return [
        compare_sample(
            sample=sample,
            yolo_runner=yolo_runner,
            p2pnet_runner=p2pnet_runner,
        )
        for sample in samples
    ]


def load_hybrid_evaluation_samples(
    manifest_path: Path,
    *,
    source: str | None = None,
    split: str | None = None,
) -> list[EvaluationSample]:
    with manifest_path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        fieldnames = csv.DictReader(stream).fieldnames or []

    if "ground_truth_count" in fieldnames:
        samples = load_evaluation_manifest(manifest_path)
        if split is not None:
            raise ValueError(
                "일반 평가 manifest에는 split 필터를 사용할 수 없습니다.",
            )
    elif "person_count" in fieldnames:
        head_point_samples = load_head_point_manifest(manifest_path)
        samples = [
            EvaluationSample(
                sample_id=sample.sample_id,
                scenario=sample.scenario,
                image_path=sample.image_path,
                ground_truth_count=sample.person_count,
                source=sample.source,
                license=sample.license,
            )
            for sample in head_point_samples
            if split is None or sample.split == split
        ]
    else:
        raise ValueError(
            "지원하는 평가 manifest 형식이 아닙니다.",
        )

    if source is not None:
        samples = [sample for sample in samples if sample.source == source]

    if not samples:
        raise ValueError(
            "조건에 맞는 평가 표본이 없습니다.",
        )

    return samples


def _model_results(
    records: list[HybridModelComparisonRecord],
) -> dict[str, list[tuple[ModelRunResult, int]]]:
    results: dict[str, list[tuple[ModelRunResult, int]]] = {}

    for record in records:
        for result in (record.yolo, record.p2pnet):
            results.setdefault(
                result.model_key,
                [],
            ).append(
                (
                    result,
                    record.ground_truth_count,
                )
            )

    return results


def summarize_hybrid_comparisons(
    records: list[HybridModelComparisonRecord],
) -> dict[str, HybridModelSummary]:
    if not records:
        raise ValueError(
            "비교 기록이 한 건 이상 필요합니다.",
        )

    summaries: dict[str, HybridModelSummary] = {}

    for model_key, results in _model_results(records).items():
        completed = [
            (result, ground_truth_count)
            for result, ground_truth_count in results
            if result.analysis_status == COMPLETED_STATUS
            and result.person_count is not None
        ]
        first_result = results[0][0]

        if completed:
            errors = [
                result.person_count - ground_truth_count
                for result, ground_truth_count in completed
                if result.person_count is not None
            ]
            mean_absolute_error = round(
                sum(abs(error) for error in errors) / len(errors),
                2,
            )
            root_mean_squared_error = round(
                (sum(error * error for error in errors) / len(errors)) ** 0.5,
                2,
            )
            mean_processing_time_ms = round(
                sum(result.processing_time_ms for result, _ in completed)
                / len(completed),
                2,
            )
        else:
            mean_absolute_error = None
            root_mean_squared_error = None
            mean_processing_time_ms = None

        summaries[model_key] = HybridModelSummary(
            model_name=first_result.model_name,
            model_version=first_result.model_version,
            sample_count=len(results),
            completed_count=len(completed),
            failed_count=len(results) - len(completed),
            mean_absolute_error=mean_absolute_error,
            root_mean_squared_error=root_mean_squared_error,
            mean_processing_time_ms=mean_processing_time_ms,
        )

    return summaries


def _model_result_to_dict(
    result: ModelRunResult,
) -> dict:
    return {
        "modelName": result.model_name,
        "modelVersion": result.model_version,
        "modelWeightId": result.model_weight_id,
        "analysisStatus": result.analysis_status,
        "personCount": result.person_count,
        "visualCongestionScore": result.visual_congestion_score,
        "localPeakCongestionScore": result.local_peak_congestion_score,
        "processingTimeMs": result.processing_time_ms,
        "failureCodes": result.failure_codes,
    }


def _summary_to_dict(
    summary: HybridModelSummary,
) -> dict:
    return {
        "modelName": summary.model_name,
        "modelVersion": summary.model_version,
        "sampleCount": summary.sample_count,
        "completedCount": summary.completed_count,
        "failedCount": summary.failed_count,
        "meanAbsoluteError": summary.mean_absolute_error,
        "rootMeanSquaredError": summary.root_mean_squared_error,
        "meanProcessingTimeMs": summary.mean_processing_time_ms,
    }


def save_hybrid_model_comparison_report(
    *,
    records: list[HybridModelComparisonRecord],
    output_path: Path,
) -> None:
    summaries = summarize_hybrid_comparisons(records)
    records_by_scenario: dict[str, list[HybridModelComparisonRecord]] = {}

    for record in records:
        records_by_scenario.setdefault(
            record.scenario,
            [],
        ).append(record)

    report = {
        "decisionPolicy": {
            "selectionStatus": UNDECIDED_STATUS,
            "reason": UNDECIDED_REASON,
        },
        "sampleCount": len(records),
        "modelSummaries": {
            model_key: _summary_to_dict(summary)
            for model_key, summary in sorted(summaries.items())
        },
        "scenarioSummaries": {
            scenario: {
                model_key: _summary_to_dict(summary)
                for model_key, summary in sorted(
                    summarize_hybrid_comparisons(scenario_records).items()
                )
            }
            for scenario, scenario_records in sorted(records_by_scenario.items())
        },
        "records": [
            {
                "sampleId": record.sample_id,
                "scenario": record.scenario,
                "groundTruthCount": record.ground_truth_count,
                "selectionStatus": record.selection_status,
                "personCountDifference": record.person_count_difference,
                "visualCongestionScoreDifference": (
                    record.visual_congestion_score_difference
                ),
                "localPeakCongestionScoreDifference": (
                    record.local_peak_congestion_score_difference
                ),
                "yolo": _model_result_to_dict(record.yolo),
                "p2pnet": _model_result_to_dict(record.p2pnet),
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
        ),
        encoding="utf-8",
    )
