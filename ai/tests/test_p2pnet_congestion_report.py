import json
from pathlib import Path

from app.services.model_evaluation import (
    EvaluationRecord,
    save_evaluation_report,
)


def test_save_report_accepts_p2pnet_model_metadata(
    tmp_path: Path,
) -> None:
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
    output_path = tmp_path / "report.json"

    save_evaluation_report(
        records=records,
        output_path=output_path,
        model_name="P2PNet",
        model_version="SHTechA",
    )

    report = json.loads(
        output_path.read_text(
            encoding="utf-8",
        )
    )

    assert report["model"] == {
        "name": "P2PNet",
        "version": "SHTechA",
    }
    assert report["summary"]["sampleCount"] == 1
    assert report["records"][0]["predictedCount"] == 4
    assert report["records"][0]["visualCongestionScore"] == 1.88
    assert report["records"][0]["localPeakCongestionScore"] == 30.0
