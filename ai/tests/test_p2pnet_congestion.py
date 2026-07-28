from pathlib import Path

from app.services.model_evaluation import (
    EvaluationSample,
)

from app.services.p2pnet_benchmark_adapter import (
    P2PNetDetectionResult,
)
from app.services.p2pnet_congestion import (
    analyze_p2pnet_congestion,
    evaluate_p2pnet_manifest,
    evaluate_p2pnet_sample,
)


def fake_detector(
    _: bytes,
) -> P2PNetDetectionResult:
    return P2PNetDetectionResult(
        person_count=2,
        confidences=[
            0.9,
            0.8,
        ],
        person_centers=[
            (
                0.10,
                0.10,
            ),
            (
                0.11,
                0.11,
            ),
        ],
        processing_time_ms=15,
    )


def test_analyze_p2pnet_congestion_returns_count_and_scores() -> None:
    result = analyze_p2pnet_congestion(
        image_bytes=b"fake-image",
        detector=fake_detector,
    )

    assert result.person_count == 2
    assert result.confidences == [
        0.9,
        0.8,
    ]
    assert result.person_centers == [
        (
            0.10,
            0.10,
        ),
        (
            0.11,
            0.11,
        ),
    ]
    assert result.visual_congestion_score == 0.94
    assert result.local_peak_congestion_score == 15.0
    assert result.processing_time_ms == 15


def test_evaluate_p2pnet_sample_returns_evaluation_record(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "sample.jpg"
    image_path.write_bytes(
        b"fake-image",
    )

    sample = EvaluationSample(
        sample_id="local-dense-1",
        scenario="LOCAL_DENSE",
        image_path=image_path,
        ground_truth_count=5,
        source="test",
        license="internal",
    )

    record = evaluate_p2pnet_sample(
        sample=sample,
        detector=fake_detector,
    )

    assert record.sample_id == "local-dense-1"
    assert record.scenario == "LOCAL_DENSE"
    assert record.ground_truth_count == 5
    assert record.predicted_count == 2
    assert record.absolute_error == 3
    assert record.visual_congestion_score == 0.94
    assert record.local_peak_congestion_score == 15.0
    assert record.processing_time_ms == 15


def test_evaluate_p2pnet_manifest_returns_all_records(
    tmp_path: Path,
) -> None:
    images_directory = tmp_path / "images"
    images_directory.mkdir()

    first_image = images_directory / "first.jpg"
    second_image = images_directory / "second.jpg"

    first_image.write_bytes(
        b"first-image",
    )
    second_image.write_bytes(
        b"second-image",
    )

    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "ground_truth_count,source,license\n"
            "first,SPARSE,images/first.jpg,"
            "1,test,internal\n"
            "second,LOCAL_DENSE,images/second.jpg,"
            "5,test,internal\n"
        ),
        encoding="utf-8",
    )

    records = evaluate_p2pnet_manifest(
        manifest_path=manifest_path,
        detector=fake_detector,
    )

    assert [record.sample_id for record in records] == [
        "first",
        "second",
    ]
    assert [record.predicted_count for record in records] == [
        2,
        2,
    ]
    assert [record.absolute_error for record in records] == [
        1,
        3,
    ]
