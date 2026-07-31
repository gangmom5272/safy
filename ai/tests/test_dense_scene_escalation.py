from app.services.dense_scene_escalation import (
    DENSE_SCENE_PERSON_THRESHOLD,
    determine_dense_scene_assessment,
)


def test_threshold_matches_project_crowded_boundary() -> None:
    assert DENSE_SCENE_PERSON_THRESHOLD == 51


def test_shadow_alone_can_trigger_dense_suspicion() -> None:
    # YOLO가 고밀도를 크게 놓친 경우(실제 172명 → YOLO 13명)
    assessment = determine_dense_scene_assessment(
        official_person_count=13,
        shadow_person_count=218,
    )

    assert assessment.suspected is True
    assert assessment.official_says_dense is False
    assert assessment.shadow_says_dense is True


def test_official_alone_can_trigger_dense_suspicion() -> None:
    assessment = determine_dense_scene_assessment(
        official_person_count=80,
        shadow_person_count=10,
    )

    assert assessment.suspected is True
    assert assessment.official_says_dense is True
    assert assessment.shadow_says_dense is False


def test_low_density_frame_is_not_suspected() -> None:
    assessment = determine_dense_scene_assessment(
        official_person_count=4,
        shadow_person_count=9,
    )

    assert assessment.suspected is False


def test_threshold_boundary_is_inclusive() -> None:
    at_threshold = determine_dense_scene_assessment(
        official_person_count=None,
        shadow_person_count=51,
    )
    below_threshold = determine_dense_scene_assessment(
        official_person_count=None,
        shadow_person_count=50,
    )

    assert at_threshold.suspected is True
    assert below_threshold.suspected is False


def test_missing_counts_do_not_trigger_suspicion() -> None:
    assessment = determine_dense_scene_assessment(
        official_person_count=None,
        shadow_person_count=None,
    )

    assert assessment.suspected is False
    assert assessment.official_says_dense is False
    assert assessment.shadow_says_dense is False


def test_custom_threshold_is_honored() -> None:
    assessment = determine_dense_scene_assessment(
        official_person_count=None,
        shadow_person_count=30,
        person_threshold=25,
    )

    assert assessment.suspected is True
    assert assessment.person_threshold == 25
