from dataclasses import dataclass

import cv2
import numpy as np


MIN_SHORT_SIDE = 240
MIN_PIXEL_COUNT = 640 * 360
MIN_BRIGHTNESS = 35.0
MAX_BRIGHTNESS = 220.0
MIN_BLUR_SCORE = 80.0


@dataclass(frozen=True)
class FrameQualityResult:
    is_valid: bool
    issues: list[str]
    width: int | None
    height: int | None
    brightness_score: float | None
    blur_score: float | None


def inspect_frame_quality(image_bytes: bytes) -> FrameQualityResult:
    encoded_image = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(encoded_image, cv2.IMREAD_COLOR)

    if image is None:
        return FrameQualityResult(
            is_valid=False,
            issues=["DECODE_FAILED"],
            width=None,
            height=None,
            brightness_score=None,
            blur_score=None,
        )

    height, width = image.shape[:2]
    gray_image = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    brightness_score = float(gray_image.mean())
    blur_score = float(cv2.Laplacian(gray_image, cv2.CV_64F).var())

    issues: list[str] = []

    if min(width, height) < MIN_SHORT_SIDE or width * height < MIN_PIXEL_COUNT:
        issues.append("LOW_RESOLUTION")

    if brightness_score < MIN_BRIGHTNESS:
        issues.append("TOO_DARK")
    elif brightness_score > MAX_BRIGHTNESS:
        issues.append("TOO_BRIGHT")

    if blur_score < MIN_BLUR_SCORE:
        issues.append("TOO_BLURRY")

    return FrameQualityResult(
        is_valid=not issues,
        issues=issues,
        width=width,
        height=height,
        brightness_score=round(brightness_score, 2),
        blur_score=round(blur_score, 2),
    )