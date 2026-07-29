from pathlib import Path

from app.services.hybrid_model_comparison import (
    AnalysisMetrics,
    ModelRunner,
)
from scripts import evaluate_hybrid_models


def test_run_evaluation_saves_unselected_comparison_report(
    tmp_path: Path,
    monkeypatch,
) -> None:
    image_path = tmp_path / "image.jpg"
    image_path.write_bytes(b"image")
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,ground_truth_count,source,license\n"
            "sample-1,SPARSE,image.jpg,2,TEAM_CONSENTED,team-consent\n"
        ),
        encoding="utf-8",
    )

    def fake_runner(model_name: str, count: int) -> ModelRunner:
        return ModelRunner(
            model_name=model_name,
            model_version="v1",
            model_weight_id="weight.pth",
            failure_code=f"{model_name.upper()}_INFERENCE_FAILED",
            analyze=lambda _: AnalysisMetrics(
                person_count=count,
                visual_congestion_score=1.0,
                local_peak_congestion_score=2.0,
                processing_time_ms=10,
            ),
        )

    monkeypatch.setattr(
        evaluate_hybrid_models,
        "build_yolo_runner",
        lambda: fake_runner("YOLO", 2),
    )
    monkeypatch.setattr(
        evaluate_hybrid_models,
        "build_p2pnet_runner",
        lambda **_: fake_runner("P2PNet", 3),
    )
    output_path = tmp_path / "report.json"

    records = evaluate_hybrid_models.run_evaluation(
        manifest_path=manifest_path,
        p2pnet_root=tmp_path / "p2pnet",
        p2pnet_weight_path=tmp_path / "selected.pth",
        output_path=output_path,
        device="cpu",
        threshold=0.5,
    )

    assert len(records) == 1
    assert records[0].selection_status == "UNDECIDED"
    assert output_path.exists()
