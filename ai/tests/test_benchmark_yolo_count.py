from pathlib import Path

from app.services.model_comparison import (
    ModelComparisonRecord,
    ModelRuntimeProfile,
)
from scripts import benchmark_yolo_count


def test_run_benchmark_uses_requested_limit(
    tmp_path: Path,
    monkeypatch,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    output_path = tmp_path / "report.json"

    fake_samples = [
        object(),
        object(),
    ]

    monkeypatch.setattr(
        benchmark_yolo_count,
        "load_evaluation_manifest",
        lambda path: fake_samples,
    )

    record = ModelComparisonRecord(
        model_name="YOLO",
        model_version="yolo11n",
        sample_id="sample-1",
        scenario="CROWDED",
        ground_truth_count=100,
        predicted_count=80,
        inference_time_ms=20.0,
        peak_gpu_memory_mb=600.0,
    )
    profile = ModelRuntimeProfile(
        model_name="YOLO",
        model_version="yolo11n",
        device="cuda",
        initialization_time_ms=1000.0,
        warmup_iterations=1,
        repetitions_per_sample=3,
    )

    received_samples: list[object] = []

    def fake_benchmark(**kwargs):
        received_samples.extend(kwargs["samples"])
        return [record], profile

    monkeypatch.setattr(
        benchmark_yolo_count,
        "benchmark_count_model",
        fake_benchmark,
    )

    saved_values: list[object] = []

    def fake_save(
        records,
        path,
        runtime_profiles,
    ) -> None:
        saved_values.extend(
            [
                records,
                path,
                runtime_profiles,
            ]
        )

    monkeypatch.setattr(
        benchmark_yolo_count,
        "save_model_comparison_report",
        fake_save,
    )

    records, runtime_profile = benchmark_yolo_count.run_benchmark(
        manifest_path=manifest_path,
        output_path=output_path,
        limit=1,
        warmup_iterations=1,
        repetitions_per_sample=3,
    )

    assert received_samples == [fake_samples[0]]
    assert records == [record]
    assert runtime_profile == profile
    assert saved_values == [
        [record],
        output_path,
        [profile],
    ]
