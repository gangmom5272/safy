from dataclasses import dataclass

import cv2
import numpy as np


FRAME_QUALITY_RULE_VERSION = "frame-quality-v0.3"
MIN_SHORT_SIDE = 240
MIN_PIXEL_COUNT = 320 * 240
MIN_BRIGHTNESS = 35.0
MAX_BRIGHTNESS = 220.0
MIN_BLUR_SCORE = 80.0
BLUR_MEASUREMENT_SHORT_SIDE = 720


@dataclass(frozen=True)
class FrameQualityResult:
    is_valid: bool
    issues: list[str]
    width: int | None
    height: int | None
    brightness_score: float | None
    blur_score: float | None


def _measure_blur_score(gray_image: np.ndarray) -> float:
    # Laplacian 분산은 인접 1픽셀 간 차이를 재므로 해상도가 커지면 같은 경계가
    # 여러 픽셀에 퍼져 점수가 낮아진다. 실제 선명함과 무관하게 판정이 뒤집히지
    # 않도록 측정 전에만 짧은 변을 고정 크기로 축소한다. 확대는 하지 않는다.
    height, width = gray_image.shape[:2]
    short_side = min(width, height)

    if short_side > BLUR_MEASUREMENT_SHORT_SIDE:
        scale = BLUR_MEASUREMENT_SHORT_SIDE / short_side
        gray_image = cv2.resize(
            gray_image,
            (
                max(1, round(width * scale)),
                max(1, round(height * scale)),
            ),
            interpolation=cv2.INTER_AREA,
        )

    return float(cv2.Laplacian(gray_image, cv2.CV_64F).var())


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
    blur_score = _measure_blur_score(gray_image)

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
