import json
from pathlib import Path

import pytest

from app.services import model_evaluation
from app.services.congestion import CongestionResult
from app.services.model_evaluation import (
    EvaluationRecord,
    EvaluationSample,
    evaluate_manifest,
    evaluate_sample,
    load_evaluation_manifest,
    save_evaluation_report,
    summarize_evaluations,
)
from app.services.person_detector import (
    PersonDetectionResult,
)


def test_summarize_evaluations_calculates_baseline_metrics() -> None:
    records = [
        EvaluationRecord(
            sample_id="empty",
            scenario="EMPTY",
            ground_truth_count=0,
            predicted_count=0,
            visual_congestion_score=0.0,
            local_peak_congestion_score=0.0,
            processing_time_ms=10,
        ),
        EvaluationRecord(
            sample_id="sparse",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=4,
            visual_congestion_score=1.88,
            local_peak_congestion_score=15.0,
            processing_time_ms=20,
        ),
        EvaluationRecord(
            sample_id="crowded",
            scenario="CROWDED",
            ground_truth_count=10,
            predicted_count=8,
            visual_congestion_score=20.0,
            local_peak_congestion_score=75.0,
            processing_time_ms=30,
        ),
    ]

    summary = summarize_evaluations(records)

    assert summary.sample_count == 3
    assert summary.total_ground_truth_count == 14
    assert summary.total_predicted_count == 12
    assert summary.mean_absolute_error == 0.67
    assert summary.mean_processing_time_ms == 20.0


def test_summarize_evaluations_rejects_empty_records() -> None:
    with pytest.raises(
        ValueError,
        match="평가 기록이 한 건 이상 필요합니다",
    ):
        summarize_evaluations([])


def test_load_evaluation_manifest_reads_samples(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "ground_truth_count,source,license\n"
            "empty,EMPTY,images/empty.png,"
            "0,generated,internal\n"
            "bus,SPARSE,images/bus.jpg,"
            "4,Ultralytics,AGPL-3.0\n"
        ),
        encoding="utf-8",
    )

    samples = load_evaluation_manifest(
        manifest_path,
    )

    assert len(samples) == 2
    assert samples[0].sample_id == "empty"
    assert samples[0].scenario == "EMPTY"
    assert samples[0].ground_truth_count == 0
    assert samples[0].image_path == (tmp_path / "images" / "empty.png")

    assert samples[1].sample_id == "bus"
    assert samples[1].scenario == "SPARSE"
    assert samples[1].ground_truth_count == 4
    assert samples[1].source == "Ultralytics"
    assert samples[1].license == "AGPL-3.0"


def test_load_evaluation_manifest_rejects_negative_count(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "ground_truth_count,source,license\n"
            "invalid,SPARSE,images/invalid.jpg,"
            "-1,test,internal\n"
        ),
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="정답 인원수는 0 이상이어야 합니다",
    ):
        load_evaluation_manifest(manifest_path)


def test_evaluate_sample_creates_evaluation_record(
    tmp_path: Path,
    monkeypatch,
) -> None:
    image_path = tmp_path / "sample.jpg"
    image_path.write_bytes(b"fake-image")

    sample = EvaluationSample(
        sample_id="sample-1",
        scenario="SPARSE",
        image_path=image_path,
        ground_truth_count=3,
        source="test",
        license="internal",
    )

    monkeypatch.setattr(
        model_evaluation,
        "detect_people",
        lambda image_bytes: PersonDetectionResult(
            person_count=2,
            confidences=[0.9, 0.8],
            processing_time_ms=25,
            person_centers=[
                (0.25, 0.5),
                (0.75, 0.5),
            ],
        ),
        raising=False,
    )
    monkeypatch.setattr(
        model_evaluation,
        "calculate_congestion",
        lambda person_centers: CongestionResult(
            visual_congestion_score=0.94,
            local_peak_congestion_score=15.0,
        ),
        raising=False,
    )

    record = evaluate_sample(sample)

    assert record.sample_id == "sample-1"
    assert record.scenario == "SPARSE"
    assert record.ground_truth_count == 3
    assert record.predicted_count == 2
    assert record.absolute_error == 1
    assert record.visual_congestion_score == 0.94
    assert record.local_peak_congestion_score == 15.0
    assert record.processing_time_ms == 25


