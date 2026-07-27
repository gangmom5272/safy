from collections.abc import Callable

from app.services.model import get_model
from app.services.person_detector import (
    detect_people,
)

CountPredictor = Callable[[bytes], int]


def load_yolo_predictor() -> CountPredictor:
    get_model()

    def predict(image_bytes: bytes) -> int:
        detection_result = detect_people(image_bytes)
        return detection_result.person_count

    return predict
