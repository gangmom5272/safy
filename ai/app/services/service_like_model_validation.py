import csv
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import cv2
import numpy as np

from app.services.congestion import (
    GRID_SIZE,
    LOCAL_WINDOW_SIZE,
    build_congestion_grid,
    calculate_congestion,
)
from app.services.head_point_dataset import (
    HeadPointSample,
    load_head_point_annotation,
)


DETECTION_ERROR = "DETECTION_ERROR"
SCORING_OVERESTIMATION = "SCORING_OVERESTIMATION"

WIDE_DISTRIBUTED = "WIDE_DISTRIBUTED"
LOCAL_CLUSTERED = "LOCAL_CLUSTERED"
NOT_APPLICABLE = "NOT_APPLICABLE"
VALID_SPATIAL_PATTERNS = frozenset(
    {
        WIDE_DISTRIBUTED,
        LOCAL_CLUSTERED,
        NOT_APPLICABLE,
    }
)

APPROVED_REVIEW_STATUS = "APPROVED"
SPATIAL_REVIEW_HEADERS = [
    "sample_id",
    "spatial_pattern",
    "review_status",
    "review_notes",
]

SCORING_OVERESTIMATION_L_THRESHOLD = 75.0
DETECTION_ABSOLUTE_TOLERANCE = 2
DETECTION_RELATIVE_TOLERANCE = 0.2


class DetectionResult(Protocol):
    person_count: int
    confidences: list[float]
    person_centers: list[tuple[float, float]]
    processing_time_ms: int


@dataclass(frozen=True)
class SpatialReview:
    sample_id: str
    spatial_pattern: str
    review_status: str
    review_notes: str


@dataclass(frozen=True)
class ThresholdSensitivity:
    threshold: float
    predicted_count: int
    absolute_error: int
    visual_congestion_score: float
    local_peak_congestion_score: float


@dataclass(frozen=True)
class ModelEvaluation:
    model_name: str
    predicted_count: int
    absolute_error: int
    visual_congestion_score: float
    local_peak_congestion_score: float
    grid_cell_absolute_error: int
    processing_time_ms: int
    error_types: list[str]
    threshold_sensitivity: list[ThresholdSensitivity]


@dataclass(frozen=True)
class ServiceLikeEvaluationRecord:
    sample_id: str
    source: str
    source_group: str
    split: str
    license: str
    consent_confirmed: bool
    scenario: str
    spatial_pattern: str
    review_notes: str
    ground_truth_count: int
    ground_truth_visual_congestion_score: float
    ground_truth_local_peak_congestion_score: float
    occupied_cell_count: int
    peak_window_person_count: int
    peak_window_person_share: float
    yolo: ModelEvaluation
    p2pnet: ModelEvaluation


@dataclass(frozen=True)
class ServiceLikeEvaluationSummary:
    sample_count: int
    wide_distributed_count: int
    local_clustered_count: int
    detection_error_sample_count: int
    scoring_overestimation_sample_count: int


@dataclass(frozen=True)
class ServiceLikeEvaluation:
    summary: ServiceLikeEvaluationSummary
    records: list[ServiceLikeEvaluationRecord]
    retraining_candidates: list[str]
    scoring_calibration_candidates: list[str]


def load_spatial_reviews(
    review_path: Path,
) -> dict[str, SpatialReview]:
    reviews: dict[str, SpatialReview] = {}

    with review_path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)

        if reader.fieldnames != SPATIAL_REVIEW_HEADERS:
            raise ValueError(
                "공간 분포 검수 CSV 헤더가 올바르지 않습니다.",
            )

        for row in reader:
            sample_id = row["sample_id"].strip()
            spatial_pattern = row["spatial_pattern"].strip()
            review_status = row["review_status"].strip()

            if not sample_id:
                raise ValueError("sample_id는 비어 있을 수 없습니다.")

            if sample_id in reviews:
                raise ValueError("sample_id는 중복될 수 없습니다.")

            if spatial_pattern not in VALID_SPATIAL_PATTERNS:
                raise ValueError("지원하지 않는 공간 분포 검수 값입니다.")

            if review_status != APPROVED_REVIEW_STATUS:
                continue

            reviews[sample_id] = SpatialReview(
                sample_id=sample_id,
                spatial_pattern=spatial_pattern,
                review_status=review_status,
                review_notes=row["review_notes"].strip(),
            )

    if not reviews:
        raise ValueError("승인된 공간 분포 검수 결과가 한 건 이상 필요합니다.")

    return reviews


