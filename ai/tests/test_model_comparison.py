import pytest

from app.services.model_comparison import (
    ModelComparisonRecord,
    summarize_by_model,
    validate_comparison_records,
)


def test_summarize_by_model_compares_models_independently() -> None:
    records = [
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="sparse",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=4,
            inference_time_ms=10.0,
            peak_gpu_memory_mb=500.0,
        ),
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="dense",
            scenario="LOCAL_DENSE",
            ground_truth_count=10,
            predicted_count=8,
            inference_time_ms=20.0,
            peak_gpu_memory_mb=600.0,
        ),
        ModelComparisonRecord(
            model_name="P2PNet",
            model_version="SHTechA",
            sample_id="sparse",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=3,
            inference_time_ms=30.0,
            peak_gpu_memory_mb=800.0,
        ),
        ModelComparisonRecord(
            model_name="P2PNet",
            model_version="SHTechA",
            sample_id="dense",
            scenario="LOCAL_DENSE",
            ground_truth_count=10,
            predicted_count=10,
            inference_time_ms=50.0,
            peak_gpu_memory_mb=900.0,
        ),
    ]

    summaries = summarize_by_model(records)

    yolo_summary = summaries["YOLO:yolo11n"]
    p2pnet_summary = summaries["P2PNet:SHTechA"]

    assert yolo_summary.sample_count == 2
    assert yolo_summary.mean_absolute_error == 1.0
    assert yolo_summary.mean_inference_time_ms == 15.0
    assert yolo_summary.peak_gpu_memory_mb == 600.0

    assert p2pnet_summary.sample_count == 2
    assert p2pnet_summary.mean_absolute_error == 0.5
    assert p2pnet_summary.mean_inference_time_ms == 40.0
    assert p2pnet_summary.peak_gpu_memory_mb == 900.0


def test_validate_comparison_rejects_different_samples() -> None:
    records = [
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="sparse",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=4,
            inference_time_ms=10.0,
            peak_gpu_memory_mb=500.0,
        ),
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="dense",
            scenario="LOCAL_DENSE",
            ground_truth_count=10,
            predicted_count=8,
            inference_time_ms=20.0,
            peak_gpu_memory_mb=600.0,
        ),
        ModelComparisonRecord(
            model_name="P2PNet",
            model_version="SHTechA",
            sample_id="sparse",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=3,
            inference_time_ms=30.0,
            peak_gpu_memory_mb=800.0,
        ),
    ]

    with pytest.raises(
        ValueError,
        match="모든 모델은 동일한 표본을 포함해야 합니다",
    ):
        validate_comparison_records(records)


def test_validate_comparison_rejects_different_ground_truth() -> None:
    records = [
        ModelComparisonRecord(
            model_name="YOLO",
            model_version="yolo11n",
            sample_id="same-image",
            scenario="SPARSE",
            ground_truth_count=4,
            predicted_count=4,
            inference_time_ms=10.0,
            peak_gpu_memory_mb=500.0,
        ),
        ModelComparisonRecord(
            model_name="P2PNet",
            model_version="SHTechA",
            sample_id="same-image",
            scenario="SPARSE",
            ground_truth_count=5,
            predicted_count=4,
            inference_time_ms=30.0,
            peak_gpu_memory_mb=800.0,
        ),
    ]

    with pytest.raises(
        ValueError,
        match="동일한 표본의 정답 정보가 일치해야 합니다",
    ):
        validate_comparison_records(records)
