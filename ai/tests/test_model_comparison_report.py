import json
from pathlib import Path

from app.services.model_comparison import (
    ModelComparisonRecord,
    save_model_comparison_report,
)


def test_save_model_comparison_report_writes_json(
    tmp_path: Path,
) -> None:
    records = [
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="crowd-001",
            scenario="CROWDED",
            ground_truth_count=100,
            predicted_count=80,
            inference_time_ms=20.0,
            peak_gpu_memory_mb=600.0,
        ),
        ModelComparisonRecord(
            model_name="P2PNet",
            model_version="SHTechA",
            sample_id="crowd-001",
            scenario="CROWDED",
            ground_truth_count=100,
            predicted_count=95,
            inference_time_ms=50.0,
            peak_gpu_memory_mb=900.0,
        ),
    ]
    output_path = tmp_path / "comparison.json"

    save_model_comparison_report(
        records,
        output_path,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert report["comparison"] == {
        "modelCount": 2,
        "sampleCount": 1,
    }

    assert report["modelSummaries"]["YOLO:yolo11n"] == {
        "modelName": "YOLO",
        "modelVersion": "yolo11n",
        "sampleCount": 1,
        "meanAbsoluteError": 20.0,
        "meanInferenceTimeMs": 20.0,
        "peakGpuMemoryMb": 600.0,
    }

    assert report["modelSummaries"]["P2PNet:SHTechA"]["meanAbsoluteError"] == 5.0

    assert report["records"][0]["sampleId"] == "crowd-001"

    assert report["records"][0]["absoluteError"] == 20
