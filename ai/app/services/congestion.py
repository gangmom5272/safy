from dataclasses import dataclass


GRID_SIZE = 8
LOCAL_WINDOW_SIZE = 2
CELL_SCORE_PER_PERSON = 30.0
MAX_CELL_SCORE = 100.0


@dataclass(frozen=True)
class CongestionResult:
    visual_congestion_score: float
    local_peak_congestion_score: float


@dataclass(frozen=True)
class CongestionGrid:
    cell_counts: list[list[int]]
    cell_scores: list[list[float]]
    peak_window_row: int
    peak_window_column: int
    peak_window_score: float


def build_congestion_grid(
    person_centers: list[tuple[float, float]],
) -> CongestionGrid:
    cell_counts = [
        [0 for _ in range(GRID_SIZE)]
        for _ in range(GRID_SIZE)
    ]

    for center_x, center_y in person_centers:
        bounded_center_x = min(
            max(center_x, 0.0),
            1.0,
        )
        bounded_center_y = min(
            max(center_y, 0.0),
            1.0,
        )

        column = min(
            int(bounded_center_x * GRID_SIZE),
            GRID_SIZE - 1,
        )
        row = min(
            int(bounded_center_y * GRID_SIZE),
            GRID_SIZE - 1,
        )

        cell_counts[row][column] += 1

    cell_scores = [
        [
            min(count * CELL_SCORE_PER_PERSON, MAX_CELL_SCORE)
            for count in row
        ]
        for row in cell_counts
    ]

    peak_window_score, peak_window_row, peak_window_column = max(
        (
            sum(
                cell_scores[row + row_offset][column + column_offset]
                for row_offset in range(LOCAL_WINDOW_SIZE)
                for column_offset in range(LOCAL_WINDOW_SIZE)
            ) / (LOCAL_WINDOW_SIZE * LOCAL_WINDOW_SIZE),
            row,
            column,
        )
        for row in range(GRID_SIZE - LOCAL_WINDOW_SIZE + 1)
        for column in range(GRID_SIZE - LOCAL_WINDOW_SIZE + 1)
    )

    return CongestionGrid(
        cell_counts=cell_counts,
        cell_scores=cell_scores,
        peak_window_row=peak_window_row,
        peak_window_column=peak_window_column,
        peak_window_score=round(peak_window_score, 2),
    )


def calculate_congestion(
    person_centers: list[tuple[float, float]],
) -> CongestionResult:
    grid = build_congestion_grid(person_centers)

    visual_congestion_score = sum(
        sum(row) for row in grid.cell_scores
    ) / (GRID_SIZE * GRID_SIZE)

    return CongestionResult(
        visual_congestion_score=round(visual_congestion_score, 2),
        local_peak_congestion_score=grid.peak_window_score,
    )