def classify_sample_errors(
    *,
    spatial_pattern: str,
    ground_truth_count: int,
    predicted_count: int,
    ground_truth_local_peak_score: float,
) -> list[str]:
    if spatial_pattern not in VALID_SPATIAL_PATTERNS:
        raise ValueError("지원하지 않는 공간 분포 검수 값입니다.")

    error_types: list[str] = []
    count_tolerance = max(
        DETECTION_ABSOLUTE_TOLERANCE,
        math.ceil(ground_truth_count * DETECTION_RELATIVE_TOLERANCE),
    )

    if abs(predicted_count - ground_truth_count) > count_tolerance:
        error_types.append(DETECTION_ERROR)

    if (
        spatial_pattern == WIDE_DISTRIBUTED
        and ground_truth_local_peak_score >= SCORING_OVERESTIMATION_L_THRESHOLD
    ):
        error_types.append(SCORING_OVERESTIMATION)

    return error_types


def summarize_threshold_sensitivity(
    *,
    ground_truth_count: int,
    confidences: list[float],
    person_centers: list[tuple[float, float]],
    thresholds: list[float],
) -> list[ThresholdSensitivity]:
    if len(confidences) != len(person_centers):
        raise ValueError("P2PNet confidence와 머리 좌표 개수가 일치해야 합니다.")

    if not thresholds:
        raise ValueError("confidence 임계값이 한 개 이상 필요합니다.")

    summaries: list[ThresholdSensitivity] = []

    for threshold in sorted(set(thresholds)):
        if not 0.0 <= threshold <= 1.0:
            raise ValueError("confidence 임계값은 0과 1 사이여야 합니다.")

        selected_centers = [
            center
            for confidence, center in zip(
                confidences,
                person_centers,
                strict=True,
            )
            if confidence > threshold
        ]
        congestion = calculate_congestion(selected_centers)

        summaries.append(
            ThresholdSensitivity(
                threshold=round(threshold, 4),
                predicted_count=len(selected_centers),
                absolute_error=abs(
                    ground_truth_count - len(selected_centers),
                ),
                visual_congestion_score=(congestion.visual_congestion_score),
                local_peak_congestion_score=(congestion.local_peak_congestion_score),
            )
        )

    return summaries


def load_ground_truth_centers(
    sample: HeadPointSample,
) -> list[tuple[float, float]]:
    annotation = load_head_point_annotation(
        annotation_path=sample.annotation_path,
        image_width=sample.image_width,
        image_height=sample.image_height,
    )

    return [
        (
            point_x / sample.image_width,
            point_y / sample.image_height,
        )
        for point_x, point_y in annotation.points
    ]


def _grid_cell_absolute_error(
    ground_truth_centers: list[tuple[float, float]],
    predicted_centers: list[tuple[float, float]],
) -> int:
    ground_truth_grid = build_congestion_grid(ground_truth_centers)
    predicted_grid = build_congestion_grid(predicted_centers)

    return sum(
        abs(
            ground_truth_grid.cell_counts[row][column]
            - predicted_grid.cell_counts[row][column]
        )
        for row in range(GRID_SIZE)
        for column in range(GRID_SIZE)
    )


def _build_model_evaluation(
    *,
    model_name: str,
    result: DetectionResult,
    ground_truth_centers: list[tuple[float, float]],
    spatial_pattern: str,
    ground_truth_local_peak_score: float,
    thresholds: list[float] | None = None,
) -> ModelEvaluation:
    congestion = calculate_congestion(result.person_centers)
    ground_truth_count = len(ground_truth_centers)

    threshold_sensitivity = (
        summarize_threshold_sensitivity(
            ground_truth_count=ground_truth_count,
            confidences=result.confidences,
            person_centers=result.person_centers,
            thresholds=thresholds,
        )
        if thresholds is not None
        else []
    )

    return ModelEvaluation(
        model_name=model_name,
        predicted_count=result.person_count,
        absolute_error=abs(
            ground_truth_count - result.person_count,
        ),
        visual_congestion_score=(congestion.visual_congestion_score),
        local_peak_congestion_score=(congestion.local_peak_congestion_score),
        grid_cell_absolute_error=_grid_cell_absolute_error(
            ground_truth_centers,
            result.person_centers,
        ),
        processing_time_ms=result.processing_time_ms,
        error_types=classify_sample_errors(
            spatial_pattern=spatial_pattern,
            ground_truth_count=ground_truth_count,
            predicted_count=result.person_count,
            ground_truth_local_peak_score=(ground_truth_local_peak_score),
        ),
        threshold_sensitivity=threshold_sensitivity,
    )


