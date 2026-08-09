import json
from pathlib import Path

from app.services.model_comparison import (
    ModelComparisonRecord,
    ModelRuntimeProfile,
    save_model_comparison_report,
)


def test_report_includes_runtime_profile(
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
        )
    ]

    runtime_profiles = [
        ModelRuntimeProfile(
            model_name="YOLO",
            model_version="yolo11n",
            device="cuda",
            initialization_time_ms=1500.0,
            warmup_iterations=1,
            repetitions_per_sample=3,
        )
    ]

    output_path = tmp_path / "comparison.json"

    save_model_comparison_report(
        records,
        output_path,
        runtime_profiles=runtime_profiles,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert report["runtimeProfiles"]["YOLO:yolo11n"] == {
        "modelName": "YOLO",
        "modelVersion": "yolo11n",
        "device": "cuda",
        "initializationTimeMs": 1500.0,
        "warmupIterations": 1,
        "repetitionsPerSample": 3,
    }
