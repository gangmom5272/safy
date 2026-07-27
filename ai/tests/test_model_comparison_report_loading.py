from pathlib import Path

from app.services.model_comparison import (
    ModelComparisonRecord,
    ModelRuntimeProfile,
    load_model_comparison_report,
    save_model_comparison_report,
)


def test_load_model_comparison_report_round_trip(
    tmp_path: Path,
) -> None:
    records = [
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="sample-1",
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
            initialization_time_ms=1000.0,
            warmup_iterations=1,
            repetitions_per_sample=3,
        )
    ]

    report_path = tmp_path / "report.json"

    save_model_comparison_report(
        records,
        report_path,
        runtime_profiles=runtime_profiles,
    )

    loaded_records, loaded_profiles = load_model_comparison_report(report_path)

    assert loaded_records == records
    assert loaded_profiles == runtime_profiles
