import json
from pathlib import Path

import cv2
import numpy as np
import pytest

from app.services.frame_quality_evaluation import (
    FrameQualityEvaluationRecord,
    FrameQualityEvaluationSample,
    evaluate_frame_quality_sample,
    load_frame_quality_manifest,
    save_frame_quality_report,
)


def test_load_frame_quality_manifest(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "expected_issues\n"
            "normal,NORMAL,images/normal.jpg,\n"
            "night,NIGHT,images/night.jpg,"
            "TOO_DARK|TOO_BLURRY\n"
        ),
        encoding="utf-8",
    )

    samples = load_frame_quality_manifest(manifest_path)

    assert len(samples) == 2

    assert samples[0].sample_id == "normal"
    assert samples[0].scenario == "NORMAL"
    assert samples[0].image_path == (tmp_path / "images" / "normal.jpg")
    assert samples[0].expected_issues == []

    assert samples[1].sample_id == "night"
    assert samples[1].scenario == "NIGHT"
    assert samples[1].expected_issues == [
        "TOO_DARK",
        "TOO_BLURRY",
    ]


def test_evaluate_frame_quality_sample_records_match(
    tmp_path: Path,
) -> None:
    rows, columns = np.indices((360, 640))
    checkerboard = ((rows // 16 + columns // 16) % 2 * 255).astype(np.uint8)
    image = cv2.cvtColor(
        checkerboard,
        cv2.COLOR_GRAY2BGR,
    )

    encoded, encoded_image = cv2.imencode(
        ".jpg",
        image,
    )

    assert encoded is True

    image_path = tmp_path / "normal.jpg"
    image_path.write_bytes(encoded_image.tobytes())

    sample = FrameQualityEvaluationSample(
        sample_id="normal",
        scenario="NORMAL",
        image_path=image_path,
        expected_issues=[],
    )

    record = evaluate_frame_quality_sample(sample)

    assert record.sample_id == "normal"
    assert record.scenario == "NORMAL"
    assert record.expected_valid is True
    assert record.actual_valid is True
    assert record.expected_issues == []
    assert record.actual_issues == []
    assert record.matches_expectation is True
    assert record.width == 640
    assert record.height == 360
    assert record.brightness_score is not None
    assert record.blur_score is not None


def test_save_frame_quality_report_writes_summary(
    tmp_path: Path,
) -> None:
    records = [
        FrameQualityEvaluationRecord(
            sample_id="normal",
            scenario="NORMAL",
            expected_valid=True,
            actual_valid=True,
            expected_issues=[],
            actual_issues=[],
            matches_expectation=True,
            width=640,
            height=360,
            brightness_score=120.0,
            blur_score=500.0,
        ),
        FrameQualityEvaluationRecord(
            sample_id="night",
            scenario="NIGHT",
            expected_valid=False,
            actual_valid=True,
            expected_issues=["TOO_DARK"],
            actual_issues=[],
            matches_expectation=False,
            width=640,
            height=360,
            brightness_score=40.0,
            blur_score=100.0,
        ),
    ]
    output_path = tmp_path / "report.json"

    save_frame_quality_report(
        records,
        output_path,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert report["summary"] == {
        "sampleCount": 2,
        "matchedCount": 1,
        "mismatchedCount": 1,
    }
    assert report["records"][0]["sampleId"] == "normal"
    assert report["records"][1]["matchesExpectation"] is False
    assert report["records"][1]["brightnessScore"] == 40.0


def test_load_frame_quality_manifest_rejects_duplicate_ids(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "expected_issues\n"
            "same,NORMAL,images/normal.jpg,\n"
            "same,NIGHT,images/night.jpg,"
            "TOO_DARK\n"
        ),
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="sample_id는 중복될 수 없습니다",
    ):
        load_frame_quality_manifest(manifest_path)
