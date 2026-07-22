from dataclasses import dataclass


GRID_SIZE = 8
LOCAL_WINDOW_SIZE = 2
CELL_SCORE_PER_PERSON = 30.0
MAX_CELL_SCORE = 100.0


@dataclass(frozen=True)
class CongestionResult:
    visual_congestion_score: float
    local_peak_congestion_score: float


def calculate_congestion(
    person_centers: list[tuple[float, float]],
) -> CongestionResult:
    cell_counts = [
        [0 for _ in range(GRID_SIZE)]
        for _ in range(GRID_SIZE)
    ]

    for center_x, center_y in person_centers:
        column = min(int(center_x * GRID_SIZE), GRID_SIZE - 1)
        row = min(int(center_y * GRID_SIZE), GRID_SIZE - 1)
        cell_counts[row][column] += 1

    cell_scores = [
        [
            min(count * CELL_SCORE_PER_PERSON, MAX_CELL_SCORE)
            for count in row
        ]
        for row in cell_counts
    ]

    visual_congestion_score = sum(
        sum(row) for row in cell_scores
    ) / (GRID_SIZE * GRID_SIZE)

    local_peak_congestion_score = max(
        sum(
            cell_scores[row + row_offset][column + column_offset]
            for row_offset in range(LOCAL_WINDOW_SIZE)
            for column_offset in range(LOCAL_WINDOW_SIZE)
        ) / (LOCAL_WINDOW_SIZE * LOCAL_WINDOW_SIZE)
        for row in range(GRID_SIZE - LOCAL_WINDOW_SIZE + 1)
        for column in range(GRID_SIZE - LOCAL_WINDOW_SIZE + 1)
    )

    return CongestionResult(
        visual_congestion_score=round(visual_congestion_score, 2),
        local_peak_congestion_score=round(local_peak_congestion_score, 2),
    )