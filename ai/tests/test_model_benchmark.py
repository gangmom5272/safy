from pathlib import Path

from app.services.model_benchmark import (
    benchmark_count_model,
)
from app.services.model_evaluation import (
    EvaluationSample,
)


def test_benchmark_count_model_measures_records(
    tmp_path: Path,
) -> None:
    first_image = tmp_path / "first.jpg"
    second_image = tmp_path / "second.jpg"

    first_image.write_bytes(b"first")
    second_image.write_bytes(b"second")

    samples = [
        EvaluationSample(
            sample_id="first",
            scenario="SPARSE",
            image_path=first_image,
            ground_truth_count=4,
            source="test",
            license="internal",
        ),
        EvaluationSample(
            sample_id="second",
            scenario="CROWDED",
            image_path=second_image,
            ground_truth_count=10,
            source="test",
            license="internal",
        ),
    ]

    def load_predictor():
        def predict(image_bytes: bytes) -> int:
            if image_bytes == b"first":
                return 4

            return 8

        return predict

    clock_values = iter(
        [
            0.0,
            1.5,
            2.0,
            2.01,
            3.0,
            3.02,
        ]
    )

    def fake_clock() -> float:
        return next(clock_values)

    memory_values = iter([500.0, 600.0])

    records, runtime_profile = benchmark_count_model(
        samples=samples,
        model_name="FakeModel",
        model_version="v1",
        load_predictor=load_predictor,
        device="cuda",
        warmup_iterations=1,
        repetitions_per_sample=1,
        clock=fake_clock,
        synchronize=lambda: None,
        reset_peak_memory=lambda: None,
        get_peak_memory_mb=lambda: next(memory_values),
    )

    assert runtime_profile.initialization_time_ms == (1500.0)
    assert runtime_profile.warmup_iterations == 1
    assert runtime_profile.repetitions_per_sample == 1

    assert records[0].predicted_count == 4
    assert records[0].inference_time_ms == 10.0
    assert records[0].peak_gpu_memory_mb == 500.0

    assert records[1].predicted_count == 8
    assert records[1].absolute_error == 2
    assert records[1].inference_time_ms == 20.0
    assert records[1].peak_gpu_memory_mb == 600.0
