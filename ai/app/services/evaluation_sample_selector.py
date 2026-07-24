from dataclasses import dataclass
from pathlib import Path

from app.services.congestion import (
    calculate_congestion,
)

PERSON_CLASS_ID = "0"
LOCAL_DENSE_THRESHOLD = 37.5


@dataclass(frozen=True)
class EvaluationCandidate:
    sample_id: str
    scenario: str
    ground_truth_count: int
    label_path: Path


def count_people_in_yolo_label(
    label_path: Path,
) -> int:
    lines = label_path.read_text(
        encoding="utf-8",
    ).splitlines()

    person_count = 0

    for line in lines:
        fields = line.split()

        if not fields:
            continue

        class_id = fields[0]

        if class_id == PERSON_CLASS_ID:
            person_count += 1

    return person_count


def classify_person_count(
    person_count: int,
) -> str:
    if person_count == 0:
        return "EMPTY"

    if person_count <= 5:
        return "SPARSE"

    if person_count <= 15:
        return "NORMAL"

    return "CROWDED"


def classify_label_scenario(
    label_path: Path,
) -> str:
    person_centers = read_person_centers_from_yolo_label(label_path)
    person_count = len(person_centers)
    count_scenario = classify_person_count(person_count)

    if count_scenario in {
        "EMPTY",
        "CROWDED",
    }:
        return count_scenario

    congestion = calculate_congestion(person_centers)

    if congestion.local_peak_congestion_score >= LOCAL_DENSE_THRESHOLD:
        return "LOCAL_DENSE"

    return count_scenario


def select_representative_labels(
    label_directory: Path,
    limit_per_scenario: int,
) -> list[EvaluationCandidate]:
    candidates: list[EvaluationCandidate] = []
    scenario_counts: dict[str, int] = {}

    for label_path in sorted(label_directory.glob("*.txt")):
        ground_truth_count = count_people_in_yolo_label(label_path)
        scenario = classify_label_scenario(label_path)
        selected_count = scenario_counts.get(
            scenario,
            0,
        )

        if selected_count >= limit_per_scenario:
            continue

        candidates.append(
            EvaluationCandidate(
                sample_id=label_path.stem,
                scenario=scenario,
                ground_truth_count=(ground_truth_count),
                label_path=label_path,
            )
        )
        scenario_counts[scenario] = selected_count + 1

    return candidates


def read_person_centers_from_yolo_label(
    label_path: Path,
) -> list[tuple[float, float]]:
    lines = label_path.read_text(
        encoding="utf-8",
    ).splitlines()

    person_centers: list[tuple[float, float]] = []

    for line in lines:
        fields = line.split()

        if not fields:
            continue

        class_id = fields[0]

        if class_id != PERSON_CLASS_ID:
            continue

        center_x = float(fields[1])
        center_y = float(fields[2])

        person_centers.append((center_x, center_y))

    return person_centers
