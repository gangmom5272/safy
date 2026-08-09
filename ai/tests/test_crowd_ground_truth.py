from pathlib import Path

import numpy as np
from scipy.io import savemat

from app.services.crowd_ground_truth import (
    load_shanghaitech_ground_truth,
)


def test_load_shanghaitech_ground_truth_returns_points(
    tmp_path: Path,
) -> None:
    annotation_path = tmp_path / "GT_IMG_1.mat"

    expected_points = np.array(
        [
            [10.5, 20.5],
            [30.0, 40.0],
        ],
        dtype=np.float64,
    )

    annotation = np.empty(
        (1, 1),
        dtype=[("location", object)],
    )
    annotation["location"][0, 0] = expected_points

    image_info = np.empty(
        (1, 1),
        dtype=object,
    )
    image_info[0, 0] = annotation

    savemat(
        annotation_path,
        {"image_info": image_info},
    )

    result = load_shanghaitech_ground_truth(annotation_path)

    assert result.person_count == 2
    assert result.person_points == [
        (10.5, 20.5),
        (30.0, 40.0),
    ]
