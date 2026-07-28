import json
from pathlib import Path

from app.services.model_evaluation import (
    EvaluationRecord,
)
from scripts import (
    evaluate_p2pnet_congestion,
)


def test_run_evaluation_saves_p2pnet_report(
    tmp_path: Path,
    monkeypatch,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    output_path = tmp_path / "report.json"
    p2pnet_root = tmp_path / "p2pnet"
    weight_path = p2pnet_root / "weights.pth"

    fake_detector = object()
    records = [
        EvaluationRecord(
            sample_id="local-dense-1",
            scenario="LOCAL_DENSE",
            ground_truth_count=5,
            predicted_count=4,
            visual_congestion_score=1.88,
            local_peak_congestion_score=30.0,
            processing_time_ms=20,
        )
    ]

    monkeypatch.setattr(
        evaluate_p2pnet_congestion,
        "load_p2pnet_detector",
        lambda **kwargs: fake_detector,
    )
    monkeypatch.setattr(
        evaluate_p2pnet_congestion,
        "evaluate_p2pnet_manifest",
        lambda **kwargs: records,
    )

    summary = evaluate_p2pnet_congestion.run_evaluation(
        manifest_path=manifest_path,
        output_path=output_path,
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device="cpu",
        threshold=0.5,
    )

    report = json.loads(
        output_path.read_text(
            encoding="utf-8",
        )
    )

    assert summary.sample_count == 1
    assert summary.mean_absolute_error == 1.0
    assert report["model"] == {
        "name": "P2PNet",
        "version": "SHTechA",
    }
    assert report["records"][0]["visualCongestionScore"] == 1.88
    assert report["records"][0]["localPeakCongestionScore"] == 30.0
