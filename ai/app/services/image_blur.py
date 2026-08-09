from dataclasses import dataclass
from time import perf_counter

import cv2
import numpy as np


IMAGE_BLUR_RULE_VERSION = "full-image-blur-v0.1"
PIXELATION_SCALE = 0.10
JPEG_QUALITY = 85


class ImageBlurError(ValueError):
    def __init__(
        self,
        code: str,
    ) -> None:
        super().__init__(code)
        self.code = code


@dataclass(frozen=True)
class ImageBlurResult:
    image_bytes: bytes
    width: int
    height: int
    processing_time_ms: int


def blur_full_image(
    image_bytes: bytes,
) -> ImageBlurResult:
    started_at = perf_counter()

    if not image_bytes:
        raise ImageBlurError(
            "IMAGE_DECODE_FAILED",
        )

    try:
        encoded_image = np.frombuffer(
            image_bytes,
            dtype=np.uint8,
        )
        image = cv2.imdecode(
            encoded_image,
            cv2.IMREAD_COLOR,
        )
    except Exception as error:
        raise ImageBlurError(
            "IMAGE_DECODE_FAILED",
        ) from error

    if image is None:
        raise ImageBlurError(
            "IMAGE_DECODE_FAILED",
        )

    height, width = image.shape[:2]

    try:
        reduced_width = max(
            1,
            round(width * PIXELATION_SCALE),
        )
        reduced_height = max(
            1,
            round(height * PIXELATION_SCALE),
        )

        reduced_image = cv2.resize(
            image,
            (
                reduced_width,
                reduced_height,
            ),
            interpolation=cv2.INTER_AREA,
        )
        pixelated_image = cv2.resize(
            reduced_image,
            (
                width,
                height,
            ),
            interpolation=cv2.INTER_LINEAR,
        )

        blur_sigma = max(
            3.0,
            min(width, height) * 0.02,
        )
        blurred_image = cv2.GaussianBlur(
            pixelated_image,
            (
                0,
                0,
            ),
            sigmaX=blur_sigma,
            sigmaY=blur_sigma,
        )
    except Exception as error:
        raise ImageBlurError(
            "IMAGE_BLUR_FAILED",
        ) from error

    try:
        encoded, output_image = cv2.imencode(
            ".jpg",
            blurred_image,
            [
                cv2.IMWRITE_JPEG_QUALITY,
                JPEG_QUALITY,
            ],
        )
    except Exception as error:
        raise ImageBlurError(
            "IMAGE_ENCODING_FAILED",
        ) from error

    if not encoded:
        raise ImageBlurError(
            "IMAGE_ENCODING_FAILED",
        )

    processing_time_ms = round(
        (perf_counter() - started_at) * 1000,
    )

    return ImageBlurResult(
        image_bytes=output_image.tobytes(),
        width=width,
        height=height,
        processing_time_ms=processing_time_ms,
    )
