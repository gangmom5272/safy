import hashlib
from pathlib import Path

import torch
from PIL import Image

from app.services.p2pnet_finetune_dataset import (
    P2PNetManifestDataset,
)


def test_p2pnet_manifest_dataset_loads_validation_sample(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "sample.jpg"
    annotation_path = tmp_path / "sample.points.csv"

    Image.new(
        "RGB",
        (
            256,
            128,
        ),
        color="white",
    ).save(image_path)

    annotation_path.write_text(
        ("x,y\n100.0,60.0\n200.0,80.0\n"),
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
            "sample-001,TEST_SOURCE,group-001,"
            "VALIDATION,sample.jpg,"
            "sample.points.csv,256,128,2,"
            "SPARSE,internal,false,"
            f"{image_sha256},head-point-v1\n"
        ),
        encoding="utf-8",
    )

    dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="VALIDATION",
        training=False,
    )

    assert len(dataset) == 1

    image_tensor, targets = dataset[0]

    assert image_tensor.shape == (
        3,
        128,
        256,
    )
    assert len(targets) == 1

    target = targets[0]

    assert torch.equal(
        target["point"],
        torch.tensor(
            [
                [100.0, 60.0],
                [200.0, 80.0],
            ]
        ),
    )
    assert torch.equal(
        target["labels"],
        torch.ones(
            2,
            dtype=torch.long,
        ),
    )
    assert torch.equal(
        target["image_id"],
        torch.tensor(
            [0],
            dtype=torch.long,
        ),
    )


def test_training_dataset_creates_positive_head_centered_patch(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "training.jpg"
    annotation_path = tmp_path / "training.points.csv"

    Image.new(
        "RGB",
        (
            256,
            256,
        ),
        color="white",
    ).save(image_path)

    annotation_path.write_text(
        ("x,y\n100.0,100.0\n"),
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    manifest_path = tmp_path / "training-manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,"
            "image_width,image_height,"
            "person_count,scenario,license,"
            "consent_confirmed,image_sha256,"
            "label_format_version\n"
            "training-001,TEST_SOURCE,group-001,"
            "TRAIN,training.jpg,"
            "training.points.csv,256,256,1,"
            "SPARSE,internal,false,"
            f"{image_sha256},head-point-v1\n"
        ),
        encoding="utf-8",
    )

    dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="TRAIN",
        training=True,
        patch_size=128,
        patch_count=2,
        random_seed=42,
    )

    image_patches, targets = dataset[0]

    assert image_patches.shape == (
        2,
        3,
        128,
        128,
    )
    assert len(targets) == 2

    assert torch.equal(
        targets[0]["point"],
        torch.tensor(
            [
                [64.0, 64.0],
            ]
        ),
    )
    assert torch.equal(
        targets[0]["labels"],
        torch.ones(
            1,
            dtype=torch.long,
        ),
    )


def test_training_dataset_creates_background_patches_for_empty_sample(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "empty.jpg"
    annotation_path = tmp_path / "empty.csv"
    manifest_path = tmp_path / "manifest.csv"

    Image.new(
        "RGB",
        (256, 256),
        color=(128, 128, 128),
    ).save(image_path)

    annotation_path.write_text(
        "x,y\n",
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(
        image_path.read_bytes(),
    ).hexdigest()

    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,image_width,"
            "image_height,person_count,scenario,"
            "license,consent_confirmed,image_sha256,"
            "label_format_version\n"
            f"empty-1,TEAM_CONSENTED,team-empty,TRAIN,"
            f"{image_path.name},{annotation_path.name},"
            "256,256,0,EMPTY,team-consent,true,"
            f"{image_sha256},head-point-v1\n"
        ),
        encoding="utf-8",
    )

    dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="TRAIN",
        training=True,
        patch_size=128,
        patch_count=2,
        random_seed=42,
    )

    image_patches, targets = dataset[0]

    assert image_patches.shape == (
        2,
        3,
        128,
        128,
    )
    assert len(targets) == 2

    for target in targets:
        assert target["point"].shape == (0, 2)
        assert target["labels"].shape == (0,)


def test_validation_dataset_limits_large_image_size(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "large.jpg"
    annotation_path = tmp_path / "large.csv"
    manifest_path = tmp_path / "manifest.csv"

    Image.new(
        "RGB",
        (2048, 1024),
        color=(128, 128, 128),
    ).save(image_path)

    annotation_path.write_text(
        "x,y\n1024,512\n",
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(
        image_path.read_bytes(),
    ).hexdigest()

    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,image_width,"
            "image_height,person_count,scenario,"
            "license,consent_confirmed,image_sha256,"
            "label_format_version\n"
            f"large-1,JHU-CROWD++ v2.0,jhu-large,"
            f"VALIDATION,{image_path.name},"
            f"{annotation_path.name},2048,1024,1,"
            "SPARSE,JHU-CROWD++,false,"
            f"{image_sha256},head-point-v1\n"
        ),
        encoding="utf-8",
    )

    dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="VALIDATION",
        training=False,
        max_long_side=1536,
    )

    image_tensor, targets = dataset[0]

    assert image_tensor.shape == (
        3,
        768,
        1536,
    )
    assert targets[0]["point"].tolist() == [
        [768.0, 384.0],
    ]


def test_validation_dataset_aligns_small_image_size(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "unaligned.jpg"
    annotation_path = tmp_path / "unaligned.csv"
    manifest_path = tmp_path / "manifest.csv"

    Image.new(
        "RGB",
        (1000, 600),
        color=(128, 128, 128),
    ).save(image_path)

    annotation_path.write_text(
        "x,y\n500,300\n",
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(
        image_path.read_bytes(),
    ).hexdigest()

    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,image_width,"
            "image_height,person_count,scenario,"
            "license,consent_confirmed,image_sha256,"
            "label_format_version\n"
            f"unaligned-1,JHU-CROWD++ v2.0,"
            f"jhu-unaligned,VALIDATION,"
            f"{image_path.name},"
            f"{annotation_path.name},1000,600,1,"
            "SPARSE,JHU-CROWD++,false,"
            f"{image_sha256},head-point-v1\n"
        ),
        encoding="utf-8",
    )

    dataset = P2PNetManifestDataset(
        manifest_path=manifest_path,
        split="VALIDATION",
        training=False,
        max_long_side=1536,
    )

    image_tensor, targets = dataset[0]

    assert image_tensor.shape == (
        3,
        512,
        896,
    )
    assert targets[0]["point"].tolist() == [
        [448.0, 256.0],
    ]
