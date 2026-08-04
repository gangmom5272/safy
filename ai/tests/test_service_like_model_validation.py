import csv
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

from app.services.head_point_dataset import HeadPointSample
from app.services.service_like_model_validation import (
    DETECTION_ERROR,
    LOCAL_CLUSTERED,
    SCORING_OVERESTIMATION,
    WIDE_DISTRIBUTED,
    aggregate_threshold_sensitivity,
    build_service_like_evaluation,
    classify_sample_errors,
    load_spatial_reviews,
    render_model_comparison,
    save_service_like_evaluation_report,
    summarize_threshold_sensitivity,
)


@dataclass(frozen=True)
class _DetectionResult:
    person_count: int
    confidences: list[float]
    person_centers: list[tuple[float, float]]
    processing_time_ms: int


def _write_sample(
    tmp_path: Path,
    *,
    sample_id: str = "park-1",
    points: list[tuple[float, float]] | None = None,
) -> HeadPointSample:
    points = points or [
        (80.0, 80.0),
        (120.0, 100.0),
        (180.0, 120.0),
        (220.0, 140.0),
        (400.0, 300.0),
        (500.0, 360.0),
    ]
    image_path = tmp_path / f"{sample_id}.jpg"
    annotation_path = tmp_path / f"{sample_id}.points.csv"
    Image.new("RGB", (640, 480), color=(120, 140, 100)).save(image_path)

    with annotation_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["x", "y"])
        writer.writerows(points)

    return HeadPointSample(
        sample_id=sample_id,
        source="JHU-CROWD++ v2.0",
        source_group=sample_id,
        split="TEST",
        image_path=image_path,
        annotation_path=annotation_path,
        image_width=640,
        image_height=480,
        person_count=len(points),
        scenario="LOCAL_DENSE",
        license="jhu-crowd-noncommercial",
        consent_confirmed=False,
        image_sha256=hashlib.sha256(image_path.read_bytes()).hexdigest(),
        label_format_version="head-point-v1",
    )


def test_load_spatial_reviews_requires_reviewed_supported_pattern(
    tmp_path: Path,
) -> None:
    review_path = tmp_path / "reviews.csv"
    review_path.write_text(
        (
            "sample_id,spatial_pattern,review_status,review_notes\n"
            "park-1,WIDE_DISTRIBUTED,APPROVED,공원 산책로\n"
            "crowd-1,LOCAL_CLUSTERED,APPROVED,무대 앞 밀집\n"
        ),
        encoding="utf-8",
    )

    reviews = load_spatial_reviews(review_path)

    assert reviews["park-1"].spatial_pattern == WIDE_DISTRIBUTED
    assert reviews["crowd-1"].spatial_pattern == LOCAL_CLUSTERED


def test_classify_sample_errors_separates_detection_and_scoring() -> None:
    assert classify_sample_errors(
        spatial_pattern=WIDE_DISTRIBUTED,
        ground_truth_count=30,
        predicted_count=29,
        ground_truth_local_peak_score=90.0,
    ) == [SCORING_OVERESTIMATION]

    assert classify_sample_errors(
        spatial_pattern=LOCAL_CLUSTERED,
        ground_truth_count=30,
        predicted_count=10,
        ground_truth_local_peak_score=90.0,
    ) == [DETECTION_ERROR]

    assert classify_sample_errors(
        spatial_pattern=WIDE_DISTRIBUTED,
        ground_truth_count=30,
        predicted_count=10,
        ground_truth_local_peak_score=90.0,
    ) == [DETECTION_ERROR, SCORING_OVERESTIMATION]


def test_summarize_threshold_sensitivity_reuses_candidate_points() -> None:
    summary = summarize_threshold_sensitivity(
        ground_truth_count=2,
        confidences=[0.51, 0.59, 0.61],
        person_centers=[
            (0.1, 0.1),
            (0.2, 0.2),
            (0.8, 0.8),
        ],
        thresholds=[0.5, 0.6],
    )

    assert summary[0].threshold == 0.5
    assert summary[0].predicted_count == 3
    assert summary[0].absolute_error == 1
    assert summary[1].threshold == 0.6
    assert summary[1].predicted_count == 1
    assert summary[1].absolute_error == 1


