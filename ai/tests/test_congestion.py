from app.services.congestion import calculate_congestion


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