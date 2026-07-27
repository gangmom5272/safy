import json
from pathlib import Path

import cv2
import numpy as np

from scripts.evaluate_frame_quality import (
    run_evaluation,
)


def test_run_evaluation_writes_report(
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

    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        ("sample_id,scenario,image_path,expected_issues\nnormal,NORMAL,normal.jpg,\n"),
        encoding="utf-8",
    )

    output_path = tmp_path / "report.json"

    records = run_evaluation(
        manifest_path=manifest_path,
        output_path=output_path,
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert len(records) == 1
    assert records[0].matches_expectation is True
    assert report["summary"] == {
        "sampleCount": 1,
        "matchedCount": 1,
        "mismatchedCount": 0,
    }
