from dataclasses import dataclass
from pathlib import Path

import numpy as np
from scipy.io import loadmat


@dataclass(frozen=True)
class CrowdGroundTruth:
    person_points: list[tuple[float, float]]

    @property
    def person_count(self) -> int:
        return len(self.person_points)


def load_shanghaitech_ground_truth(
    annotation_path: Path,
) -> CrowdGroundTruth:
    annotation_data = loadmat(annotation_path)

    if "image_info" not in annotation_data:
        raise ValueError("ShanghaiTech 정답에 image_info가 없습니다.")

    try:
        raw_points = annotation_data["image_info"][0, 0][0, 0][0]
    except (IndexError, TypeError, ValueError) as error:
        raise ValueError("ShanghaiTech 정답 형식이 올바르지 않습니다.") from error

    points_array = np.asarray(
        raw_points,
        dtype=np.float64,
    )

    if points_array.ndim != 2 or points_array.shape[1] != 2:
        raise ValueError("사람 위치 좌표는 (N, 2) 형식이어야 합니다.")

    person_points = [
        (float(x_coordinate), float(y_coordinate))
        for x_coordinate, y_coordinate in points_array
    ]

    return CrowdGroundTruth(person_points=person_points)
