import cv2
import numpy as np


def _decode_image(
    image_bytes: bytes,
) -> np.ndarray:
    encoded_image = np.frombuffer(
        image_bytes,
        dtype=np.uint8,
    )
    image = cv2.imdecode(
        encoded_image,
        cv2.IMREAD_COLOR,
    )

    if image is None:
        raise ValueError("이미지를 디코딩할 수 없습니다.")

    return image


def _encode_jpeg(
    image: np.ndarray,
) -> bytes:
    encoded, encoded_image = cv2.imencode(
        ".jpg",
        image,
        [
            cv2.IMWRITE_JPEG_QUALITY,
            95,
        ],
    )

    if not encoded:
        raise ValueError("이미지를 인코딩할 수 없습니다.")

    return encoded_image.tobytes()


def create_dark_variant(
    image_bytes: bytes,
    brightness_factor: float = 0.15,
) -> bytes:
    image = _decode_image(image_bytes)

    dark_image = np.clip(
        image.astype(np.float32) * brightness_factor,
        0,
        255,
    ).astype(np.uint8)

    return _encode_jpeg(dark_image)


def create_blurry_variant(
    image_bytes: bytes,
    kernel_size: int = 31,
) -> bytes:
    image = _decode_image(image_bytes)

    if kernel_size < 3 or kernel_size % 2 == 0:
        raise ValueError("블러 커널 크기는 3 이상의 홀수여야 합니다.")

    blurry_image = cv2.GaussianBlur(
        image,
        (
            kernel_size,
            kernel_size,
        ),
        0,
    )

    return _encode_jpeg(blurry_image)
