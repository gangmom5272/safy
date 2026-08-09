import json
from pathlib import Path

from app.services.model_comparison import (
    ModelComparisonRecord,
    save_model_comparison_report,
)


def test_report_summarizes_each_scenario(
    tmp_path: Path,
) -> None:
    records = [
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="sparse-1",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=4,
            inference_time_ms=10.0,
            peak_gpu_memory_mb=500.0,
        ),
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="crowded-1",
            scenario="CROWDED",
            ground_truth_count=100,
            predicted_count=80,
            inference_time_ms=20.0,
            peak_gpu_memory_mb=600.0,
        ),
        ModelComparisonRecord(
            model_name="P2PNet",
            model_version="SHTechA",
            sample_id="sparse-1",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=3,
            inference_time_ms=30.0,
            peak_gpu_memory_mb=800.0,
        ),
        ModelComparisonRecord(
            model_name="P2PNet",
            model_version="SHTechA",
            sample_id="crowded-1",
            scenario="CROWDED",
            ground_truth_count=100,
            predicted_count=95,
            inference_time_ms=50.0,
            peak_gpu_memory_mb=900.0,
        ),
    ]

    output_path = tmp_path / "report.json"

    save_model_comparison_report(
        records,
        output_path,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert (
        report["scenarioSummaries"]["YOLO:yolo11n"]["SPARSE"]["meanAbsoluteError"]
        == 0.0
    )

    assert (
        report["scenarioSummaries"]["YOLO:yolo11n"]["CROWDED"]["meanAbsoluteError"]
        == 20.0
    )

    assert (
        report["scenarioSummaries"]["P2PNet:SHTechA"]["SPARSE"]["meanAbsoluteError"]
        == 1.0
    )

    assert (
        report["scenarioSummaries"]["P2PNet:SHTechA"]["CROWDED"]["meanAbsoluteError"]
        == 5.0
    )
