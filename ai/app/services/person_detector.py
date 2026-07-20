from dataclasses import dataclass
from time import perf_counter

import cv2
import numpy as np
import torch

from app.services.model import get_model


@dataclass(frozen=True)
class PersonDetectionResult:
    person_count: int
    confidences: list[float]
    processing_time_ms: int


def detect_people(image_bytes: bytes) -> PersonDetectionResult:
    encoded_image = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(encoded_image, cv2.IMREAD_COLOR)

    if image is None:
        raise ValueError("이미지를 디코딩할 수 없습니다.")

    device = 0 if torch.cuda.is_available() else "cpu"
    started_at = perf_counter()

    result = get_model().predict(
        source=image,
        classes=[0],
        device=device,
        verbose=False,
    )[0]

    processing_time_ms = round((perf_counter() - started_at) * 1000)
    confidences = (
        result.boxes.conf.detach().cpu().tolist()
        if result.boxes is not None
        else []
    )

    return PersonDetectionResult(
        person_count=len(confidences),
        confidences=confidences,
        processing_time_ms=processing_time_ms,
    )