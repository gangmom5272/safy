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


def create_bright_variant(
    image_bytes: bytes,
    whitening_factor: float = 0.9,
) -> bytes:
    image = _decode_image(image_bytes)

    if not 0.0 < whitening_factor <= 1.0:
        raise ValueError("밝기 보정 비율은 0 초과 1 이하여야 합니다.")

    bright_image = (
        image.astype(np.float32) + (255.0 - image.astype(np.float32)) * whitening_factor
    )

    bright_image = np.clip(
        bright_image,
        0,
        255,
    ).astype(np.uint8)

    return _encode_jpeg(bright_image)


def create_low_resolution_variant(
    image_bytes: bytes,
    target_short_side: int = 200,
) -> bytes:
    image = _decode_image(image_bytes)
    height, width = image.shape[:2]
    source_short_side = min(width, height)

    if target_short_side <= 0:
        raise ValueError("목표 짧은 변은 1 이상이어야 합니다.")

    if target_short_side >= source_short_side:
        raise ValueError("목표 짧은 변은 원본보다 작아야 합니다.")

    scale = target_short_side / source_short_side

    resized_image = cv2.resize(
        image,
        (
            round(width * scale),
            round(height * scale),
        ),
        interpolation=cv2.INTER_AREA,
    )

    return _encode_jpeg(resized_image)
