from pathlib import Path

import pytest

from app.services.evaluation_sample_selector import (
    classify_label_scenario,
    classify_person_count,
    count_people_in_yolo_label,
    read_person_centers_from_yolo_label,
    select_representative_labels,
)


def test_count_people_in_yolo_label_counts_only_person(
    tmp_path: Path,
) -> None:
    label_path = tmp_path / "sample.txt"
    label_path.write_text(
        ("0 0.25 0.50 0.10 0.20\n5 0.50 0.50 0.20 0.20\n0 0.75 0.50 0.10 0.20\n"),
        encoding="utf-8",
    )

    person_count = count_people_in_yolo_label(
        label_path,
    )

    assert person_count == 2


@pytest.mark.parametrize(
    ("person_count", "expected_scenario"),
    [
        (0, "EMPTY"),
        (1, "SPARSE"),
        (5, "SPARSE"),
        (6, "NORMAL"),
        (15, "NORMAL"),
        (16, "CROWDED"),
    ],
)
def test_classify_person_count_returns_scenario(
    person_count: int,
    expected_scenario: str,
) -> None:
    assert classify_person_count(person_count) == expected_scenario


def test_select_representative_labels_limits_each_scenario(
    tmp_path: Path,
) -> None:
    label_counts = {
        "a-empty": 0,
        "b-sparse": 1,
        "c-sparse": 2,
        "d-normal": 6,
        "e-crowded": 16,
    }

    for sample_id, person_count in label_counts.items():
        label_path = tmp_path / f"{sample_id}.txt"
        label_path.write_text(
            "0 0.5 0.5 0.1 0.2\n" * person_count,
            encoding="utf-8",
        )

    candidates = select_representative_labels(
        label_directory=tmp_path,
        limit_per_scenario=1,
    )

    assert [
        (
            candidate.sample_id,
            candidate.scenario,
            candidate.ground_truth_count,
        )
        for candidate in candidates
    ] == [
        ("a-empty", "EMPTY", 0),
        ("b-sparse", "SPARSE", 1),
        ("d-normal", "NORMAL", 6),
        ("e-crowded", "CROWDED", 16),
    ]


def test_read_person_centers_from_yolo_label(
    tmp_path: Path,
) -> None:
    label_path = tmp_path / "sample.txt"
    label_path.write_text(
        ("0 0.25 0.40 0.10 0.20\n5 0.50 0.50 0.20 0.20\n0 0.75 0.60 0.10 0.20\n"),
        encoding="utf-8",
    )

    centers = read_person_centers_from_yolo_label(
        label_path,
    )

    assert centers == [
        (0.25, 0.4),
        (0.75, 0.6),
    ]


def test_classify_label_scenario_detects_local_density(
    tmp_path: Path,
) -> None:
    label_path = tmp_path / "local-dense.txt"
    label_path.write_text(
        (
            "0 0.10 0.10 0.05 0.10\n"
            "0 0.10 0.10 0.05 0.10\n"
            "0 0.20 0.10 0.05 0.10\n"
            "0 0.20 0.10 0.05 0.10\n"
            "0 0.10 0.20 0.05 0.10\n"
            "0 0.10 0.20 0.05 0.10\n"
        ),
        encoding="utf-8",
    )

    scenario = classify_label_scenario(
        label_path,
    )

    assert scenario == "LOCAL_DENSE"
