from dataclasses import dataclass
from pathlib import Path

from app.services.congestion import (
    calculate_congestion,
)
from app.services.model_evaluation import (
    EvaluationRecord,
    EvaluationSample,
    load_evaluation_manifest,
)
from app.services.p2pnet_benchmark_adapter import (
    P2PNetDetector,
)


@dataclass(frozen=True)
class P2PNetCongestionResult:
    person_count: int
    confidences: list[float]
    person_centers: list[tuple[float, float]]
    visual_congestion_score: float
    local_peak_congestion_score: float
    processing_time_ms: int


def analyze_p2pnet_congestion(
    *,
    image_bytes: bytes,
    detector: P2PNetDetector,
) -> P2PNetCongestionResult:
    detection_result = detector(
        image_bytes,
    )
    congestion_result = calculate_congestion(
        detection_result.person_centers,
    )

    return P2PNetCongestionResult(
        person_count=(detection_result.person_count),
        confidences=detection_result.confidences,
        person_centers=(detection_result.person_centers),
        visual_congestion_score=(congestion_result.visual_congestion_score),
        local_peak_congestion_score=(congestion_result.local_peak_congestion_score),
        processing_time_ms=(detection_result.processing_time_ms),
    )


def evaluate_p2pnet_sample(
    *,
    sample: EvaluationSample,
    detector: P2PNetDetector,
) -> EvaluationRecord:
    result = analyze_p2pnet_congestion(
        image_bytes=sample.image_path.read_bytes(),
        detector=detector,
    )

    return EvaluationRecord(
        sample_id=sample.sample_id,
        scenario=sample.scenario,
        ground_truth_count=(sample.ground_truth_count),
        predicted_count=result.person_count,
        visual_congestion_score=(result.visual_congestion_score),
        local_peak_congestion_score=(result.local_peak_congestion_score),
        processing_time_ms=(result.processing_time_ms),
    )


def evaluate_p2pnet_manifest(
    *,
    manifest_path: Path,
    detector: P2PNetDetector,
) -> list[EvaluationRecord]:
    samples = load_evaluation_manifest(
        manifest_path,
    )

    return [
        evaluate_p2pnet_sample(
            sample=sample,
            detector=detector,
        )
        for sample in samples
    ]
