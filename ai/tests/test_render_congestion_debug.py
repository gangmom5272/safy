from pathlib import Path

import cv2
import numpy as np

from app.services.person_detector import PersonDetectionResult
from scripts import render_congestion_debug


def test_render_file_saves_debug_image(
    monkeypatch,
    tmp_path: Path,
) -> None:
    image = np.full((80, 80, 3), 255, dtype=np.uint8)
    encoded_success, encoded_image = cv2.imencode(
        ".png",
        image,
    )

    assert encoded_success is True

    input_path = tmp_path / "input.png"
    output_path = tmp_path / "debug.png"
    input_path.write_bytes(encoded_image.tobytes())

    fake_detection = PersonDetectionResult(
        person_count=1,
        confidences=[0.9],
        processing_time_ms=10,
        person_centers=[(0.3, 0.3)],
        person_boxes=[(0.2, 0.15, 0.4, 0.55)],
    )

    monkeypatch.setattr(
        render_congestion_debug,
        "detect_people",
        lambda image_bytes: fake_detection,
    )

    result = render_congestion_debug.render_file(
        input_path=input_path,
        output_path=output_path,
    )

    assert output_path.exists()
    assert output_path.read_bytes().startswith(b"\x89PNG")
    assert result.person_count == 1
    assert result.visual_congestion_score == 0.47
    assert result.local_peak_congestion_score == 7.5
    assert result.processing_time_ms == 10