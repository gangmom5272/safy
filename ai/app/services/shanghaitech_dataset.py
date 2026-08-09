import csv
import os
from dataclasses import dataclass
from pathlib import Path

from app.services.crowd_ground_truth import (
    load_shanghaitech_ground_truth,
)


@dataclass(frozen=True)
class ShanghaiTechSample:
    sample_id: str
    scenario: str
    image_path: Path
    annotation_path: Path
    ground_truth_count: int


def _extract_image_number(
    image_path: Path,
) -> int:
    return int(image_path.stem.removeprefix("IMG_"))


def discover_shanghaitech_samples(
    test_data_path: Path,
) -> list[ShanghaiTechSample]:
    images_path = test_data_path / "images"
    annotations_path = test_data_path / "ground-truth"

    image_paths = sorted(
        images_path.glob("IMG_*.jpg"),
        key=_extract_image_number,
    )

    samples: list[ShanghaiTechSample] = []

    for image_path in image_paths:
        image_number = _extract_image_number(image_path)
        annotation_path = annotations_path / f"GT_IMG_{image_number}.mat"

        if not annotation_path.exists():
            raise ValueError(f"{image_path.name}의 정답 파일이 없습니다.")

        ground_truth = load_shanghaitech_ground_truth(annotation_path)

        samples.append(
            ShanghaiTechSample(
                sample_id=(f"shanghaitech-a-{image_number}"),
                scenario="CROWDED",
                image_path=image_path,
                annotation_path=annotation_path,
                ground_truth_count=(ground_truth.person_count),
            )
        )

    return samples


def save_shanghaitech_manifest(
    samples: list[ShanghaiTechSample],
    manifest_path: Path,
) -> None:
    manifest_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with manifest_path.open(
        "w",
        encoding="utf-8",
        newline="",
    ) as manifest_file:
        writer = csv.DictWriter(
            manifest_file,
            fieldnames=[
                "sample_id",
                "scenario",
                "image_path",
                "ground_truth_count",
                "source",
                "license",
            ],
        )
        writer.writeheader()

        for sample in samples:
            relative_image_path = Path(
                os.path.relpath(
                    sample.image_path,
                    manifest_path.parent,
                )
            ).as_posix()

            writer.writerow(
                {
                    "sample_id": sample.sample_id,
                    "scenario": sample.scenario,
                    "image_path": (relative_image_path),
                    "ground_truth_count": (sample.ground_truth_count),
                    "source": ("ShanghaiTech Part A"),
                    "license": ("see-dataset-terms"),
                }
            )
