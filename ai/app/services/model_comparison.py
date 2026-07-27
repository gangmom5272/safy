import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ModelComparisonRecord:
    model_name: str
    model_version: str
    sample_id: str
    scenario: str
    ground_truth_count: int
    predicted_count: int
    inference_time_ms: float
    peak_gpu_memory_mb: float

    @property
    def model_key(self) -> str:
        return f"{self.model_name}:{self.model_version}"

    @property
    def absolute_error(self) -> int:
        return abs(self.ground_truth_count - self.predicted_count)


@dataclass(frozen=True)
class ModelComparisonSummary:
    model_name: str
    model_version: str
    sample_count: int
    mean_absolute_error: float
    mean_inference_time_ms: float
    peak_gpu_memory_mb: float


@dataclass(frozen=True)
class ModelRuntimeProfile:
    model_name: str
    model_version: str
    device: str
    initialization_time_ms: float
    warmup_iterations: int
    repetitions_per_sample: int

    @property
    def model_key(self) -> str:
        return f"{self.model_name}:{self.model_version}"


def summarize_by_model(
    records: list[ModelComparisonRecord],
) -> dict[str, ModelComparisonSummary]:
    records_by_model: dict[
        str,
        list[ModelComparisonRecord],
    ] = {}

    for record in records:
        records_by_model.setdefault(
            record.model_key,
            [],
        ).append(record)

    summaries: dict[
        str,
        ModelComparisonSummary,
    ] = {}

    for model_key, model_records in records_by_model.items():
        sample_count = len(model_records)

        summaries[model_key] = ModelComparisonSummary(
            model_name=(model_records[0].model_name),
            model_version=(model_records[0].model_version),
            sample_count=sample_count,
            mean_absolute_error=round(
                sum(record.absolute_error for record in model_records) / sample_count,
                2,
            ),
            mean_inference_time_ms=round(
                sum(record.inference_time_ms for record in model_records)
                / sample_count,
                2,
            ),
            peak_gpu_memory_mb=round(
                max(record.peak_gpu_memory_mb for record in model_records),
                2,
            ),
        )

    return summaries


def validate_comparison_records(
    records: list[ModelComparisonRecord],
) -> None:
    if not records:
        raise ValueError("비교 기록이 한 건 이상 필요합니다.")

    sample_ids_by_model: dict[
        str,
        set[str],
    ] = {}
    metadata_by_sample: dict[
        str,
        tuple[str, int],
    ] = {}

    for record in records:
        sample_ids_by_model.setdefault(
            record.model_key,
            set(),
        ).add(record.sample_id)

        sample_metadata = (
            record.scenario,
            record.ground_truth_count,
        )

        existing_metadata = metadata_by_sample.get(record.sample_id)

        if existing_metadata is not None and existing_metadata != sample_metadata:
            raise ValueError("동일한 표본의 정답 정보가 일치해야 합니다.")

        metadata_by_sample[record.sample_id] = sample_metadata

    expected_sample_ids = next(iter(sample_ids_by_model.values()))

    if any(
        sample_ids != expected_sample_ids for sample_ids in sample_ids_by_model.values()
    ):
        raise ValueError("모든 모델은 동일한 표본을 포함해야 합니다.")


def _summary_to_dict(
    summary: ModelComparisonSummary,
) -> dict[str, str | int | float]:
    return {
        "modelName": summary.model_name,
        "modelVersion": summary.model_version,
        "sampleCount": summary.sample_count,
        "meanAbsoluteError": (summary.mean_absolute_error),
        "meanInferenceTimeMs": (summary.mean_inference_time_ms),
        "peakGpuMemoryMb": (summary.peak_gpu_memory_mb),
    }


def _runtime_profile_to_dict(
    profile: ModelRuntimeProfile,
) -> dict[str, str | int | float]:
    return {
        "modelName": profile.model_name,
        "modelVersion": profile.model_version,
        "device": profile.device,
        "initializationTimeMs": (profile.initialization_time_ms),
        "warmupIterations": (profile.warmup_iterations),
        "repetitionsPerSample": (profile.repetitions_per_sample),
    }


def save_model_comparison_report(
    records: list[ModelComparisonRecord],
    output_path: Path,
    runtime_profiles: (list[ModelRuntimeProfile] | None) = None,
) -> None:
    validate_comparison_records(records)

    summaries = summarize_by_model(records)
    profiles = runtime_profiles or []

    records_by_model_and_scenario: dict[
        str,
        dict[
            str,
            list[ModelComparisonRecord],
        ],
    ] = {}

    for record in records:
        records_by_model_and_scenario.setdefault(
            record.model_key,
            {},
        ).setdefault(
            record.scenario,
            [],
        ).append(record)

    scenario_summaries: dict[
        str,
        dict[
            str,
            dict[str, str | int | float],
        ],
    ] = {}

    for model_key, records_by_scenario in sorted(records_by_model_and_scenario.items()):
        scenario_summaries[model_key] = {}

        for scenario, scenario_records in sorted(records_by_scenario.items()):
            scenario_summary = summarize_by_model(scenario_records)[model_key]

            scenario_summaries[model_key][scenario] = _summary_to_dict(scenario_summary)

    report = {
        "comparison": {
            "modelCount": len(summaries),
            "sampleCount": len({record.sample_id for record in records}),
        },
        "runtimeProfiles": {
            profile.model_key: (_runtime_profile_to_dict(profile))
            for profile in profiles
        },
        "scenarioSummaries": scenario_summaries,
        "modelSummaries": {
            model_key: _summary_to_dict(summary)
            for model_key, summary in sorted(summaries.items())
        },
        "records": [
            {
                "modelName": (record.model_name),
                "modelVersion": (record.model_version),
                "sampleId": record.sample_id,
                "scenario": record.scenario,
                "groundTruthCount": (record.ground_truth_count),
                "predictedCount": (record.predicted_count),
                "absoluteError": (record.absolute_error),
                "inferenceTimeMs": (record.inference_time_ms),
                "peakGpuMemoryMb": (record.peak_gpu_memory_mb),
            }
            for record in records
        ],
    }

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    output_path.write_text(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def load_model_comparison_report(
    report_path: Path,
) -> tuple[
    list[ModelComparisonRecord],
    list[ModelRuntimeProfile],
]:
    report = json.loads(report_path.read_text(encoding="utf-8"))

    records = [
        ModelComparisonRecord(
            model_name=row["modelName"],
            model_version=row["modelVersion"],
            sample_id=row["sampleId"],
            scenario=row["scenario"],
            ground_truth_count=(row["groundTruthCount"]),
            predicted_count=(row["predictedCount"]),
            inference_time_ms=(row["inferenceTimeMs"]),
            peak_gpu_memory_mb=(row["peakGpuMemoryMb"]),
        )
        for row in report["records"]
    ]

    runtime_profiles = [
        ModelRuntimeProfile(
            model_name=row["modelName"],
            model_version=row["modelVersion"],
            device=row["device"],
            initialization_time_ms=(row["initializationTimeMs"]),
            warmup_iterations=(row["warmupIterations"]),
            repetitions_per_sample=(row["repetitionsPerSample"]),
        )
        for row in report.get(
            "runtimeProfiles",
            {},
        ).values()
    ]

    return records, runtime_profiles
