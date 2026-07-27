import cv2
import numpy as np

from app.services.frame_quality import (
    FRAME_QUALITY_RULE_VERSION,
    inspect_frame_quality,
)


def encode_image(image: np.ndarray) -> bytes:
    success, encoded_image = cv2.imencode(".jpg", image)

    assert success
    return encoded_image.tobytes()


def test_invalid_bytes_return_decode_failed() -> None:
    result = inspect_frame_quality(b"not-an-image")

    assert result.is_valid is False
    assert result.issues == ["DECODE_FAILED"]
    assert result.width is None
    assert result.height is None
    assert result.brightness_score is None
    assert result.blur_score is None


def test_low_resolution_frame_is_invalid() -> None:
    image = np.full((120, 160, 3), 128, dtype=np.uint8)

    result = inspect_frame_quality(encode_image(image))

    assert result.is_valid is False
    assert "LOW_RESOLUTION" in result.issues
    assert result.width == 160
    assert result.height == 120


def test_dark_frame_is_invalid() -> None:
    image = np.full((720, 1280, 3), 10, dtype=np.uint8)

    result = inspect_frame_quality(encode_image(image))

    assert result.is_valid is False
    assert "TOO_DARK" in result.issues
    assert result.brightness_score is not None
    assert result.brightness_score < 35.0


def test_bright_frame_is_invalid() -> None:
    image = np.full((720, 1280, 3), 245, dtype=np.uint8)

    result = inspect_frame_quality(encode_image(image))

    assert result.is_valid is False
    assert "TOO_BRIGHT" in result.issues
    assert result.brightness_score is not None
    assert result.brightness_score > 220.0


def test_blurry_frame_is_invalid() -> None:
    image = np.full((720, 1280, 3), 128, dtype=np.uint8)

    result = inspect_frame_quality(encode_image(image))

    assert result.is_valid is False
    assert result.issues == ["TOO_BLURRY"]
    assert result.blur_score is not None
    assert result.blur_score < 80.0


def test_valid_frame_passes_quality_check() -> None:
    rows, columns = np.indices((720, 1280))
    checkerboard = ((rows // 32 + columns // 32) % 2 * 255).astype(np.uint8)
    image = cv2.cvtColor(checkerboard, cv2.COLOR_GRAY2BGR)

    result = inspect_frame_quality(encode_image(image))

    assert result.is_valid is True
    assert result.issues == []
    assert result.width == 1280
    assert result.height == 720
    assert result.brightness_score is not None
    assert 35.0 <= result.brightness_score <= 220.0
    assert result.blur_score is not None
    assert result.blur_score >= 80.0


def test_320_by_240_frame_passes_resolution_check() -> None:
    rows, columns = np.indices((240, 320))
    checkerboard = ((rows // 16 + columns // 16) % 2 * 255).astype(np.uint8)
    image = cv2.cvtColor(
        checkerboard,
        cv2.COLOR_GRAY2BGR,
    )

    result = inspect_frame_quality(encode_image(image))

    assert "LOW_RESOLUTION" not in result.issues
    assert result.width == 320
    assert result.height == 240


def test_frame_quality_rule_version_matches_calibrated_rule() -> None:
    assert FRAME_QUALITY_RULE_VERSION == "frame-quality-v0.2"
