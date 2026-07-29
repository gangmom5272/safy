import os
import csv
import hashlib
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageOps


HEAD_POINT_LABEL_VERSION = "head-point-v1"

VALID_DATASET_SPLITS = frozenset(
    {
        "TRAIN",
        "VALIDATION",
        "TEST",
    }
)


@dataclass(frozen=True)
class HeadPointAnnotation:
    points: list[tuple[float, float]]

    @property
    def person_count(self) -> int:
        return len(self.points)


@dataclass(frozen=True)
class HeadPointDatasetSummary:
    sample_count: int
    person_count: int
    split_counts: dict[str, int]
    scenario_counts: dict[str, int]
    source_counts: dict[str, int]


@dataclass(frozen=True)
class HeadPointSample:
    sample_id: str
    source: str
    source_group: str
    split: str
    image_path: Path
    annotation_path: Path
    image_width: int
    image_height: int
    person_count: int
    scenario: str
    license: str
    consent_confirmed: bool
    image_sha256: str
    label_format_version: str


def load_head_point_annotation(
    annotation_path: Path,
    image_width: int,
    image_height: int,
) -> HeadPointAnnotation:
    if image_width < 1 or image_height < 1:
        raise ValueError("이미지 너비와 높이는 1 이상이어야 합니다")

    points: list[tuple[float, float]] = []
    seen_points: set[tuple[float, float]] = set()

    with annotation_path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)

        if reader.fieldnames != [
            "x",
            "y",
        ]:
            raise ValueError("머리 좌표 라벨에는 x,y 헤더가 필요합니다")

        for row in reader:
            point_x = float(row["x"])
            point_y = float(row["y"])
            point = (
                point_x,
                point_y,
            )

            if not (0.0 <= point_x < image_width and 0.0 <= point_y < image_height):
                raise ValueError("머리 좌표는 이미지 범위 안이어야 합니다")

            if point in seen_points:
                raise ValueError("머리 좌표는 중복될 수 없습니다")

            points.append(point)
            seen_points.add(point)

    return HeadPointAnnotation(
        points=points,
    )


def save_head_point_annotation(
    points: list[tuple[float, float]],
    annotation_path: Path,
) -> None:
    annotation_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with annotation_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.writer(
            stream,
            lineterminator="\n",
        )
        writer.writerow(
            [
                "x",
                "y",
            ]
        )

        for point_x, point_y in points:
            writer.writerow(
                [
                    float(point_x),
                    float(point_y),
                ]
            )


def load_head_point_manifest(
    manifest_path: Path,
) -> list[HeadPointSample]:
    samples: list[HeadPointSample] = []

    with manifest_path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)

        for row in reader:
            image_path = (manifest_path.parent / row["image_path"]).resolve()
            annotation_path = (manifest_path.parent / row["annotation_path"]).resolve()

            samples.append(
                HeadPointSample(
                    sample_id=row["sample_id"],
                    source=row["source"],
                    source_group=(row["source_group"]),
                    split=row["split"],
                    image_path=image_path,
                    annotation_path=annotation_path,
                    image_width=int(row["image_width"]),
                    image_height=int(row["image_height"]),
                    person_count=int(row["person_count"]),
                    scenario=row["scenario"],
                    license=row["license"],
                    consent_confirmed=(
                        row["consent_confirmed"].strip().lower() == "true"
                    ),
                    image_sha256=row["image_sha256"],
                    label_format_version=(row["label_format_version"]),
                )
            )

    validate_head_point_samples(samples)

    return samples


