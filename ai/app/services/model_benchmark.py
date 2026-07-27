import time
from collections.abc import Callable

from app.services.model_comparison import (
    ModelComparisonRecord,
    ModelRuntimeProfile,
)
from app.services.model_evaluation import (
    EvaluationSample,
)

CountPredictor = Callable[[bytes], int]
PredictorLoader = Callable[[], CountPredictor]
Clock = Callable[[], float]
VoidCallback = Callable[[], None]
MemoryReader = Callable[[], float]


def _do_nothing() -> None:
    return None


def _zero_memory() -> float:
    return 0.0


def benchmark_count_model(
    *,
    samples: list[EvaluationSample],
    model_name: str,
    model_version: str,
    load_predictor: PredictorLoader,
    device: str,
    warmup_iterations: int = 1,
    repetitions_per_sample: int = 3,
    clock: Clock = time.perf_counter,
    synchronize: VoidCallback = _do_nothing,
    reset_peak_memory: VoidCallback = (_do_nothing),
    get_peak_memory_mb: MemoryReader = (_zero_memory),
) -> tuple[
    list[ModelComparisonRecord],
    ModelRuntimeProfile,
]:
    if not samples:
        raise ValueError("평가 표본이 한 건 이상 필요합니다.")

    if warmup_iterations < 0:
        raise ValueError("워밍업 횟수는 0 이상이어야 합니다.")

    if repetitions_per_sample < 1:
        raise ValueError("반복 측정 횟수는 1 이상이어야 합니다.")

    initialization_started_at = clock()
    predictor = load_predictor()
    synchronize()
    initialization_finished_at = clock()

    initialization_time_ms = round(
        (initialization_finished_at - initialization_started_at) * 1000,
        2,
    )

    warmup_image_bytes = samples[0].image_path.read_bytes()

    for _ in range(warmup_iterations):
        predictor(warmup_image_bytes)
        synchronize()

    records: list[ModelComparisonRecord] = []

    for sample in samples:
        image_bytes = sample.image_path.read_bytes()

        inference_times_ms: list[float] = []
        predicted_counts: list[int] = []

        reset_peak_memory()

        for _ in range(repetitions_per_sample):
            synchronize()
            inference_started_at = clock()

            predicted_count = predictor(image_bytes)

            synchronize()
            inference_finished_at = clock()

            inference_times_ms.append(
                (inference_finished_at - inference_started_at) * 1000
            )
            predicted_counts.append(predicted_count)

        if len(set(predicted_counts)) != 1:
            raise ValueError("동일한 표본의 반복 예측 결과가 일치하지 않습니다.")

        records.append(
            ModelComparisonRecord(
                model_name=model_name,
                model_version=model_version,
                sample_id=sample.sample_id,
                scenario=sample.scenario,
                ground_truth_count=(sample.ground_truth_count),
                predicted_count=(predicted_counts[0]),
                inference_time_ms=round(
                    sum(inference_times_ms) / len(inference_times_ms),
                    2,
                ),
                peak_gpu_memory_mb=round(
                    get_peak_memory_mb(),
                    2,
                ),
            )
        )

    runtime_profile = ModelRuntimeProfile(
        model_name=model_name,
        model_version=model_version,
        device=device,
        initialization_time_ms=(initialization_time_ms),
        warmup_iterations=warmup_iterations,
        repetitions_per_sample=(repetitions_per_sample),
    )

    return records, runtime_profile
