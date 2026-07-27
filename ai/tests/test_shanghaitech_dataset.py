from pathlib import Path

from app.services import shanghaitech_dataset
from app.services.crowd_ground_truth import (
    CrowdGroundTruth,
)


def test_discover_shanghaitech_samples_pairs_files(
    tmp_path: Path,
    monkeypatch,
) -> None:
    test_data_path = tmp_path / "test_data"
    images_path = test_data_path / "images"
    annotations_path = test_data_path / "ground-truth"

    images_path.mkdir(parents=True)
    annotations_path.mkdir(parents=True)

    for image_number in (10, 2):
        (images_path / f"IMG_{image_number}.jpg").write_bytes(b"test-image")

        (annotations_path / f"GT_IMG_{image_number}.mat").write_bytes(
            b"test-annotation"
        )

    ground_truth_counts = {
        "GT_IMG_2.mat": 20,
        "GT_IMG_10.mat": 100,
    }

    def fake_load_ground_truth(
        annotation_path: Path,
    ) -> CrowdGroundTruth:
        count = ground_truth_counts[annotation_path.name]

        return CrowdGroundTruth(person_points=[(0.0, 0.0) for _ in range(count)])

    monkeypatch.setattr(
        shanghaitech_dataset,
        "load_shanghaitech_ground_truth",
        fake_load_ground_truth,
    )

    samples = shanghaitech_dataset.discover_shanghaitech_samples(test_data_path)

    assert [sample.sample_id for sample in samples] == [
        "shanghaitech-a-2",
        "shanghaitech-a-10",
    ]

    assert [sample.ground_truth_count for sample in samples] == [20, 100]

    assert all(sample.scenario == "CROWDED" for sample in samples)