def validate_head_point_samples(
    samples: list[HeadPointSample],
) -> None:
    if not samples:
        raise ValueError("머리 좌표 학습 표본이 한 건 이상 필요합니다")

    seen_sample_ids: set[str] = set()
    image_splits: dict[str, str] = {}
    source_group_splits: dict[
        tuple[str, str],
        str,
    ] = {}

    for sample in samples:
        if sample.source == "TEAM_CONSENTED" and not sample.consent_confirmed:
            raise ValueError("팀 사진은 사용 동의가 확인되어야 합니다")

        if sample.split not in VALID_DATASET_SPLITS:
            raise ValueError("데이터 분할은 TRAIN, VALIDATION, TEST 중 하나여야 합니다")

        if sample.label_format_version != HEAD_POINT_LABEL_VERSION:
            raise ValueError("지원하지 않는 머리 좌표 라벨 버전입니다")

        if sample.sample_id in seen_sample_ids:
            raise ValueError("sample_id는 중복될 수 없습니다")

        previous_image_split = image_splits.get(
            sample.image_sha256,
        )

        if previous_image_split is not None and previous_image_split != sample.split:
            raise ValueError("동일한 이미지는 여러 데이터 분할에 포함될 수 없습니다")

        source_group_key = (
            sample.source,
            sample.source_group,
        )
        previous_group_split = source_group_splits.get(
            source_group_key,
        )

        if previous_group_split is not None and previous_group_split != sample.split:
            raise ValueError(
                "동일한 source_group은 여러 데이터 분할에 포함될 수 없습니다"
            )

        seen_sample_ids.add(
            sample.sample_id,
        )
        image_splits[sample.image_sha256] = sample.split
        source_group_splits[source_group_key] = sample.split


def validate_head_point_sample_files(
    sample: HeadPointSample,
) -> None:
    if not sample.annotation_path.is_file():
        raise ValueError("머리 좌표 라벨 파일이 존재해야 합니다")

    actual_image_sha256 = hashlib.sha256(sample.image_path.read_bytes()).hexdigest()

    if actual_image_sha256.lower() != sample.image_sha256.lower():
        raise ValueError("이미지 SHA-256이 manifest와 일치해야 합니다")

    with Image.open(
        sample.image_path,
    ) as image:
        oriented_image = ImageOps.exif_transpose(image)
        actual_width, actual_height = oriented_image.size

    if actual_width != sample.image_width or actual_height != sample.image_height:
        raise ValueError("이미지 크기가 manifest와 일치해야 합니다")

    annotation = load_head_point_annotation(
        annotation_path=sample.annotation_path,
        image_width=sample.image_width,
        image_height=sample.image_height,
    )

    if annotation.person_count != sample.person_count:
        raise ValueError("person_count와 머리 좌표 개수가 일치해야 합니다")


def validate_head_point_dataset(
    manifest_path: Path,
) -> HeadPointDatasetSummary:
    samples = load_head_point_manifest(manifest_path)

    for sample in samples:
        validate_head_point_sample_files(sample)

    return HeadPointDatasetSummary(
        sample_count=len(samples),
        person_count=sum(sample.person_count for sample in samples),
        split_counts=dict(Counter(sample.split for sample in samples)),
        scenario_counts=dict(Counter(sample.scenario for sample in samples)),
        source_counts=dict(Counter(sample.source for sample in samples)),
    )


def combine_head_point_manifests(
    *,
    manifest_paths: list[Path],
    output_path: Path,
) -> Path:
    if not manifest_paths:
        raise ValueError("결합할 머리 좌표 manifest가 한 개 이상 필요합니다.")

    samples: list[HeadPointSample] = []

    for manifest_path in manifest_paths:
        manifest_samples = load_head_point_manifest(manifest_path)

        for sample in manifest_samples:
            validate_head_point_sample_files(sample)

        samples.extend(manifest_samples)

    validate_head_point_samples(samples)

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    fieldnames = [
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

    records: list[dict[str, object]] = []

    for sample in samples:
        relative_image_path = Path(
            os.path.relpath(
                sample.image_path.resolve(),
                output_path.parent.resolve(),
            )
        ).as_posix()

        relative_annotation_path = Path(
            os.path.relpath(
                sample.annotation_path.resolve(),
                output_path.parent.resolve(),
            )
        ).as_posix()

        records.append(
            {
                "sample_id": (sample.sample_id),
                "source": sample.source,
                "source_group": (sample.source_group),
                "split": sample.split,
                "image_path": (relative_image_path),
                "annotation_path": (relative_annotation_path),
                "image_width": (sample.image_width),
                "image_height": (sample.image_height),
                "person_count": (sample.person_count),
                "scenario": sample.scenario,
                "license": sample.license,
                "consent_confirmed": (str(sample.consent_confirmed).lower()),
                "image_sha256": (sample.image_sha256),
                "label_format_version": (sample.label_format_version),
            }
        )

    with output_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fieldnames,
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(records)

    return output_path
