import csv
import hashlib
import json
from dataclasses import dataclass, replace
from pathlib import Path

from PIL import Image

from app.services.congestion import calculate_congestion
from app.services.head_point_dataset import HeadPointSample
from app.services.head_point_model_evaluation import (
    HeadPointEvaluationRecord,
    compute_ground_truth_congestion,
    determine_large_false_positive,
    evaluate_head_point_manifest,
    evaluate_head_point_sample,
    save_holdout_evaluation_report,
    summarize_by_scenario_and_macro,
)


@dataclass(frozen=True)
class _FakeDetectionResult:
    person_count: int
    person_centers: list[tuple[float, float]]
    processing_time_ms: int


def _make_fake_detector(result: _FakeDetectionResult):
    def detect(image_bytes: bytes) -> _FakeDetectionResult:
        return result

    return detect


def _write_sample(
    tmp_path: Path,
    *,
    sample_id: str,
    scenario: str,
    points: list[tuple[float, float]],
    image_width: int = 640,
    image_height: int = 480,
) -> HeadPointSample:
    image_path = tmp_path / f"{sample_id}.jpg"
    annotation_path = tmp_path / f"{sample_id}.points.csv"

    unique_color = (
        int(hashlib.sha1(sample_id.encode()).hexdigest()[:2], 16),
        int(hashlib.sha1(sample_id.encode()).hexdigest()[2:4], 16),
        int(hashlib.sha1(sample_id.encode()).hexdigest()[4:6], 16),
    )
    Image.new("RGB", (image_width, image_height), color=unique_color).save(image_path)

    with annotation_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["x", "y"])
        for point_x, point_y in points:
            writer.writerow([point_x, point_y])

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    return HeadPointSample(
        sample_id=sample_id,
        source="JHU-CROWD++ v2.0",
        source_group=sample_id,
        split="TEST",
        image_path=image_path,
        annotation_path=annotation_path,
        image_width=image_width,
        image_height=image_height,
        person_count=len(points),
        scenario=scenario,
        license="jhu-crowd-noncommercial",
        consent_confirmed=False,
        image_sha256=image_sha256,
        label_format_version="head-point-v1",
    )


def test_compute_ground_truth_congestion_matches_normalized_points(
    tmp_path: Path,
) -> None:
    points = [(64.0, 48.0), (576.0, 432.0)]
    sample = _write_sample(
        tmp_path,
        sample_id="sample-1",
        scenario="SPARSE",
        points=points,
    )

    result = compute_ground_truth_congestion(sample)

    expected = calculate_congestion(
        [
            (64.0 / 640, 48.0 / 480),
            (576.0 / 640, 432.0 / 480),
        ]
    )

    assert result == expected


def test_determine_large_false_positive_flags_low_density_overcount() -> None:
    assert (
        determine_large_false_positive(
            scenario="SPARSE",
            ground_truth_count=2,
            predicted_count=40,
        )
        is True
    )


def test_determine_large_false_positive_ignores_small_excess() -> None:
    assert (
        determine_large_false_positive(
            scenario="NORMAL",
            ground_truth_count=10,
            predicted_count=15,
        )
        is False
    )


def test_determine_large_false_positive_ignores_crowded_scenario() -> None:
    assert (
        determine_large_false_positive(
            scenario="CROWDED",
            ground_truth_count=100,
            predicted_count=500,
        )
        is False
    )


def test_determine_large_false_positive_handles_zero_ground_truth() -> None:
    assert (
        determine_large_false_positive(
            scenario="EMPTY",
            ground_truth_count=0,
            predicted_count=25,
        )
        is True
    )
    assert (
        determine_large_false_positive(
            scenario="EMPTY",
            ground_truth_count=0,
            predicted_count=5,
        )
        is False
    )


def test_evaluate_head_point_sample_builds_record(tmp_path: Path) -> None:
    sample = _write_sample(
        tmp_path,
        sample_id="sample-1",
        scenario="SPARSE",
        points=[(64.0, 48.0), (576.0, 432.0)],
    )
    detector = _make_fake_detector(
        _FakeDetectionResult(
            person_count=1,
            person_centers=[(0.1, 0.1)],
            processing_time_ms=12,
        )
    )

    record = evaluate_head_point_sample(sample=sample, detect=detector)

    assert record.sample_id == "sample-1"
    assert record.scenario == "SPARSE"
    assert record.ground_truth_count == 2
    assert record.predicted_count == 1
    assert record.absolute_error == 1
    assert record.processing_time_ms == 12