def build_service_like_evaluation(
    *,
    samples: list[HeadPointSample],
    reviews: dict[str, SpatialReview],
    yolo_results: dict[str, DetectionResult],
    p2pnet_results: dict[str, DetectionResult],
    thresholds: list[float],
) -> ServiceLikeEvaluation:
    sample_by_id = {sample.sample_id: sample for sample in samples}
    records: list[ServiceLikeEvaluationRecord] = []

    for sample_id, review in sorted(reviews.items()):
        if sample_id not in sample_by_id:
            raise ValueError(f"검수 표본을 manifest에서 찾을 수 없습니다: {sample_id}")

        if sample_id not in yolo_results or sample_id not in p2pnet_results:
            raise ValueError(f"모델 평가 결과가 누락되었습니다: {sample_id}")

        sample = sample_by_id[sample_id]

        if sample.split != "TEST":
            raise ValueError("실서비스 유사 평가는 TEST 분할만 사용할 수 있습니다.")

        if not sample.license and not sample.consent_confirmed:
            raise ValueError("평가 표본에는 라이선스 또는 사용 동의가 필요합니다.")

        ground_truth_centers = load_ground_truth_centers(sample)
        ground_truth_congestion = calculate_congestion(ground_truth_centers)
        ground_truth_grid = build_congestion_grid(ground_truth_centers)
        peak_window_person_count = sum(
            ground_truth_grid.cell_counts[row][column]
            for row in range(
                ground_truth_grid.peak_window_row,
                ground_truth_grid.peak_window_row + LOCAL_WINDOW_SIZE,
            )
            for column in range(
                ground_truth_grid.peak_window_column,
                ground_truth_grid.peak_window_column + LOCAL_WINDOW_SIZE,
            )
        )
        occupied_cell_count = sum(
            1 for row in ground_truth_grid.cell_counts for count in row if count > 0
        )

        yolo = _build_model_evaluation(
            model_name="YOLO",
            result=yolo_results[sample_id],
            ground_truth_centers=ground_truth_centers,
            spatial_pattern=review.spatial_pattern,
            ground_truth_local_peak_score=(
                ground_truth_congestion.local_peak_congestion_score
            ),
        )
        p2pnet = _build_model_evaluation(
            model_name="P2PNet",
            result=p2pnet_results[sample_id],
            ground_truth_centers=ground_truth_centers,
            spatial_pattern=review.spatial_pattern,
            ground_truth_local_peak_score=(
                ground_truth_congestion.local_peak_congestion_score
            ),
            thresholds=thresholds,
        )

        records.append(
            ServiceLikeEvaluationRecord(
                sample_id=sample.sample_id,
                source=sample.source,
                source_group=sample.source_group,
                split=sample.split,
                license=sample.license,
                consent_confirmed=sample.consent_confirmed,
                scenario=sample.scenario,
                spatial_pattern=review.spatial_pattern,
                review_notes=review.review_notes,
                ground_truth_count=sample.person_count,
                ground_truth_visual_congestion_score=(
                    ground_truth_congestion.visual_congestion_score
                ),
                ground_truth_local_peak_congestion_score=(
                    ground_truth_congestion.local_peak_congestion_score
                ),
                occupied_cell_count=occupied_cell_count,
                peak_window_person_count=peak_window_person_count,
                peak_window_person_share=round(
                    peak_window_person_count / max(1, sample.person_count),
                    4,
                ),
                yolo=yolo,
                p2pnet=p2pnet,
            )
        )

    retraining_candidates = sorted(
        record.sample_id
        for record in records
        if DETECTION_ERROR in record.yolo.error_types
        or DETECTION_ERROR in record.p2pnet.error_types
    )
    scoring_calibration_candidates = sorted(
        record.sample_id
        for record in records
        if SCORING_OVERESTIMATION in record.yolo.error_types
        or SCORING_OVERESTIMATION in record.p2pnet.error_types
    )

    summary = ServiceLikeEvaluationSummary(
        sample_count=len(records),
        wide_distributed_count=sum(
            1 for record in records if record.spatial_pattern == WIDE_DISTRIBUTED
        ),
        local_clustered_count=sum(
            1 for record in records if record.spatial_pattern == LOCAL_CLUSTERED
        ),
        detection_error_sample_count=len(retraining_candidates),
        scoring_overestimation_sample_count=len(
            scoring_calibration_candidates,
        ),
    )

    return ServiceLikeEvaluation(
        summary=summary,
        records=records,
        retraining_candidates=retraining_candidates,
        scoring_calibration_candidates=(scoring_calibration_candidates),
    )


