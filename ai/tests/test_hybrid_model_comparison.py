import json
from pathlib import Path

from app.services.hybrid_model_comparison import (
    AnalysisMetrics,
    ModelRunner,
    compare_sample,
    load_hybrid_evaluation_samples,
    save_hybrid_model_comparison_report,
    summarize_hybrid_comparisons,
)
from app.services.model_evaluation import EvaluationSample


def build_sample(tmp_path: Path) -> EvaluationSample:
    image_path = tmp_path / "sample.jpg"
    image_path.write_bytes(b"image")

    return EvaluationSample(
        sample_id="sample-1",
        scenario="SPARSE",
        image_path=image_path,
        ground_truth_count=3,
        source="TEAM_CONSENTED",
        license="team-consent",
    )


def build_runner(
    *,
    model_name: str,
    predicted_count: int,
) -> ModelRunner:
    return ModelRunner(
        model_name=model_name,
        model_version="v1",
        model_weight_id=f"{model_name.lower()}-v1.pth",
        failure_code=f"{model_name.upper()}_INFERENCE_FAILED",
        analyze=lambda _: AnalysisMetrics(
            person_count=predicted_count,
            visual_congestion_score=float(predicted_count),
            local_peak_congestion_score=float(predicted_count * 2),
            processing_time_ms=10,
        ),
    )


def test_compare_sample_preserves_both_model_results(
    tmp_path: Path,
) -> None:
    record = compare_sample(
        sample=build_sample(tmp_path),
        yolo_runner=build_runner(
            model_name="YOLO",
            predicted_count=3,
        ),
        p2pnet_runner=build_runner(
            model_name="P2PNet",
            predicted_count=8,
        ),
    )

    assert record.selection_status == "UNDECIDED"
    assert record.yolo.analysis_status == "COMPLETED"
    assert record.p2pnet.analysis_status == "COMPLETED"
    assert record.person_count_difference == 5
    assert record.visual_congestion_score_difference == 5.0
    assert record.local_peak_congestion_score_difference == 10.0


def test_compare_sample_preserves_success_when_other_model_fails(
    tmp_path: Path,
) -> None:
    def fail(_: bytes) -> AnalysisMetrics:
        raise RuntimeError("sensitive model detail")

    failed_runner = ModelRunner(
        model_name="P2PNet",
        model_version="v1",
        model_weight_id="selected.pth",
        failure_code="P2PNET_INFERENCE_FAILED",
        analyze=fail,
    )

    record = compare_sample(
        sample=build_sample(tmp_path),
        yolo_runner=build_runner(
            model_name="YOLO",
            predicted_count=3,
        ),
        p2pnet_runner=failed_runner,
    )

    assert record.yolo.analysis_status == "COMPLETED"
    assert record.p2pnet.analysis_status == "FAILED"
    assert record.p2pnet.failure_codes == [
        "P2PNET_INFERENCE_FAILED",
    ]
    assert record.p2pnet.person_count is None
    assert record.person_count_difference is None


def test_summarize_hybrid_comparisons_reports_each_model(
    tmp_path: Path,
) -> None:
    sample = build_sample(tmp_path)
    records = [
        compare_sample(
            sample=sample,
            yolo_runner=build_runner(
                model_name="YOLO",
                predicted_count=3,
            ),
            p2pnet_runner=build_runner(
                model_name="P2PNet",
                predicted_count=8,
            ),
        )
    ]

    summary = summarize_hybrid_comparisons(records)

    assert summary["YOLO:v1"].sample_count == 1
    assert summary["YOLO:v1"].mean_absolute_error == 0.0
    assert summary["P2PNet:v1"].mean_absolute_error == 5.0
    assert summary["P2PNet:v1"].root_mean_squared_error == 5.0
    assert summary["P2PNet:v1"].failed_count == 0


def test_save_report_keeps_selection_undecided(
    tmp_path: Path,
) -> None:
    record = compare_sample(
        sample=build_sample(tmp_path),
        yolo_runner=build_runner(
            model_name="YOLO",
            predicted_count=3,
        ),
        p2pnet_runner=build_runner(
            model_name="P2PNet",
            predicted_count=8,
        ),
    )
    output_path = tmp_path / "report.json"

    save_hybrid_model_comparison_report(
        records=[record],
        output_path=output_path,
    )

    report = json.loads(
        output_path.read_text(encoding="utf-8"),
    )
    assert report["decisionPolicy"] == {
        "selectionStatus": "UNDECIDED",
        "reason": "INSUFFICIENT_SAFY_EVALUATION_DATA",
    }
    assert report["records"][0]["personCountDifference"] == 5
    assert report["records"][0]["yolo"]["modelWeightId"] == ("yolo-v1.pth")


def test_load_hybrid_evaluation_samples_accepts_evaluation_manifest(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "image.jpg"
    image_path.write_bytes(b"image")
    manifest_path = tmp_path / "evaluation.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,ground_truth_count,source,license\n"
            "sample-1,SPARSE,image.jpg,3,TEAM_CONSENTED,team-consent\n"
        ),
        encoding="utf-8",
    )

    samples = load_hybrid_evaluation_samples(manifest_path)

    assert len(samples) == 1
    assert samples[0].ground_truth_count == 3
    assert samples[0].image_path == image_path


def test_load_hybrid_evaluation_samples_accepts_head_point_manifest(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "image.jpg"
    annotation_path = tmp_path / "points.csv"
    image_path.write_bytes(b"image")
    annotation_path.write_text("x,y\n1,1\n", encoding="utf-8")
    manifest_path = tmp_path / "head-points.csv"
    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,image_path,annotation_path,"
            "image_width,image_height,person_count,scenario,license,"
            "consent_confirmed,image_sha256,label_format_version\n"
            "sample-1,TEAM_CONSENTED,group-1,VALIDATION,image.jpg,points.csv,"
            "10,10,1,SPARSE,team-consent,true,hash,head-point-v1\n"
        ),
        encoding="utf-8",
    )

    samples = load_hybrid_evaluation_samples(
        manifest_path,
        source="TEAM_CONSENTED",
        split="VALIDATION",
    )

    assert len(samples) == 1
    assert samples[0].ground_truth_count == 1
    assert samples[0].scenario == "SPARSE"
