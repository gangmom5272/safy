from io import BytesIO
from pathlib import Path

import pytest
import torch
from PIL import Image

from app.services import (
    p2pnet_benchmark_adapter,
)
from app.services.p2pnet_benchmark_adapter import (
    load_p2pnet_detector,
)


class FakeP2PNet:
    def __init__(self) -> None:
        self.loaded_state = None
        self.device = None
        self.eval_called = False

    def to(self, device):
        self.device = device
        return self

    def load_state_dict(self, state) -> None:
        self.loaded_state = state

    def eval(self):
        self.eval_called = True
        return self

    def __call__(self, samples):
        return {
            "pred_logits": torch.tensor(
                [
                    [
                        [0.0, 2.0],
                        [2.0, 0.0],
                        [0.0, 1.0],
                    ]
                ]
            ),
            "pred_points": torch.tensor(
                [
                    [
                        [25.6, 64.0],
                        [128.0, 128.0],
                        [230.4, 192.0],
                    ]
                ]
            ),
        }


def test_load_p2pnet_detector_returns_normalized_points(
    tmp_path: Path,
    monkeypatch,
) -> None:
    fake_model = FakeP2PNet()

    monkeypatch.setattr(
        p2pnet_benchmark_adapter,
        "_import_build_model",
        lambda root: lambda args: fake_model,
    )
    monkeypatch.setattr(
        p2pnet_benchmark_adapter.torch,
        "load",
        lambda *args, **kwargs: {
            "model": {
                "weight": "value",
            }
        },
    )

    image = Image.new(
        "RGB",
        (
            256,
            256,
        ),
        color="white",
    )
    image_buffer = BytesIO()
    image.save(
        image_buffer,
        format="JPEG",
    )

    detector = load_p2pnet_detector(
        p2pnet_root=tmp_path,
        weight_path=tmp_path / "SHTechA.pth",
        device="cpu",
        threshold=0.5,
    )

    result = detector(
        image_buffer.getvalue(),
    )

    assert result.person_count == 2
    assert result.person_centers == pytest.approx(
        [
            (
                0.1,
                0.25,
            ),
            (
                0.9,
                0.75,
            ),
        ]
    )
    assert result.processing_time_ms >= 0
    assert fake_model.loaded_state == {
        "weight": "value",
    }
    assert fake_model.device == torch.device("cpu")
    assert fake_model.eval_called is True


def test_prepare_image_tensor_applies_exif_orientation() -> None:
    image = Image.new(
        "RGB",
        (
            256,
            128,
        ),
        color="white",
    )

    exif = image.getexif()
    exif[274] = 6

    image_buffer = BytesIO()
    image.save(
        image_buffer,
        format="JPEG",
        exif=exif,
    )

    def fake_transform(
        transformed_image: Image.Image,
    ) -> torch.Tensor:
        return torch.zeros(
            (
                3,
                transformed_image.height,
                transformed_image.width,
            )
        )

    (
        image_tensor,
        resized_width,
        resized_height,
    ) = p2pnet_benchmark_adapter._prepare_image_tensor(
        image_bytes=(image_buffer.getvalue()),
        transform=fake_transform,
        device_object=torch.device("cpu"),
    )

    assert resized_width == 128
    assert resized_height == 256
    assert image_tensor.shape == (
        1,
        3,
        256,
        128,
    )


def test_prepare_image_tensor_limits_large_input_size() -> None:
    image = Image.new(
        "RGB",
        (
            2048,
            1536,
        ),
        color="white",
    )

    image_buffer = BytesIO()
    image.save(
        image_buffer,
        format="JPEG",
    )

    transformed_size: list[tuple[int, int]] = []

    def fake_transform(
        transformed_image: Image.Image,
    ) -> torch.Tensor:
        transformed_size.append(transformed_image.size)

        return torch.zeros(
            (
                3,
                1,
                1,
            )
        )

    (
        _,
        resized_width,
        resized_height,
    ) = p2pnet_benchmark_adapter._prepare_image_tensor(
        image_bytes=(image_buffer.getvalue()),
        transform=fake_transform,
        device_object=torch.device("cpu"),
    )

    assert resized_width == 1536
    assert resized_height == 1152
    assert transformed_size == [
        (
            1536,
            1152,
        )
    ]
