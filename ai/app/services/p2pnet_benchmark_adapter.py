import importlib
import sys
from collections.abc import Callable
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from time import perf_counter
from types import SimpleNamespace

import torch
import torchvision.transforms as transforms
from PIL import Image


CountPredictor = Callable[[bytes], int]


@dataclass(frozen=True)
class P2PNetDetectionResult:
    person_count: int
    confidences: list[float]
    person_centers: list[tuple[float, float]]
    processing_time_ms: int


P2PNetDetector = Callable[
    [bytes],
    P2PNetDetectionResult,
]


def _import_build_model(
    p2pnet_root: Path,
):
    dependency_paths = [
        p2pnet_root,
        p2pnet_root / "vendor",
    ]

    for dependency_path in reversed(
        dependency_paths,
    ):
        path_text = str(
            dependency_path.resolve(),
        )

        if path_text not in sys.path:
            sys.path.insert(
                0,
                path_text,
            )

    models_module = importlib.import_module(
        "models",
    )
    backbone_module = importlib.import_module(
        "models.backbone",
    )

    _disable_external_backbone_preload(
        backbone_module,
    )

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
        backbone_module.models.vgg16_bn,
    )
    backbone_module.models.vgg16 = _force_pretrained_false(
        backbone_module.models.vgg16,
    )


def _build_transform():
    return transforms.Compose(
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


def _load_model(
    *,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
):
    device_object = torch.device(device)
    build_model = _import_build_model(
        p2pnet_root,
    )

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
    model.load_state_dict(
        checkpoint["model"],
    )
    model.eval()

    return (
        model,
        device_object,
        _build_transform(),
    )


def _prepare_image_tensor(
    *,
    image_bytes: bytes,
    transform,
    device_object: torch.device,
) -> tuple[
    torch.Tensor,
    int,
    int,
]:
    with Image.open(
        BytesIO(image_bytes),
    ) as source_image:
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

    resized_image = image.resize(
        (
            resized_width,
            resized_height,
        ),
        Image.Resampling.LANCZOS,
    )

    image_tensor = transform(
        resized_image,
    ).unsqueeze(0)
    image_tensor = image_tensor.to(
        device_object,
    )

    return (
        image_tensor,
        resized_width,
        resized_height,
    )


def load_p2pnet_predictor(
    *,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
    threshold: float = 0.5,
) -> CountPredictor:
    (
        model,
        device_object,
        transform,
    ) = _load_model(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
    )

    def predict(
        image_bytes: bytes,
    ) -> int:
        (
            image_tensor,
            _,
            _,
        ) = _prepare_image_tensor(
            image_bytes=image_bytes,
            transform=transform,
            device_object=device_object,
        )

        with torch.inference_mode():
            outputs = model(image_tensor)

            scores = torch.nn.functional.softmax(
                outputs["pred_logits"],
                dim=-1,
            )[0, :, 1]

            predicted_count = int((scores > threshold).sum().item())

        return predicted_count

    return predict


def load_p2pnet_detector(
    *,
    p2pnet_root: Path,
    weight_path: Path,
    device: str,
    threshold: float = 0.5,
) -> P2PNetDetector:
    (
        model,
        device_object,
        transform,
    ) = _load_model(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
    )

    def detect(
        image_bytes: bytes,
    ) -> P2PNetDetectionResult:
        (
            image_tensor,
            resized_width,
            resized_height,
        ) = _prepare_image_tensor(
            image_bytes=image_bytes,
            transform=transform,
            device_object=device_object,
        )

        started_at = perf_counter()

        with torch.inference_mode():
            outputs = model(image_tensor)

            scores = torch.nn.functional.softmax(
                outputs["pred_logits"],
                dim=-1,
            )[0, :, 1]
            selected_mask = scores > threshold

            selected_scores = scores[selected_mask].detach().cpu().tolist()
            selected_points = (
                outputs["pred_points"][0][selected_mask].detach().cpu().tolist()
            )

        processing_time_ms = round((perf_counter() - started_at) * 1000)

        person_centers = [
            (
                round(
                    min(
                        max(
                            float(point_x) / resized_width,
                            0.0,
                        ),
                        1.0,
                    ),
                    6,
                ),
                round(
                    min(
                        max(
                            float(point_y) / resized_height,
                            0.0,
                        ),
                        1.0,
                    ),
                    6,
                ),
            )
            for point_x, point_y in selected_points
        ]
        confidences = [
            round(
                float(score),
                6,
            )
            for score in selected_scores
        ]

        return P2PNetDetectionResult(
            person_count=len(
                person_centers,
            ),
            confidences=confidences,
            person_centers=person_centers,
            processing_time_ms=(processing_time_ms),
        )

    return detect
