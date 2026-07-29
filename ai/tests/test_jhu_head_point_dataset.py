import csv

import pytest

from pathlib import Path

from PIL import Image

from app.services.head_point_dataset import (
    load_head_point_manifest,
)

from app.services.jhu_head_point_dataset import (
    build_jhu_head_point_dataset,
    convert_jhu_head_point_annotation,
    load_jhu_head_points,
    normalize_jhu_head_points,
)


def test_load_jhu_head_points(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "0001.txt"
    annotation_path.write_text(
        ("100 120 20 25 1 0\n320 240 18 22 2 1\n"),
        encoding="utf-8",
    )

    points = load_jhu_head_points(annotation_path)

    assert points == [
        (100.0, 120.0),
        (320.0, 240.0),
    ]


def test_load_jhu_head_points_rejects_invalid_row(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "invalid.txt"
    annotation_path.write_text(
        "100 120 20\n",
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="JHU 머리 좌표 라벨은 6개 필드가 필요합니다",
    ):
        load_jhu_head_points(annotation_path)


def test_convert_jhu_head_point_annotation(
    tmp_path: Path,
) -> None:
    source_path = tmp_path / "0001.txt"
    output_path = tmp_path / "0001.points.csv"

    source_path.write_text(
        ("100 120 20 25 1 0\n320 240 18 22 2 1\n"),
        encoding="utf-8",
    )

    convert_jhu_head_point_annotation(
        source_path=source_path,
        output_path=output_path,
    )

    assert output_path.read_text(
        encoding="utf-8",
    ) == ("x,y\n100.0,120.0\n320.0,240.0\n")


def test_build_jhu_head_point_dataset(
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "jhu"
    image_directory = source_root / "train" / "images"
    annotation_directory = source_root / "train" / "gt"
    image_directory.mkdir(
        parents=True,
    )
    annotation_directory.mkdir(
        parents=True,
    )

    image_path = image_directory / "0001.jpg"
    source_annotation_path = annotation_directory / "0001.txt"

    Image.new(
        "RGB",
        (
            640,
            480,
        ),
    ).save(image_path)

    source_annotation_path.write_text(
        ("100 120 20 25 1 0\n320 240 18 22 2 1\n"),
        encoding="utf-8",
    )

    output_root = tmp_path / "output"

    manifest_path = build_jhu_head_point_dataset(
        source_root=source_root,
        output_root=output_root,
    )

    samples = load_head_point_manifest(manifest_path)

    assert len(samples) == 1

    sample = samples[0]

    assert sample.sample_id == ("jhu-train-0001")
    assert sample.source == ("JHU-CROWD++ v2.0")
    assert sample.source_group == "jhu-0001"
    assert sample.split == "TRAIN"
    assert sample.image_path == image_path
    assert sample.image_width == 640
    assert sample.image_height == 480
    assert sample.person_count == 2
    assert sample.scenario == "SPARSE"
    assert sample.license == ("jhu-crowd-noncommercial")
    assert sample.consent_confirmed is False
    assert sample.label_format_version == ("head-point-v1")
    assert sample.annotation_path.read_text(
        encoding="utf-8",
    ) == ("x,y\n100.0,120.0\n320.0,240.0\n")


def test_normalize_jhu_head_points_clamps_mild_overflow() -> None:
    points = [
        (-0.5, 40.0),
        (100.0, 60.0),
        (50.0, 100.0),
    ]

    normalized_points, corrected_point_count = normalize_jhu_head_points(
        points=points,
        image_width=100,
        image_height=100,
    )

    assert normalized_points == [
        (0.0, 40.0),
        (99.0, 60.0),
        (50.0, 99.0),
    ]
    assert corrected_point_count == 3


def test_normalize_jhu_head_points_rejects_severe_overflow() -> None:
    with pytest.raises(
        ValueError,
        match="JHU 머리 좌표가 허용 범위를 초과했습니다",
    ):
        normalize_jhu_head_points(
            points=[
                (120.0, 50.0),
            ],
            image_width=100,
            image_height=100,
        )


def test_build_jhu_dataset_corrects_and_excludes_invalid_points(
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "jhu"
    image_directory = source_root / "train" / "images"
    annotation_directory = source_root / "train" / "gt"

    image_directory.mkdir(parents=True)
    annotation_directory.mkdir(parents=True)

    mild_image_path = image_directory / "0001.jpg"
    severe_image_path = image_directory / "0002.jpg"

    Image.new(
        "RGB",
        (640, 480),
    ).save(mild_image_path)

    Image.new(
        "RGB",
        (640, 480),
    ).save(severe_image_path)

    (annotation_directory / "0001.txt").write_text(
        "640 100 20 25 1 0\n",
        encoding="utf-8",
    )

    (annotation_directory / "0002.txt").write_text(
        "800 100 20 25 1 0\n",
        encoding="utf-8",
    )

    output_root = tmp_path / "output"

    manifest_path = build_jhu_head_point_dataset(
        source_root=source_root,
        output_root=output_root,
    )

    samples = load_head_point_manifest(manifest_path)

    assert len(samples) == 1
    assert samples[0].sample_id == ("jhu-train-0001")
    assert samples[0].person_count == 1
    assert samples[0].annotation_path.read_text(
        encoding="utf-8",
    ) == ("x,y\n639.0,100.0\n")

    report_path = output_root / "conversion-report.csv"

    with report_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        report_records = list(csv.DictReader(stream))

    assert report_records == [
        {
            "sample_id": "jhu-train-0001",
            "status": "CORRECTED",
            "corrected_point_count": "1",
            "reason": "COORDINATE_CLAMPED",
        },
        {
            "sample_id": "jhu-train-0002",
            "status": "EXCLUDED",
            "corrected_point_count": "0",
            "reason": ("COORDINATE_OVERFLOW_EXCEEDED"),
        },
    ]