def save_service_like_evaluation_report(
    *,
    evaluation: ServiceLikeEvaluation,
    output_path: Path,
    metadata: dict[str, object] | None = None,
) -> Path:
    payload = {
        "evaluationMetadata": metadata or {},
        "summary": {
            "sampleCount": evaluation.summary.sample_count,
            "wideDistributedCount": (evaluation.summary.wide_distributed_count),
            "localClusteredCount": (evaluation.summary.local_clustered_count),
            "detectionErrorSampleCount": (
                evaluation.summary.detection_error_sample_count
            ),
            "scoringOverestimationSampleCount": (
                evaluation.summary.scoring_overestimation_sample_count
            ),
        },
        "thresholdSensitivityByDensity": (
            aggregate_threshold_sensitivity(evaluation.records)
        ),
        "retrainingCandidates": evaluation.retraining_candidates,
        "scoringCalibrationCandidates": (evaluation.scoring_calibration_candidates),
        "records": [
            {
                "sampleId": record.sample_id,
                "source": record.source,
                "sourceGroup": record.source_group,
                "split": record.split,
                "license": record.license,
                "consentConfirmed": record.consent_confirmed,
                "scenario": record.scenario,
                "spatialPattern": record.spatial_pattern,
                "reviewNotes": record.review_notes,
                "groundTruth": {
                    "personCount": record.ground_truth_count,
                    "visualCongestionScore": (
                        record.ground_truth_visual_congestion_score
                    ),
                    "localPeakCongestionScore": (
                        record.ground_truth_local_peak_congestion_score
                    ),
                    "occupiedCellCount": record.occupied_cell_count,
                    "peakWindowPersonCount": (record.peak_window_person_count),
                    "peakWindowPersonShare": (record.peak_window_person_share),
                },
                "yolo": _model_evaluation_to_dict(record.yolo),
                "p2pnet": _model_evaluation_to_dict(record.p2pnet),
            }
            for record in evaluation.records
        ],
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    return output_path


def _model_evaluation_to_dict(
    evaluation: ModelEvaluation,
) -> dict[str, object]:
    return {
        "modelName": evaluation.model_name,
        "predictedCount": evaluation.predicted_count,
        "absoluteError": evaluation.absolute_error,
        "visualCongestionScore": evaluation.visual_congestion_score,
        "localPeakCongestionScore": (evaluation.local_peak_congestion_score),
        "gridCellAbsoluteError": evaluation.grid_cell_absolute_error,
        "processingTimeMs": evaluation.processing_time_ms,
        "errorTypes": evaluation.error_types,
        "thresholdSensitivity": [
            {
                "threshold": sensitivity.threshold,
                "predictedCount": sensitivity.predicted_count,
                "absoluteError": sensitivity.absolute_error,
                "visualCongestionScore": (sensitivity.visual_congestion_score),
                "localPeakCongestionScore": (sensitivity.local_peak_congestion_score),
            }
            for sensitivity in evaluation.threshold_sensitivity
        ],
    }


def aggregate_threshold_sensitivity(
    records: list[ServiceLikeEvaluationRecord],
) -> dict[str, list[dict[str, int | float]]]:
    density_groups = {
        "LOW_DENSITY": {"EMPTY", "SPARSE", "NORMAL"},
        "HIGH_DENSITY": {"LOCAL_DENSE", "CROWDED"},
    }
    aggregate: dict[str, list[dict[str, int | float]]] = {}

    for group_name, scenarios in density_groups.items():
        group_records = [record for record in records if record.scenario in scenarios]

        if not group_records:
            aggregate[group_name] = []
            continue

        thresholds = sorted(
            {
                sensitivity.threshold
                for record in group_records
                for sensitivity in record.p2pnet.threshold_sensitivity
            }
        )
        summaries: list[dict[str, int | float]] = []

        for threshold in thresholds:
            measurements = []

            for record in group_records:
                measurement = next(
                    (
                        sensitivity
                        for sensitivity in record.p2pnet.threshold_sensitivity
                        if sensitivity.threshold == threshold
                    ),
                    None,
                )

                if measurement is not None:
                    measurements.append((record, measurement))

            if not measurements:
                continue

            sample_count = len(measurements)
            summaries.append(
                {
                    "threshold": threshold,
                    "sampleCount": sample_count,
                    "meanAbsoluteError": round(
                        sum(
                            measurement.absolute_error
                            for _, measurement in measurements
                        )
                        / sample_count,
                        2,
                    ),
                    "meanOvercount": round(
                        sum(
                            max(
                                measurement.predicted_count - record.ground_truth_count,
                                0,
                            )
                            for record, measurement in measurements
                        )
                        / sample_count,
                        2,
                    ),
                    "meanUndercount": round(
                        sum(
                            max(
                                record.ground_truth_count - measurement.predicted_count,
                                0,
                            )
                            for record, measurement in measurements
                        )
                        / sample_count,
                        2,
                    ),
                }
            )

        aggregate[group_name] = summaries

    return aggregate


def render_model_comparison(
    *,
    image_bytes: bytes,
    ground_truth_centers: list[tuple[float, float]],
    yolo_centers: list[tuple[float, float]],
    p2pnet_centers: list[tuple[float, float]],
    labels: tuple[str, str, str],
    max_panel_side: int = 1280,
) -> bytes:
    encoded_image = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(encoded_image, cv2.IMREAD_COLOR)

    if image is None:
        raise ValueError("이미지를 디코딩할 수 없습니다.")

    height, width = image.shape[:2]
    resize_scale = min(1.0, max_panel_side / max(width, height))

    if resize_scale < 1.0:
        image = cv2.resize(
            image,
            (
                round(width * resize_scale),
                round(height * resize_scale),
            ),
            interpolation=cv2.INTER_AREA,
        )

    panels = [
        _render_point_panel(image, centers, label)
        for centers, label in zip(
            [ground_truth_centers, yolo_centers, p2pnet_centers],
            labels,
            strict=True,
        )
    ]
    combined = np.concatenate(panels, axis=1)
    success, encoded_result = cv2.imencode(".png", combined)

    if not success:
        raise ValueError("모델 비교 이미지를 인코딩할 수 없습니다.")

    return encoded_result.tobytes()


def _render_point_panel(
    image: np.ndarray,
    centers: list[tuple[float, float]],
    label: str,
) -> np.ndarray:
    panel = image.copy()
    height, width = panel.shape[:2]
    grid = build_congestion_grid(centers)

    for index in range(1, GRID_SIZE):
        x = round(index * width / GRID_SIZE)
        y = round(index * height / GRID_SIZE)
        cv2.line(panel, (x, 0), (x, height - 1), (0, 180, 0), 1)
        cv2.line(panel, (0, y), (width - 1, y), (0, 180, 0), 1)

    for center_x, center_y in centers:
        x = round(min(max(center_x, 0.0), 1.0) * (width - 1))
        y = round(min(max(center_y, 0.0), 1.0) * (height - 1))
        cv2.circle(
            panel, (x, y), max(2, round(min(width, height) / 250)), (0, 0, 255), -1
        )

    if grid.peak_window_score > 0:
        left = round(grid.peak_window_column * width / GRID_SIZE)
        top = round(grid.peak_window_row * height / GRID_SIZE)
        right = round(
            (grid.peak_window_column + LOCAL_WINDOW_SIZE) * width / GRID_SIZE,
        )
        bottom = round(
            (grid.peak_window_row + LOCAL_WINDOW_SIZE) * height / GRID_SIZE,
        )
        cv2.rectangle(
            panel,
            (left, top),
            (min(right, width - 1), min(bottom, height - 1)),
            (255, 0, 255),
            3,
        )

    congestion = calculate_congestion(centers)
    cv2.rectangle(panel, (0, 0), (width - 1, 38), (0, 0, 0), -1)
    cv2.putText(
        panel,
        (
            f"{label} N={len(centers)} "
            f"C={congestion.visual_congestion_score:.2f} "
            f"L={congestion.local_peak_congestion_score:.2f}"
        ),
        (8, 26),
        cv2.FONT_HERSHEY_SIMPLEX,
        max(0.4, min(0.7, width / 1200)),
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )

    return panel