def test_evaluate_manifest_evaluates_all_samples(
    tmp_path: Path,
    monkeypatch,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "ground_truth_count,source,license\n"
            "empty,EMPTY,images/empty.png,"
            "0,generated,internal\n"
            "sparse,SPARSE,images/sparse.jpg,"
            "4,test,internal\n"
        ),
        encoding="utf-8",
    )

    def fake_evaluate_sample(
        sample: EvaluationSample,
    ) -> EvaluationRecord:
        return EvaluationRecord(
            sample_id=sample.sample_id,
            scenario=sample.scenario,
            ground_truth_count=(sample.ground_truth_count),
            predicted_count=(sample.ground_truth_count),
            visual_congestion_score=0.0,
            local_peak_congestion_score=0.0,
            processing_time_ms=10,
        )

    monkeypatch.setattr(
        model_evaluation,
        "evaluate_sample",
        fake_evaluate_sample,
        raising=False,
    )

    records = evaluate_manifest(manifest_path)

    assert len(records) == 2
    assert records[0].sample_id == "empty"
    assert records[1].sample_id == "sparse"
    assert records[0].predicted_count == 0
    assert records[1].predicted_count == 4


def test_save_evaluation_report_writes_json(
    tmp_path: Path,
) -> None:
    records = [
        EvaluationRecord(
            sample_id="empty",
            scenario="EMPTY",
            ground_truth_count=0,
            predicted_count=0,
            visual_congestion_score=0.0,
            local_peak_congestion_score=0.0,
            processing_time_ms=10,
        ),
        EvaluationRecord(
            sample_id="crowded",
            scenario="CROWDED",
            ground_truth_count=10,
            predicted_count=8,
            visual_congestion_score=20.0,
            local_peak_congestion_score=75.0,
            processing_time_ms=30,
        ),
    ]
    output_path = tmp_path / "report.json"

    save_evaluation_report(
        records,
        output_path,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert report["model"] == {
        "name": "YOLO",
        "version": "yolo11n",
    }
    assert report["summary"] == {
        "sampleCount": 2,
        "totalGroundTruthCount": 10,
        "totalPredictedCount": 8,
        "meanAbsoluteError": 1.0,
        "meanProcessingTimeMs": 20.0,
    }
    assert report["scenarioSummaries"] == {
        "CROWDED": {
            "sampleCount": 1,
            "totalGroundTruthCount": 10,
            "totalPredictedCount": 8,
            "meanAbsoluteError": 2.0,
            "meanProcessingTimeMs": 30.0,
        },
        "EMPTY": {
            "sampleCount": 1,
            "totalGroundTruthCount": 0,
            "totalPredictedCount": 0,
            "meanAbsoluteError": 0.0,
            "meanProcessingTimeMs": 10.0,
        },
    }
    assert report["records"][0]["sampleId"] == "empty"
    assert report["records"][0]["absoluteError"] == 0
    assert report["records"][1]["sampleId"] == "crowded"
    assert report["records"][1]["absoluteError"] == 2
    assert report["records"][1]["localPeakCongestionScore"] == 75.0


def test_load_evaluation_manifest_rejects_duplicate_ids(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "ground_truth_count,source,license\n"
            "same,EMPTY,images/empty.png,"
            "0,generated,internal\n"
            "same,SPARSE,images/sparse.jpg,"
            "4,test,internal\n"
        ),
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="sample_id는 중복될 수 없습니다",
    ):
        load_evaluation_manifest(manifest_path)
