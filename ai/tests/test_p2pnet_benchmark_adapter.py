from io import BytesIO
from pathlib import Path

import torch
from PIL import Image

from app.services import (
    p2pnet_benchmark_adapter,
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
            )
        }


def test_load_p2pnet_predictor_returns_count(
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
        lambda *args, **kwargs: {"model": {"weight": "value"}},
    )

    image = Image.new(
        "RGB",
        (256, 256),
        color="white",
    )
    image_buffer = BytesIO()
    image.save(image_buffer, format="JPEG")

    predictor = p2pnet_benchmark_adapter.load_p2pnet_predictor(
        p2pnet_root=tmp_path,
        weight_path=(tmp_path / "SHTechA.pth"),
        device="cpu",
        threshold=0.5,
    )

    predicted_count = predictor(image_buffer.getvalue())

    assert predicted_count == 2
    assert fake_model.loaded_state == {"weight": "value"}
    assert fake_model.device == torch.device("cpu")
    assert fake_model.eval_called is True


def test_disable_external_backbone_preload() -> None:
    calls: list[tuple[str, bool]] = []

    class FakeModels:
        @staticmethod
        def vgg16_bn(
            *,
            pretrained: bool,
        ):
            calls.append(("vgg16_bn", pretrained))
            return "vgg16_bn"

        @staticmethod
        def vgg16(
            *,
            pretrained: bool,
        ):
            calls.append(("vgg16", pretrained))
            return "vgg16"

    fake_backbone_module = type(
        "FakeBackboneModule",
        (),
        {"models": FakeModels()},
    )()

    p2pnet_benchmark_adapter._disable_external_backbone_preload(fake_backbone_module)

    fake_backbone_module.models.vgg16_bn(pretrained=True)
    fake_backbone_module.models.vgg16(pretrained=True)

    assert calls == [
        ("vgg16_bn", False),
        ("vgg16", False),
    ]
