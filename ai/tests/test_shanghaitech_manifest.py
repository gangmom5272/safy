import csv
from pathlib import Path

from app.services.shanghaitech_dataset import (
    ShanghaiTechSample,
    save_shanghaitech_manifest,
)


def test_save_shanghaitech_manifest_writes_csv(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "dataset" / "images" / "IMG_1.jpg"
    annotation_path = tmp_path / "dataset" / "ground-truth" / "GT_IMG_1.mat"

    image_path.parent.mkdir(parents=True)
    annotation_path.parent.mkdir(parents=True)

    image_path.write_bytes(b"image")
    annotation_path.write_bytes(b"annotation")

    samples = [
        ShanghaiTechSample(
            sample_id="shanghaitech-a-1",
            scenario="CROWDED",
            image_path=image_path,
            annotation_path=annotation_path,
            ground_truth_count=172,
        )
    ]

    manifest_path = tmp_path / "manifest.csv"

    save_shanghaitech_manifest(
        samples,
        manifest_path,
    )

    with manifest_path.open(
        "r",
        encoding="utf-8",
        newline="",
    ) as manifest_file:
        rows = list(csv.DictReader(manifest_file))

    assert rows == [
        {
            "sample_id": "shanghaitech-a-1",
            "scenario": "CROWDED",
            "image_path": ("dataset/images/IMG_1.jpg"),
            "ground_truth_count": "172",
            "source": "ShanghaiTech Part A",
            "license": "see-dataset-terms",
        }
    ]
