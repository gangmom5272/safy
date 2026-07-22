from dataclasses import dataclass, field
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
    person_centers: list[tuple[float, float]] = field(
        default_factory=list,
    )


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

    if result.boxes is None:
        confidences = []
        person_centers = []
    else:
        confidences = result.boxes.conf.detach().cpu().tolist()
        normalized_centers = (
            result.boxes.xywhn[:, :2]
            .detach()
            .cpu()
            .tolist()
        )
        person_centers = [
            (float(center_x), float(center_y))
            for center_x, center_y in normalized_centers
        ]

    return PersonDetectionResult(
        person_count=len(confidences),
        confidences=confidences,
        processing_time_ms=processing_time_ms,
        person_centers=person_centers,
    )