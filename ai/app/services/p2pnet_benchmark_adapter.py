import importlib
import sys
from collections.abc import Callable
from io import BytesIO
from pathlib import Path
from types import SimpleNamespace

import torch
import torchvision.transforms as transforms
from PIL import Image

CountPredictor = Callable[[bytes], int]


def _import_build_model(
    p2pnet_root: Path,
):
    dependency_paths = [
        p2pnet_root,
        p2pnet_root / "vendor",
    ]

    for dependency_path in reversed(dependency_paths):
        path_text = str(dependency_path.resolve())

        if path_text not in sys.path:
            sys.path.insert(0, path_text)

    models_module = importlib.import_module("models")
    backbone_module = importlib.import_module("models.backbone")

    _disable_external_backbone_preload(backbone_module)

    return models_module.build_model


def _force_pretrained_false(
    model_factory,
):
    def wrapped_factory(
        *args,
        **kwargs,
    ):
        kwargs["pretrained"] = False

        return model_factory(
            *args,
            **kwargs,
        )

    return wrapped_factory


def _disable_external_backbone_preload(
    backbone_module,
) -> None:
    backbone_module.models.vgg16_bn = _force_pretrained_false(
        backbone_module.models.vgg16_bn
    )
    backbone_module.models.vgg16 = _force_pretrained_false(backbone_module.models.vgg16)


def load_p2pnet_predictor(
    *,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
    threshold: float = 0.5,
) -> CountPredictor:
    device_object = torch.device(device)
    build_model = _import_build_model(p2pnet_root)

    model_arguments = SimpleNamespace(
        backbone="vgg16_bn",
        row=2,
        line=2,
    )

    model = build_model(model_arguments)
    model.to(device_object)

    checkpoint = torch.load(
        weight_path,
        map_location="cpu",
        weights_only=False,
    )
    model.load_state_dict(checkpoint["model"])
    model.eval()

    transform = transforms.Compose(
        [
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[
                    0.485,
                    0.456,
                    0.406,
                ],
                std=[
                    0.229,
                    0.224,
                    0.225,
                ],
            ),
        ]
    )

    def predict(image_bytes: bytes) -> int:
        with Image.open(BytesIO(image_bytes)) as source_image:
            image = source_image.convert("RGB")

        width, height = image.size
        resized_width = max(
            128,
            width // 128 * 128,
        )
        resized_height = max(
            128,
            height // 128 * 128,
        )

        image = image.resize(
            (
                resized_width,
                resized_height,
            ),
            Image.Resampling.LANCZOS,
        )

        image_tensor = transform(image).unsqueeze(0)
        image_tensor = image_tensor.to(device_object)

        with torch.inference_mode():
            outputs = model(image_tensor)

            scores = torch.nn.functional.softmax(
                outputs["pred_logits"],
                dim=-1,
            )[0, :, 1]

            predicted_count = int((scores > threshold).sum().item())

        return predicted_count

    return predict
