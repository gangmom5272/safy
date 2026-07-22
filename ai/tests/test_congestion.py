from app.services.congestion import (
    build_congestion_grid,
    calculate_congestion,
)


def test_no_people_returns_zero_scores() -> None:
    result = calculate_congestion([])

    assert result.visual_congestion_score == 0.0
    assert result.local_peak_congestion_score == 0.0


def test_one_person_in_each_cell_returns_thirty_scores() -> None:
    person_centers = [
        ((column + 0.5) / 8, (row + 0.5) / 8)
        for row in range(8)
        for column in range(8)
    ]

    result = calculate_congestion(person_centers)

    assert result.visual_congestion_score == 30.0
    assert result.local_peak_congestion_score == 30.0


def test_local_cluster_returns_high_local_peak_score() -> None:
    person_centers = [
        ((column + 0.5) / 8, (row + 0.5) / 8)
        for row in (2, 3)
        for column in (4, 5)
        for _ in range(3)
    ]

    result = calculate_congestion(person_centers)

    assert result.visual_congestion_score == 5.62
    assert result.local_peak_congestion_score == 90.0


def test_boundary_coordinates_and_cell_score_are_capped() -> None:
    person_centers = [(1.0, 1.0)] * 10

    result = calculate_congestion(person_centers)

    assert result.visual_congestion_score == 1.56
    assert result.local_peak_congestion_score == 25.0


def test_build_congestion_grid_returns_visualization_data() -> None:
    person_centers = [
        ((column + 0.5) / 8, (row + 0.5) / 8)
        for row in (2, 3)
        for column in (4, 5)
    ]

    grid = build_congestion_grid(person_centers)

    assert grid.cell_counts[2][4] == 1
    assert grid.cell_counts[2][5] == 1
    assert grid.cell_counts[3][4] == 1
    assert grid.cell_counts[3][5] == 1

    assert grid.cell_scores[2][4] == 30.0
    assert grid.peak_window_row == 2
    assert grid.peak_window_column == 4


def test_out_of_range_coordinates_are_clamped_to_edges() -> None:
    person_centers = [
        (-0.25, -0.25),
        (1.25, 1.25),
    ]

    grid = build_congestion_grid(person_centers)

    assert grid.cell_counts[0][0] == 1
    assert grid.cell_counts[7][7] == 1
    assert sum(
        sum(row) for row in grid.cell_counts
    ) == 2