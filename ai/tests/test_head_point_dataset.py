import pytest
import hashlib

from PIL import Image
from pathlib import Path
from dataclasses import replace

from app.services.head_point_dataset import (
    HEAD_POINT_LABEL_VERSION,
    HeadPointSample,
    load_head_point_annotation,
    load_head_point_manifest,
    save_head_point_annotation,
    validate_head_point_dataset,
    validate_head_point_sample_files,
    validate_head_point_samples,
)


def test_load_head_point_annotation(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "sample.points.csv"
    annotation_path.write_text(
        ("x,y\n100,120\n320,240\n"),
        encoding="utf-8",
    )

    annotation = load_head_point_annotation(
        annotation_path=annotation_path,
        image_width=640,
        image_height=480,
    )

    assert HEAD_POINT_LABEL_VERSION == ("head-point-v1")
    assert annotation.person_count == 2
    assert annotation.points == [
        (100.0, 120.0),
        (320.0, 240.0),
    ]


def test_load_head_point_annotation_rejects_point_outside_image(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "invalid.points.csv"
    annotation_path.write_text(
        ("x,y\n640,120\n"),
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="머리 좌표는 이미지 범위 안이어야 합니다",
    ):
        load_head_point_annotation(
            annotation_path=annotation_path,
            image_width=640,
            image_height=480,
        )


def test_load_head_point_annotation_rejects_duplicate_points(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "duplicate.points.csv"
    annotation_path.write_text(
        ("x,y\n100,120\n100,120\n"),
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="머리 좌표는 중복될 수 없습니다",
    ):
        load_head_point_annotation(
            annotation_path=annotation_path,
            image_width=640,
            image_height=480,
        )


def test_load_head_point_annotation_rejects_invalid_header(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "invalid-header.points.csv"
    annotation_path.write_text(
        ("center_x,center_y\n100,120\n"),
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="머리 좌표 라벨에는 x,y 헤더가 필요합니다",
    ):
        load_head_point_annotation(
            annotation_path=annotation_path,
            image_width=640,
            image_height=480,
        )


def test_load_head_point_annotation_rejects_invalid_image_size(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "empty.points.csv"
    annotation_path.write_text(
        "x,y\n",
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="이미지 너비와 높이는 1 이상이어야 합니다",
    ):
        load_head_point_annotation(
            annotation_path=annotation_path,
            image_width=0,
            image_height=480,
        )


def test_load_empty_head_point_annotation(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "empty.points.csv"
    annotation_path.write_text(
        "x,y\n",
        encoding="utf-8",
    )

    annotation = load_head_point_annotation(
        annotation_path=annotation_path,
        image_width=640,
        image_height=480,
    )

    assert annotation.person_count == 0
    assert annotation.points == []


def test_load_head_point_manifest(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "images" / "sample.jpg"
    annotation_path = tmp_path / "annotations" / "sample.points.csv"
    image_path.parent.mkdir()
    annotation_path.parent.mkdir()

    image_path.write_bytes(b"image")
    annotation_path.write_text(
        ("x,y\n100,120\n320,240\n"),
        encoding="utf-8",
    )

    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,"
            "image_width,image_height,"
            "person_count,scenario,license,"
            "consent_confirmed,image_sha256,"
            "label_format_version\n"
            "team-001,TEAM_CONSENTED,event-001,TRAIN,"
            "images/sample.jpg,"
            "annotations/sample.points.csv,"
            "640,480,2,NORMAL,internal-consent,"
            "true,"
            f"{'a' * 64},"
            "head-point-v1\n"
        ),
        encoding="utf-8",
    )

    samples = load_head_point_manifest(
        manifest_path,
    )

    assert len(samples) == 1

    sample = samples[0]

    assert sample.sample_id == "team-001"
    assert sample.source == "TEAM_CONSENTED"
    assert sample.source_group == "event-001"
    assert sample.split == "TRAIN"
    assert sample.image_path == image_path
    assert sample.annotation_path == annotation_path
    assert sample.image_width == 640
    assert sample.image_height == 480
    assert sample.person_count == 2
    assert sample.scenario == "NORMAL"
    assert sample.license == "internal-consent"
    assert sample.consent_confirmed is True
    assert sample.image_sha256 == "a" * 64
    assert sample.label_format_version == ("head-point-v1")


def test_validate_head_point_samples_rejects_duplicate_ids(
    tmp_path: Path,
) -> None:
    sample = HeadPointSample(
        sample_id="same-id",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=tmp_path / "first.jpg",
        annotation_path=(tmp_path / "first.points.csv"),
        image_width=640,
        image_height=480,
        person_count=2,
        scenario="NORMAL",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256="a" * 64,
        label_format_version="head-point-v1",
    )
    duplicate = replace(
        sample,
        image_path=tmp_path / "second.jpg",
        annotation_path=(tmp_path / "second.points.csv"),
        image_sha256="b" * 64,
    )

    with pytest.raises(
        ValueError,
        match="sample_id는 중복될 수 없습니다",
    ):
        validate_head_point_samples(
            [
                sample,
                duplicate,
            ]
        )


def test_validate_head_point_samples_rejects_image_split_leakage(
    tmp_path: Path,
) -> None:
    train_sample = HeadPointSample(
        sample_id="train-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=tmp_path / "train.jpg",
        annotation_path=(tmp_path / "train.points.csv"),
        image_width=640,
        image_height=480,
        person_count=2,
        scenario="NORMAL",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256="a" * 64,
        label_format_version="head-point-v1",
    )
    validation_sample = replace(
        train_sample,
        sample_id="validation-001",
        split="VALIDATION",
        image_path=tmp_path / "validation.jpg",
        annotation_path=(tmp_path / "validation.points.csv"),
    )

    with pytest.raises(
        ValueError,
        match="동일한 이미지는 여러 데이터 분할에 포함될 수 없습니다",
    ):
        validate_head_point_samples(
            [
                train_sample,
                validation_sample,
            ]
        )


def test_validate_head_point_samples_rejects_source_group_split_leakage(
    tmp_path: Path,
) -> None:
    train_sample = HeadPointSample(
        sample_id="event-001-frame-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=tmp_path / "frame-001.jpg",
        annotation_path=(tmp_path / "frame-001.points.csv"),
        image_width=640,
        image_height=480,
        person_count=2,
        scenario="NORMAL",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256="a" * 64,
        label_format_version="head-point-v1",
    )
    validation_sample = replace(
        train_sample,
        sample_id="event-001-frame-002",
        split="VALIDATION",
        image_path=tmp_path / "frame-002.jpg",
        annotation_path=(tmp_path / "frame-002.points.csv"),
        image_sha256="b" * 64,
    )

    with pytest.raises(
        ValueError,
        match="동일한 source_group은 여러 데이터 분할에 포함될 수 없습니다",
    ):
        validate_head_point_samples(
            [
                train_sample,
                validation_sample,
            ]
        )


def test_validate_head_point_samples_requires_team_consent(
    tmp_path: Path,
) -> None:
    sample = HeadPointSample(
        sample_id="team-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=tmp_path / "sample.jpg",
        annotation_path=(tmp_path / "sample.points.csv"),
        image_width=640,
        image_height=480,
        person_count=2,
        scenario="NORMAL",
        license="internal-consent",
        consent_confirmed=False,
        image_sha256="a" * 64,
        label_format_version="head-point-v1",
    )

    with pytest.raises(
        ValueError,
        match="팀 사진은 사용 동의가 확인되어야 합니다",
    ):
        validate_head_point_samples([sample])


def test_validate_head_point_samples_rejects_unknown_split(
    tmp_path: Path,
) -> None:
    sample = HeadPointSample(
        sample_id="team-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="UNKNOWN",
        image_path=tmp_path / "sample.jpg",
        annotation_path=(tmp_path / "sample.points.csv"),
        image_width=640,
        image_height=480,
        person_count=2,
        scenario="NORMAL",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256="a" * 64,
        label_format_version="head-point-v1",
    )

    with pytest.raises(
        ValueError,
        match="데이터 분할은 TRAIN, VALIDATION, TEST 중 하나여야 합니다",
    ):
        validate_head_point_samples([sample])


def test_validate_head_point_samples_rejects_unknown_label_version(
    tmp_path: Path,
) -> None:
    sample = HeadPointSample(
        sample_id="team-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=tmp_path / "sample.jpg",
        annotation_path=(tmp_path / "sample.points.csv"),
        image_width=640,
        image_height=480,
        person_count=2,
        scenario="NORMAL",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256="a" * 64,
        label_format_version="unknown-version",
    )

    with pytest.raises(
        ValueError,
        match="지원하지 않는 머리 좌표 라벨 버전입니다",
    ):
        validate_head_point_samples([sample])


def test_validate_head_point_sample_files_rejects_count_mismatch(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "sample.jpg"
    annotation_path = tmp_path / "sample.points.csv"

    Image.new(
        "RGB",
        (
            640,
            480,
        ),
    ).save(image_path)

    annotation_path.write_text(
        ("x,y\n100,120\n320,240\n"),
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    sample = HeadPointSample(
        sample_id="team-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=image_path,
        annotation_path=annotation_path,
        image_width=640,
        image_height=480,
        person_count=3,
        scenario="NORMAL",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256=image_sha256,
        label_format_version="head-point-v1",
    )

    with pytest.raises(
        ValueError,
        match="person_count와 머리 좌표 개수가 일치해야 합니다",
    ):
        validate_head_point_sample_files(sample)


def test_validate_head_point_sample_files_rejects_hash_mismatch(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "sample.jpg"
    annotation_path = tmp_path / "sample.points.csv"

    Image.new(
        "RGB",
        (
            640,
            480,
        ),
    ).save(image_path)

    annotation_path.write_text(
        ("x,y\n100,120\n"),
        encoding="utf-8",
    )

    sample = HeadPointSample(
        sample_id="team-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=image_path,
        annotation_path=annotation_path,
        image_width=640,
        image_height=480,
        person_count=1,
        scenario="SPARSE",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256="0" * 64,
        label_format_version="head-point-v1",
    )

    with pytest.raises(
        ValueError,
        match="이미지 SHA-256이 manifest와 일치해야 합니다",
    ):
        validate_head_point_sample_files(sample)


def test_validate_head_point_sample_files_rejects_image_size_mismatch(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "sample.jpg"
    annotation_path = tmp_path / "sample.points.csv"

    Image.new(
        "RGB",
        (
            320,
            240,
        ),
    ).save(image_path)

    annotation_path.write_text(
        ("x,y\n100,120\n"),
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    sample = HeadPointSample(
        sample_id="team-001",
        source="TEAM_CONSENTED",
        source_group="event-001",
        split="TRAIN",
        image_path=image_path,
        annotation_path=annotation_path,
        image_width=640,
        image_height=480,
        person_count=1,
        scenario="SPARSE",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256=image_sha256,
        label_format_version="head-point-v1",
    )

    with pytest.raises(
        ValueError,
        match="이미지 크기가 manifest와 일치해야 합니다",
    ):
        validate_head_point_sample_files(sample)


def test_validate_head_point_samples_rejects_empty_samples() -> None:
    with pytest.raises(
        ValueError,
        match="머리 좌표 학습 표본이 한 건 이상 필요합니다",
    ):
        validate_head_point_samples([])


def test_load_head_point_manifest_validates_loaded_samples(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,"
            "image_width,image_height,"
            "person_count,scenario,license,"
            "consent_confirmed,image_sha256,"
            "label_format_version\n"
        ),
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="머리 좌표 학습 표본이 한 건 이상 필요합니다",
    ):
        load_head_point_manifest(manifest_path)


def test_validate_head_point_dataset_returns_summary(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "sample.jpg"
    annotation_path = tmp_path / "sample.points.csv"

    Image.new(
        "RGB",
        (
            640,
            480,
        ),
    ).save(image_path)

    annotation_path.write_text(
        ("x,y\n100,120\n"),
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,"
            "image_width,image_height,"
            "person_count,scenario,license,"
            "consent_confirmed,image_sha256,"
            "label_format_version\n"
            "team-001,TEAM_CONSENTED,event-001,TRAIN,"
            "sample.jpg,sample.points.csv,"
            "640,480,1,SPARSE,internal-consent,"
            f"true,{image_sha256},head-point-v1\n"
        ),
        encoding="utf-8",
    )

    summary = validate_head_point_dataset(manifest_path)

    assert summary.sample_count == 1
    assert summary.person_count == 1
    assert summary.split_counts == {
        "TRAIN": 1,
    }
    assert summary.scenario_counts == {
        "SPARSE": 1,
    }
    assert summary.source_counts == {
        "TEAM_CONSENTED": 1,
    }


def test_save_head_point_annotation(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "sample.points.csv"

    save_head_point_annotation(
        points=[
            (100.0, 120.0),
            (320.0, 240.0),
        ],
        annotation_path=annotation_path,
    )

    assert annotation_path.read_text(
        encoding="utf-8",
    ) == ("x,y\n100.0,120.0\n320.0,240.0\n")


def test_validate_head_point_sample_files_applies_exif_orientation(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "portrait.jpg"
    annotation_path = tmp_path / "portrait.points.csv"

    image = Image.new(
        "RGB",
        (
            640,
            480,
        ),
    )
    exif = image.getexif()
    exif[274] = 6

    image.save(
        image_path,
        format="JPEG",
        exif=exif,
    )

    annotation_path.write_text(
        ("x,y\n100.0,120.0\n"),
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    sample = HeadPointSample(
        sample_id="team-portrait",
        source="TEAM_CONSENTED",
        source_group="team-portrait",
        split="TRAIN",
        image_path=image_path,
        annotation_path=annotation_path,
        image_width=480,
        image_height=640,
        person_count=1,
        scenario="SPARSE",
        license="internal-consent",
        consent_confirmed=True,
        image_sha256=image_sha256,
        label_format_version="head-point-v1",
    )

    validate_head_point_sample_files(sample)
