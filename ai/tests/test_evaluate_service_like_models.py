import csv
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from app.services.head_point_dataset import HeadPointSample
from app.services.p2pnet_finetune_split import (
    save_p2pnet_finetune_manifest,
)
from scripts.evaluate_service_like_models import (
    parse_thresholds,
    run_evaluation,
)


@dataclass(frozen=True)
class _DetectionResult:
    person_count: int
    confidences: list[float]
    person_centers: list[tuple[float, float]]
    processing_time_ms: int


def _detector(image_bytes: bytes) -> _DetectionResult:
    return _DetectionResult(
        person_count=1,
        confidences=[0.9],
        person_centers=[(0.5, 0.5)],
        processing_time_ms=1,
    )


def _write_manifest(tmp_path: Path) -> Path:
    image_path = tmp_path / "sample.jpg"
    annotation_path = tmp_path / "sample.points.csv"
    Image.new("RGB", (320, 240), color=(100, 120, 130)).save(image_path)
    annotation_path.write_text("x,y\n160,120\n", encoding="utf-8")
    sample = HeadPointSample(
        sample_id="sample-1",
        source="JHU-CROWD++ v2.0",
        source_group="sample-1",
        split="TEST",
        image_path=image_path,
        annotation_path=annotation_path,
        image_width=320,
        image_height=240,
        person_count=1,
        scenario="SPARSE",
        license="jhu-crowd-noncommercial",
        consent_confirmed=False,
        image_sha256=hashlib.sha256(image_path.read_bytes()).hexdigest(),
        label_format_version="head-point-v1",
    )
    manifest_path = tmp_path / "manifest.csv"
    save_p2pnet_finetune_manifest(
        samples=[sample],
        output_path=manifest_path,
    )
    return manifest_path


def test_parse_thresholds_sorts_and_deduplicates() -> None:
    assert parse_thresholds("0.7,0.5,0.5,0.6") == [0.5, 0.6, 0.7]


def test_run_evaluation_saves_report_and_visualization(
    tmp_path: Path,
) -> None:
    manifest_path = _write_manifest(tmp_path)
    review_path = tmp_path / "reviews.csv"
    with review_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(
            [
                "sample_id",
                "spatial_pattern",
                "review_status",
                "review_notes",
            ]
        )
        writer.writerow(["sample-1", "WIDE_DISTRIBUTED", "APPROVED", "공원형"])
    output_path = tmp_path / "report.json"
    visualization_directory = tmp_path / "visualizations"

    evaluation = run_evaluation(
        manifest_path=manifest_path,
        spatial_review_path=review_path,
        output_path=output_path,
        visualization_directory=visualization_directory,
        thresholds=[0.5, 0.6],
        yolo_detect=_detector,
        p2pnet_detect=_detector,
    )

    assert evaluation.summary.sample_count == 1
    assert output_path.is_file()
    assert (visualization_directory / "sample-1.comparison.png").is_file()
    payload = json.loads(output_path.read_text(encoding="utf-8"))
    assert payload["evaluationMetadata"]["operationalRuleChanged"] is False
