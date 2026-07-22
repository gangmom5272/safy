from types import SimpleNamespace

import cv2
import numpy as np
import torch

from app.services import person_detector


class FakeModel:
    def predict(self, **kwargs):
        boxes = SimpleNamespace(
            conf=torch.tensor([0.9, 0.8]),
            xywhn=torch.tensor(
                [
                    [0.25, 0.50, 0.10, 0.20],
                    [0.75, 0.50, 0.10, 0.20],
                ]
            ),
            xyxyn=torch.tensor(
                [
                    [0.20, 0.40, 0.30, 0.60],
                    [0.70, 0.40, 0.80, 0.60],
                ]
            ),
        )
        return [SimpleNamespace(boxes=boxes)]


def encode_test_image() -> bytes:
    image = np.full((720, 1280, 3), 128, dtype=np.uint8)
    success, encoded_image = cv2.imencode(".jpg", image)

    assert success
    return encoded_image.tobytes()


def test_detect_people_returns_normalized_geometry(
    monkeypatch,
) -> None:
    monkeypatch.setattr(
        person_detector,
        "get_model",
        lambda: FakeModel(),
    )
    monkeypatch.setattr(
        person_detector.torch.cuda,
        "is_available",
        lambda: False,
    )

    result = person_detector.detect_people(encode_test_image())

    assert result.person_count == 2
    assert result.person_centers == [
        (0.25, 0.5),
        (0.75, 0.5),
    ]
    assert result.person_boxes == [
        (0.2, 0.4, 0.3, 0.6),
        (0.7, 0.4, 0.8, 0.6),
    ]