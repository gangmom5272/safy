import cv2
import numpy as np

from app.services import image_blur
from app.services.image_blur import (
    ImageBlurError,
    blur_full_image,
)


def encode_image(image: np.ndarray) -> bytes:
    success, encoded_image = cv2.imencode(
        ".jpg",
        image,
    )

    assert success is True
    return encoded_image.tobytes()


def decode_image(image_bytes: bytes) -> np.ndarray:
    encoded_image = np.frombuffer(
        image_bytes,
        dtype=np.uint8,
    )
    image = cv2.imdecode(
        encoded_image,
        cv2.IMREAD_COLOR,
    )

    assert image is not None
    return image


def test_blur_full_image_preserves_size_and_reduces_detail() -> None:
    rows, columns = np.indices((240, 320))
    checkerboard = ((rows // 4 + columns // 4) % 2 * 255).astype(np.uint8)
    image = cv2.cvtColor(
        checkerboard,
        cv2.COLOR_GRAY2BGR,
    )
    image_bytes = encode_image(image)

    result = blur_full_image(image_bytes)
    blurred_image = decode_image(result.image_bytes)

    original_detail = cv2.Laplacian(
        image,
        cv2.CV_64F,
    ).var()
    blurred_detail = cv2.Laplacian(
        blurred_image,
        cv2.CV_64F,
    ).var()

    assert result.width == 320
    assert result.height == 240
    assert blurred_image.shape == image.shape
    assert result.image_bytes != image_bytes
    assert blurred_detail < original_detail * 0.05


def test_blur_full_image_rejects_invalid_image() -> None:
    try:
        blur_full_image(b"not-an-image")
    except ImageBlurError as error:
        assert error.code == "IMAGE_DECODE_FAILED"
    else:
        raise AssertionError(
            "잘못된 이미지는 ImageBlurError를 발생시켜야 합니다.",
        )


def test_blur_full_image_rejects_empty_bytes() -> None:
    try:
        blur_full_image(b"")
    except ImageBlurError as error:
        assert error.code == "IMAGE_DECODE_FAILED"
    else:
        raise AssertionError(
            "빈 입력은 ImageBlurError를 발생시켜야 합니다.",
        )


def test_blur_full_image_wraps_processing_failure(
    monkeypatch,
) -> None:
    image = np.full(
        (
            240,
            320,
            3,
        ),
        128,
        dtype=np.uint8,
    )

    def fail_gaussian_blur(
        *_,
        **__,
    ) -> None:
        raise RuntimeError(
            "Sensitive OpenCV processing error",
        )

    monkeypatch.setattr(
        image_blur.cv2,
        "GaussianBlur",
        fail_gaussian_blur,
    )

    try:
        blur_full_image(
            encode_image(image),
        )
    except ImageBlurError as error:
        assert error.code == "IMAGE_BLUR_FAILED"
    else:
        raise AssertionError(
            "블러 처리 실패는 ImageBlurError로 변환되어야 합니다.",
        )