def test_build_service_like_evaluation_lists_follow_up_candidates(
    tmp_path: Path,
) -> None:
    sample = _write_sample(tmp_path)
    review_path = tmp_path / "reviews.csv"
    review_path.write_text(
        (
            "sample_id,spatial_pattern,review_status,review_notes\n"
            "park-1,WIDE_DISTRIBUTED,APPROVED,공원형 분산 장면\n"
        ),
        encoding="utf-8",
    )
    reviews = load_spatial_reviews(review_path)

    yolo_result = _DetectionResult(
        person_count=1,
        confidences=[0.9],
        person_centers=[(0.1, 0.1)],
        processing_time_ms=10,
    )
    p2pnet_result = _DetectionResult(
        person_count=6,
        confidences=[0.9] * 6,
        person_centers=[
            (x / 640, y / 480)
            for x, y in [
                (80, 80),
                (120, 100),
                (180, 120),
                (220, 140),
                (400, 300),
                (500, 360),
            ]
        ],
        processing_time_ms=20,
    )

    evaluation = build_service_like_evaluation(
        samples=[sample],
        reviews=reviews,
        yolo_results={sample.sample_id: yolo_result},
        p2pnet_results={sample.sample_id: p2pnet_result},
        thresholds=[0.5, 0.6],
    )

    assert evaluation.summary.sample_count == 1
    assert evaluation.summary.detection_error_sample_count == 1
    assert evaluation.retraining_candidates == ["park-1"]
    assert evaluation.records[0].source == "JHU-CROWD++ v2.0"
    assert evaluation.records[0].license == "jhu-crowd-noncommercial"
    assert DETECTION_ERROR in evaluation.records[0].yolo.error_types


def test_save_report_contains_separate_follow_up_lists(tmp_path: Path) -> None:
    sample = _write_sample(tmp_path)
    review_path = tmp_path / "reviews.csv"
    review_path.write_text(
        (
            "sample_id,spatial_pattern,review_status,review_notes\n"
            "park-1,WIDE_DISTRIBUTED,APPROVED,공원형 분산 장면\n"
        ),
        encoding="utf-8",
    )
    result = _DetectionResult(
        person_count=6,
        confidences=[0.9] * 6,
        person_centers=[(0.1, 0.1)] * 6,
        processing_time_ms=10,
    )
    evaluation = build_service_like_evaluation(
        samples=[sample],
        reviews=load_spatial_reviews(review_path),
        yolo_results={"park-1": result},
        p2pnet_results={"park-1": result},
        thresholds=[0.5],
    )
    output_path = tmp_path / "report.json"

    save_service_like_evaluation_report(
        evaluation=evaluation,
        output_path=output_path,
    )

    payload = json.loads(output_path.read_text(encoding="utf-8"))
    assert "retrainingCandidates" in payload
    assert "scoringCalibrationCandidates" in payload
    assert payload["summary"]["sampleCount"] == 1
    assert payload["records"][0]["spatialPattern"] == WIDE_DISTRIBUTED
    assert payload["records"][0]["yolo"]["predictedCount"] == 6


def test_aggregate_threshold_sensitivity_separates_density_groups(
    tmp_path: Path,
) -> None:
    sample = _write_sample(tmp_path)
    review_path = tmp_path / "reviews.csv"
    review_path.write_text(
        (
            "sample_id,spatial_pattern,review_status,review_notes\n"
            "park-1,WIDE_DISTRIBUTED,APPROVED,분산 장면\n"
        ),
        encoding="utf-8",
    )
    result = _DetectionResult(
        person_count=6,
        confidences=[0.51, 0.59, 0.61, 0.7, 0.8, 0.9],
        person_centers=[(0.1, 0.1)] * 6,
        processing_time_ms=10,
    )
    evaluation = build_service_like_evaluation(
        samples=[sample],
        reviews=load_spatial_reviews(review_path),
        yolo_results={"park-1": result},
        p2pnet_results={"park-1": result},
        thresholds=[0.5, 0.6],
    )

    aggregate = aggregate_threshold_sensitivity(evaluation.records)

    assert aggregate["LOW_DENSITY"] == []
    assert aggregate["HIGH_DENSITY"][0]["threshold"] == 0.5
    assert aggregate["HIGH_DENSITY"][0]["sampleCount"] == 1


def test_render_model_comparison_returns_three_panel_png(tmp_path: Path) -> None:
    sample = _write_sample(tmp_path)
    image_bytes = sample.image_path.read_bytes()

    rendered = render_model_comparison(
        image_bytes=image_bytes,
        ground_truth_centers=[(0.1, 0.1)],
        yolo_centers=[(0.2, 0.2)],
        p2pnet_centers=[(0.3, 0.3)],
        labels=("GROUND_TRUTH", "YOLO", "P2PNET"),
    )

    decoded = cv2.imdecode(
        np.frombuffer(rendered, dtype=np.uint8),
        cv2.IMREAD_COLOR,
    )
    assert decoded is not None
    assert decoded.shape[1] == 640 * 3
    assert decoded.shape[0] == 480
