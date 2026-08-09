import csv
import hashlib
import os
from pathlib import Path

from PIL import Image

from app.services.congestion import (
    calculate_congestion,
)
from app.services.head_point_dataset import (
    HEAD_POINT_LABEL_VERSION,
    save_head_point_annotation,
)


JHU_SOURCE_NAME = "JHU-CROWD++ v2.0"
JHU_LICENSE = "jhu-crowd-noncommercial"

JHU_SPLIT_NAMES = {
    "train": "TRAIN",
    "val": "VALIDATION",
    "test": "TEST",
}


MAX_JHU_COORDINATE_OVERFLOW_RATIO = 0.01


def normalize_jhu_head_points(
    points: list[tuple[float, float]],
    image_width: int,
    image_height: int,
) -> tuple[
    list[tuple[float, float]],
    int,
]:
    if image_width <= 0 or image_height <= 0:
        raise ValueError("이미지 크기는 양수여야 합니다.")

    maximum_x = float(image_width - 1)
    maximum_y = float(image_height - 1)

    normalized_points: list[tuple[float, float]] = []
    corrected_point_count = 0

    for point_x, point_y in points:
        overflow_x = max(
            0.0,
            -point_x,
            point_x - maximum_x,
        )
        overflow_y = max(
            0.0,
            -point_y,
            point_y - maximum_y,
        )

        overflow_ratio = max(
            overflow_x / image_width,
            overflow_y / image_height,
        )

        if overflow_ratio > MAX_JHU_COORDINATE_OVERFLOW_RATIO:
            raise ValueError("JHU 머리 좌표가 허용 범위를 초과했습니다")

        normalized_x = min(
            max(point_x, 0.0),
            maximum_x,
        )
        normalized_y = min(
            max(point_y, 0.0),
            maximum_y,
        )

        if normalized_x != point_x or normalized_y != point_y:
            corrected_point_count += 1

        normalized_points.append(
            (
                normalized_x,
                normalized_y,
            )
        )

    return (
        normalized_points,
        corrected_point_count,
    )


def load_jhu_head_points(
    annotation_path: Path,
) -> list[tuple[float, float]]:
    points: list[tuple[float, float]] = []

    lines = annotation_path.read_text(
        encoding="utf-8",
    ).splitlines()

    for line in lines:
        fields = line.split()

        if not fields:
            continue

        if len(fields) != 6:
            raise ValueError("JHU 머리 좌표 라벨은 6개 필드가 필요합니다")

        point_x = float(fields[0])
        point_y = float(fields[1])

        points.append(
            (
                point_x,
                point_y,
            )
        )

    return points


def convert_jhu_head_point_annotation(
    source_path: Path,
    output_path: Path,
) -> None:
    points = load_jhu_head_points(source_path)

    save_head_point_annotation(
        points=points,
        annotation_path=output_path,
    )


def _classify_jhu_scenario(
    points: list[tuple[float, float]],
    image_width: int,
    image_height: int,
) -> str:
    person_count = len(points)

    if person_count == 0:
        return "EMPTY"

    if person_count <= 5:
        return "SPARSE"

    if person_count > 50:
        return "CROWDED"

    normalized_points = [
        (
            point_x / image_width,
            point_y / image_height,
        )
        for point_x, point_y in points
    ]
    congestion = calculate_congestion(normalized_points)

    if congestion.local_peak_congestion_score >= 37.5:
        return "LOCAL_DENSE"

    return "NORMAL"


def build_jhu_head_point_dataset(
    source_root: Path,
    output_root: Path,
) -> Path:
    output_root.mkdir(
        parents=True,
        exist_ok=True,
    )

    manifest_path = output_root / "manifest.csv"
    conversion_report_path = output_root / "conversion-report.csv"

    records: list[dict[str, object]] = []
    conversion_records: list[dict[str, object]] = []

    for source_split, dataset_split in JHU_SPLIT_NAMES.items():
        image_directory = source_root / source_split / "images"
        annotation_directory = source_root / source_split / "gt"

        if not image_directory.is_dir():
            continue

        for image_path in sorted(image_directory.glob("*.jpg")):
            sample_name = image_path.stem
            sample_id = f"jhu-{source_split}-{sample_name}"
            source_annotation_path = annotation_directory / f"{sample_name}.txt"

            points = load_jhu_head_points(source_annotation_path)

            with Image.open(image_path) as image:
                image_width, image_height = image.size

            try:
                (
                    normalized_points,
                    corrected_point_count,
                ) = normalize_jhu_head_points(
                    points=points,
                    image_width=image_width,
                    image_height=image_height,
                )
            except ValueError:
                conversion_records.append(
                    {
                        "sample_id": sample_id,
                        "status": "EXCLUDED",
                        "corrected_point_count": 0,
                        "reason": ("COORDINATE_OVERFLOW_EXCEEDED"),
                    }
                )
                continue

            if corrected_point_count > 0:
                conversion_records.append(
                    {
                        "sample_id": sample_id,
                        "status": "CORRECTED",
                        "corrected_point_count": (corrected_point_count),
                        "reason": ("COORDINATE_CLAMPED"),
                    }
                )

            output_annotation_path = (
                output_root
                / "annotations"
                / source_split
                / (f"{sample_name}.points.csv")
            )

            save_head_point_annotation(
                points=normalized_points,
                annotation_path=(output_annotation_path),
            )

            image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

            relative_image_path = Path(
                os.path.relpath(
                    image_path.resolve(),
                    output_root.resolve(),
                )
            ).as_posix()

            relative_annotation_path = Path(
                os.path.relpath(
                    output_annotation_path.resolve(),
                    output_root.resolve(),
                )
            ).as_posix()

            records.append(
                {
                    "sample_id": sample_id,
                    "source": JHU_SOURCE_NAME,
                    "source_group": (f"jhu-{sample_name}"),
                    "split": dataset_split,
                    "image_path": (relative_image_path),
                    "annotation_path": (relative_annotation_path),
                    "image_width": image_width,
                    "image_height": image_height,
                    "person_count": len(normalized_points),
                    "scenario": (
                        _classify_jhu_scenario(
                            points=(normalized_points),
                            image_width=image_width,
                            image_height=(image_height),
                        )
                    ),
                    "license": JHU_LICENSE,
                    "consent_confirmed": ("false"),
                    "image_sha256": (image_sha256),
                    "label_format_version": (HEAD_POINT_LABEL_VERSION),
                }
            )

    manifest_fieldnames = [
        "sample_id",
        "source",
        "source_group",
        "split",
        "image_path",
        "annotation_path",
        "image_width",
        "image_height",
        "person_count",
        "scenario",
        "license",
        "consent_confirmed",
        "image_sha256",
        "label_format_version",
    ]

    with manifest_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=manifest_fieldnames,
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(records)

    conversion_fieldnames = [
        "sample_id",
        "status",
        "corrected_point_count",
        "reason",
    ]

    with conversion_report_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=conversion_fieldnames,
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(conversion_records)

    return manifest_path
