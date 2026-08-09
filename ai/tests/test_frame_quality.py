import cv2
import numpy as np

from app.services.frame_quality import (
    BLUR_MEASUREMENT_SHORT_SIDE,
    FRAME_QUALITY_RULE_VERSION,
    inspect_frame_quality,
)


PRODUCTION_FRAME_SIZE = (1280, 720)
LARGER_FRAME_SIZES = [
    (1920, 1080),
    (3840, 2160),
]


def encode_image(image: np.ndarray) -> bytes:
    success, encoded_image = cv2.imencode(".jpg", image)

    assert success
    return encoded_image.tobytes()


def build_textured_frame(width: int, height: int) -> np.ndarray:
    # 합성 체커보드는 확대해도 경계가 1픽셀로 유지되어 해상도 의존성을
    # 재현하지 못한다. 실사 질감에 가까운 표본을 확대해 같은 장면을
    # 더 큰 해상도로 표현한다.
    generator = np.random.default_rng(42)
    texture = generator.normal(128, 28, (720, 1280)).clip(0, 255)
    frame = cv2.cvtColor(
        cv2.GaussianBlur(texture.astype(np.uint8), (3, 3), 0),
        cv2.COLOR_GRAY2BGR,
    )

    if (width, height) == PRODUCTION_FRAME_SIZE:
        return frame

    return cv2.resize(
        frame,
        (width, height),
        interpolation=cv2.INTER_CUBIC,
    )


def measure_native_blur_score(image_bytes: bytes) -> float:
    decoded_image = cv2.imdecode(
        np.frombuffer(image_bytes, dtype=np.uint8),
        cv2.IMREAD_COLOR,
    )
    gray_image = cv2.cvtColor(decoded_image, cv2.COLOR_BGR2GRAY)

    return float(cv2.Laplacian(gray_image, cv2.CV_64F).var())


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
    assert FRAME_QUALITY_RULE_VERSION == "frame-quality-v0.3"


def test_sharp_frame_passes_at_every_resolution() -> None:
    for width, height in [PRODUCTION_FRAME_SIZE, *LARGER_FRAME_SIZES]:
        image = build_textured_frame(width, height)

        result = inspect_frame_quality(encode_image(image))

        assert result.issues == [], (width, height)
        assert result.blur_score is not None
        assert result.blur_score >= 80.0, (width, height)


def test_blurry_frame_is_rejected_at_every_resolution() -> None:
    for width, height in [PRODUCTION_FRAME_SIZE, *LARGER_FRAME_SIZES]:
        image = cv2.GaussianBlur(
            build_textured_frame(width, height),
            (31, 31),
            0,
        )

        result = inspect_frame_quality(encode_image(image))

        assert result.issues == ["TOO_BLURRY"], (width, height)
        assert result.blur_score is not None
        assert result.blur_score < 80.0, (width, height)


def test_production_frame_blur_score_is_measured_without_resizing() -> None:
    width, height = PRODUCTION_FRAME_SIZE

    assert min(width, height) == BLUR_MEASUREMENT_SHORT_SIDE

    image_bytes = encode_image(build_textured_frame(width, height))

    result = inspect_frame_quality(image_bytes)

    assert result.blur_score == round(
        measure_native_blur_score(image_bytes),
        2,
    )


def test_small_frame_blur_score_is_not_upscaled() -> None:
    small_image = cv2.resize(
        build_textured_frame(*PRODUCTION_FRAME_SIZE),
        (480, 270),
        interpolation=cv2.INTER_AREA,
    )
    image_bytes = encode_image(small_image)

    result = inspect_frame_quality(image_bytes)

    assert result.blur_score == round(
        measure_native_blur_score(image_bytes),
        2,
    )
