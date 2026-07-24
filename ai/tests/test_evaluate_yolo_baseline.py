import json
from pathlib import Path

from app.services.model_evaluation import (
    EvaluationRecord,
)
from scripts import evaluate_yolo_baseline


def test_run_evaluation_saves_report(
    tmp_path: Path,
    monkeypatch,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    output_path = tmp_path / "report.json"

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
            predicted_count=3,
            visual_congestion_score=1.41,
            local_peak_congestion_score=15.0,
            processing_time_ms=20,
        ),
    ]

    monkeypatch.setattr(
        evaluate_yolo_baseline,
        "evaluate_manifest",
        lambda path: records,
        raising=False,
    )

    summary = evaluate_yolo_baseline.run_evaluation(
        manifest_path=manifest_path,
        output_path=output_path,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert summary.sample_count == 2
    assert summary.mean_absolute_error == 0.5
    assert summary.mean_processing_time_ms == 15.0
    assert report["summary"]["sampleCount"] == 2
    assert len(report["records"]) == 2
