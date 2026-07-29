import random
from pathlib import Path

import torch
import torchvision.transforms as transforms
from PIL import Image, ImageOps
from torch.utils.data import Dataset

from app.services.head_point_dataset import (
    HeadPointSample,
    load_head_point_annotation,
    load_head_point_manifest,
    validate_head_point_sample_files,
)


DEFAULT_PATCH_SIZE = 128
DEFAULT_PATCH_COUNT = 4
DEFAULT_MAX_LONG_SIDE = 1536
IMAGE_SIZE_ALIGNMENT = 128


class P2PNetManifestDataset(Dataset):
    def __init__(
        self,
        *,
        manifest_path: Path,
        split: str,
        training: bool,
        patch_size: int = DEFAULT_PATCH_SIZE,
        patch_count: int = DEFAULT_PATCH_COUNT,
        random_seed: int = 42,
        max_long_side: int = DEFAULT_MAX_LONG_SIDE,
    ) -> None:
        samples = load_head_point_manifest(manifest_path)

        self.samples = [sample for sample in samples if sample.split == split]

        if not self.samples:
            raise ValueError(f"{split} 분할의 머리 좌표 표본이 필요합니다.")

        if patch_size <= 0:
            raise ValueError("patch_size는 양수여야 합니다.")

        if patch_count <= 0:
            raise ValueError(
                "patch_count는 양수여야 합니다.",
            )

        if max_long_side < IMAGE_SIZE_ALIGNMENT:
            raise ValueError(
                "max_long_side는 128 이상이어야 합니다.",
            )

        for sample in self.samples:
            validate_head_point_sample_files(sample)

        self.training = training
        self.patch_size = patch_size
        self.patch_count = patch_count
        self.max_long_side = max_long_side
        self.random_generator = random.Random(random_seed)
        self.transform = transforms.Compose(
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

    def __len__(self) -> int:
        return len(self.samples)

    def _load_image(
        self,
        sample: HeadPointSample,
    ) -> Image.Image:
        with Image.open(sample.image_path) as source_image:
            image = ImageOps.exif_transpose(source_image).convert("RGB")

        return image

    def _load_points(
        self,
        sample: HeadPointSample,
    ) -> torch.Tensor:
        annotation = load_head_point_annotation(
            annotation_path=(sample.annotation_path),
            image_width=(sample.image_width),
            image_height=(sample.image_height),
        )

        return torch.tensor(
            annotation.points,
            dtype=torch.float32,
        ).reshape(
            -1,
            2,
        )

    def _build_target(
        self,
        *,
        points: torch.Tensor,
        image_id: int,
    ) -> dict[str, torch.Tensor]:
        return {
            "point": points,
            "labels": torch.ones(
                points.shape[0],
                dtype=torch.long,
            ),
            "image_id": torch.tensor(
                [image_id],
                dtype=torch.long,
            ),
        }

    def _ensure_minimum_patch_size(
        self,
        *,
        image: Image.Image,
        points: torch.Tensor,
    ) -> tuple[
        Image.Image,
        torch.Tensor,
    ]:
        width, height = image.size

        scale = max(
            1.0,
            self.patch_size / width,
            self.patch_size / height,
        )

        if scale == 1.0:
            return image, points

        resized_width = max(
            self.patch_size,
            round(width * scale),
        )
        resized_height = max(
            self.patch_size,
            round(height * scale),
        )

        resized_image = image.resize(
            (
                resized_width,
                resized_height,
            ),
            Image.Resampling.BILINEAR,
        )
        resized_points = points * scale

        return (
            resized_image,
            resized_points,
        )

    def _crop_training_patch(
        self,
        *,
        image: Image.Image,
        points: torch.Tensor,
        start_x: int,
        start_y: int,
        image_id: int,
    ) -> tuple[
        torch.Tensor,
        dict[str, torch.Tensor],
    ]:
        end_x = start_x + self.patch_size
        end_y = start_y + self.patch_size

        patch = image.crop(
            (
                start_x,
                start_y,
                end_x,
                end_y,
            )
        )

        if points.shape[0] == 0:
            patch_points = points.clone()
        else:
            selected = (
                (points[:, 0] >= start_x)
                & (points[:, 0] < end_x)
                & (points[:, 1] >= start_y)
                & (points[:, 1] < end_y)
            )
            patch_points = points[selected].clone()

            if patch_points.shape[0] > 0:
                patch_points[:, 0] -= start_x
                patch_points[:, 1] -= start_y

        patch_tensor = self.transform(patch)
        target = self._build_target(
            points=patch_points,
            image_id=image_id,
        )

        return (
            patch_tensor,
            target,
        )

    def _build_training_patches(
        self,
        *,
        image: Image.Image,
        points: torch.Tensor,
        index: int,
    ) -> tuple[
        torch.Tensor,
        list[dict[str, torch.Tensor]],
    ]:
        (
            image,
            points,
        ) = self._ensure_minimum_patch_size(
            image=image,
            points=points,
        )

        width, height = image.size
        patch_tensors: list[torch.Tensor] = []
        targets: list[dict[str, torch.Tensor]] = []

        crop_origins: list[tuple[int, int]] = []

        if points.shape[0] > 0:
            selected_index = self.random_generator.randrange(points.shape[0])
            center_x = float(points[selected_index, 0])
            center_y = float(points[selected_index, 1])

            start_x = min(
                max(
                    round(center_x - self.patch_size / 2),
                    0,
                ),
                width - self.patch_size,
            )
            start_y = min(
                max(
                    round(center_y - self.patch_size / 2),
                    0,
                ),
                height - self.patch_size,
            )
            crop_origins.append(
                (
                    start_x,
                    start_y,
                )
            )

        while len(crop_origins) < self.patch_count:
            crop_origins.append(
                (
                    self.random_generator.randint(
                        0,
                        width - self.patch_size,
                    ),
                    self.random_generator.randint(
                        0,
                        height - self.patch_size,
                    ),
                )
            )

        for patch_index, (
            start_x,
            start_y,
        ) in enumerate(crop_origins[: self.patch_count]):
            patch_tensor, target = self._crop_training_patch(
                image=image,
                points=points,
                start_x=start_x,
                start_y=start_y,
                image_id=(index * self.patch_count + patch_index),
            )
            patch_tensors.append(patch_tensor)
            targets.append(target)

        return (
            torch.stack(patch_tensors),
            targets,
        )

    def _resize_validation_image(
        self,
        *,
        image: Image.Image,
        points: torch.Tensor,
    ) -> tuple[
        Image.Image,
        torch.Tensor,
    ]:
        width, height = image.size
        longest_side = max(
            width,
            height,
        )

        is_size_aligned = (
            width % IMAGE_SIZE_ALIGNMENT == 0 and height % IMAGE_SIZE_ALIGNMENT == 0
        )

        if longest_side <= self.max_long_side and is_size_aligned:
            return image, points

        scale = min(
            1.0,
            (self.max_long_side / longest_side),
        )

        scaled_width = round(
            width * scale,
        )
        scaled_height = round(
            height * scale,
        )

        resized_width = max(
            IMAGE_SIZE_ALIGNMENT,
            (scaled_width // IMAGE_SIZE_ALIGNMENT) * IMAGE_SIZE_ALIGNMENT,
        )
        resized_height = max(
            IMAGE_SIZE_ALIGNMENT,
            (scaled_height // IMAGE_SIZE_ALIGNMENT) * IMAGE_SIZE_ALIGNMENT,
        )

        resized_image = image.resize(
            (
                resized_width,
                resized_height,
            ),
            Image.Resampling.BILINEAR,
        )

        resized_points = points.clone()

        if resized_points.shape[0] > 0:
            resized_points[:, 0] *= resized_width / width
            resized_points[:, 1] *= resized_height / height

        return (
            resized_image,
            resized_points,
        )

    def __getitem__(
        self,
        index: int,
    ) -> tuple[
        torch.Tensor,
        list[dict[str, torch.Tensor]],
    ]:
        sample = self.samples[index]
        image = self._load_image(sample)
        points = self._load_points(sample)

        if self.training:
            return self._build_training_patches(
                image=image,
                points=points,
                index=index,
            )

        image, points = self._resize_validation_image(
            image=image,
            points=points,
        )

        image_tensor = self.transform(image)
        target = self._build_target(
            points=points,
            image_id=index,
        )

        return (
            image_tensor,
            [target],
        )