def test_evaluate_head_point_manifest_filters_by_split(tmp_path: Path) -> None:
    from app.services.p2pnet_finetune_split import save_p2pnet_finetune_manifest

    train_sample = _write_sample(
        tmp_path,
        sample_id="train-1",
        scenario="NORMAL",
        points=[(10.0, 10.0)],
    )
    test_sample = _write_sample(
        tmp_path,
        sample_id="test-1",
        scenario="SPARSE",
        points=[(10.0, 10.0), (20.0, 20.0)],
    )
    from dataclasses import replace

    train_sample = replace(train_sample, split="TRAIN")
    test_sample = replace(test_sample, split="TEST")

    manifest_path = tmp_path / "manifest" / "manifest.csv"
    save_p2pnet_finetune_manifest(
        samples=[train_sample, test_sample],
        output_path=manifest_path,
    )

    detector = _make_fake_detector(
        _FakeDetectionResult(
            person_count=2,
            person_centers=[(0.1, 0.1), (0.2, 0.2)],
            processing_time_ms=5,
        )
    )

    records = evaluate_head_point_manifest(
        manifest_path=manifest_path,
        detect=detector,
        split="TEST",
    )

    assert len(records) == 1
    assert records[0].sample_id == "test-1"


def _make_record(
    *,
    sample_id: str,
    scenario: str,
    ground_truth_count: int,
    predicted_count: int,
    ground_truth_c: float = 0.0,
    ground_truth_l: float = 0.0,
    predicted_c: float = 0.0,
    predicted_l: float = 0.0,
    processing_time_ms: int = 10,
) -> HeadPointEvaluationRecord:
    return HeadPointEvaluationRecord(
        sample_id=sample_id,
        scenario=scenario,
        ground_truth_count=ground_truth_count,
        predicted_count=predicted_count,
        ground_truth_visual_congestion_score=ground_truth_c,
        ground_truth_local_peak_congestion_score=ground_truth_l,
        predicted_visual_congestion_score=predicted_c,
        predicted_local_peak_congestion_score=predicted_l,
        processing_time_ms=processing_time_ms,
        is_large_false_positive=False,
    )


def test_summarize_by_scenario_and_macro_computes_unweighted_macro_average() -> None:
    records = [
        _make_record(
            sample_id="crowded-1",
            scenario="CROWDED",
            ground_truth_count=100,
            predicted_count=110,
        ),
        _make_record(
            sample_id="crowded-2",
            scenario="CROWDED",
            ground_truth_count=200,
            predicted_count=210,
        ),
        _make_record(
            sample_id="crowded-3",
            scenario="CROWDED",
            ground_truth_count=300,
            predicted_count=310,
        ),
        _make_record(
            sample_id="crowded-4",
            scenario="CROWDED",
            ground_truth_count=400,
            predicted_count=410,
        ),
        _make_record(
            sample_id="sparse-1",
            scenario="SPARSE",
            ground_truth_count=2,
            predicted_count=42,
        ),
    ]

    summary = summarize_by_scenario_and_macro(
        model_name="YOLO",
        model_version="yolo11n",
        records=records,
    )

    assert summary.by_scenario["CROWDED"].mean_absolute_error == 10.0
    assert summary.by_scenario["SPARSE"].mean_absolute_error == 40.0

    # 시나리오 표본 수가 4:1로 불균형하므로 micro(overall) 평균과
    # macro(시나리오 단순 평균)이 달라야 한다.
    assert summary.overall.mean_absolute_error != summary.macro_mean_absolute_error
    assert summary.macro_mean_absolute_error == 25.0


def test_summarize_by_scenario_and_macro_counts_large_false_positives() -> None:
    flagged_record = replace(
        _make_record(
            sample_id="sparse-1",
            scenario="SPARSE",
            ground_truth_count=0,
            predicted_count=30,
        ),
        is_large_false_positive=True,
    )

    summary = summarize_by_scenario_and_macro(
        model_name="P2PNet",
        model_version="safy-p2pnet-v2-epoch3",
        records=[flagged_record],
    )

    assert summary.by_scenario["SPARSE"].large_false_positive_count == 1
    assert summary.overall.large_false_positive_count == 1


def test_save_holdout_evaluation_report_writes_expected_structure(
    tmp_path: Path,
) -> None:
    records = [
        _make_record(
            sample_id="crowded-1",
            scenario="CROWDED",
            ground_truth_count=100,
            predicted_count=110,
        ),
    ]
    summary = summarize_by_scenario_and_macro(
        model_name="YOLO",
        model_version="yolo11n",
        records=records,
    )

    output_path = tmp_path / "report.json"
    save_holdout_evaluation_report(
        summary=summary,
        records=records,
        output_path=output_path,
    )

    payload = json.loads(output_path.read_text(encoding="utf-8"))

    assert payload["model"] == {"name": "YOLO", "version": "yolo11n"}
    assert payload["macroMeanAbsoluteError"] == summary.macro_mean_absolute_error
    assert payload["byScenario"]["CROWDED"]["sampleCount"] == 1
    assert len(payload["records"]) == 1
