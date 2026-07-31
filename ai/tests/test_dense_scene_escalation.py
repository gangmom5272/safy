from app.services.dense_scene_escalation import (
    DENSE_SCENE_LOCAL_PEAK_THRESHOLD,
    determine_dense_scene_assessment,
)


def test_threshold_matches_documented_risk_exception_rule() -> None:
    """위험등급 예외 규칙의 'L >= 80 → 최소 CAUTION'과 같은 경계선을 쓴다."""
    assert DENSE_SCENE_LOCAL_PEAK_THRESHOLD == 80.0


def test_shadow_alone_can_trigger_dense_suspicion() -> None:
    # 광화문 집회 사례: 실제 9,059명인데 YOLO L=7.5, P2PNet L=100
    assessment = determine_dense_scene_assessment(
        official_local_peak_congestion_score=7.5,
        shadow_local_peak_congestion_score=100.0,
    )

    assert assessment.suspected is True
    assert assessment.official_says_dense is False
    assert assessment.shadow_says_dense is True


def test_official_alone_can_trigger_dense_suspicion() -> None:
    assessment = determine_dense_scene_assessment(
        official_local_peak_congestion_score=95.0,
        shadow_local_peak_congestion_score=10.0,
    )

    assert assessment.suspected is True
    assert assessment.official_says_dense is True
    assert assessment.shadow_says_dense is False


def test_low_density_frame_is_not_suspected() -> None:
    assessment = determine_dense_scene_assessment(
        official_local_peak_congestion_score=22.5,
        shadow_local_peak_congestion_score=37.5,
    )

    assert assessment.suspected is False


def test_threshold_boundary_is_inclusive() -> None:
    at_threshold = determine_dense_scene_assessment(
        official_local_peak_congestion_score=None,
        shadow_local_peak_congestion_score=80.0,
    )
    below_threshold = determine_dense_scene_assessment(
        official_local_peak_congestion_score=None,
        shadow_local_peak_congestion_score=79.9,
    )

    assert at_threshold.suspected is True
    assert below_threshold.suspected is False


def test_missing_scores_do_not_trigger_suspicion() -> None:
    assessment = determine_dense_scene_assessment(
        official_local_peak_congestion_score=None,
        shadow_local_peak_congestion_score=None,
    )

    assert assessment.suspected is False
    assert assessment.official_says_dense is False
    assert assessment.shadow_says_dense is False


def test_custom_threshold_is_honored() -> None:
    assessment = determine_dense_scene_assessment(
        official_local_peak_congestion_score=None,
        shadow_local_peak_congestion_score=70.0,
        local_peak_threshold=60.0,
    )

    assert assessment.suspected is True
    assert assessment.local_peak_threshold == 60.0


def test_person_count_is_not_used_for_the_decision() -> None:
    """인원수는 면적 정보가 없어 판정에 쓰지 않는다.

    색종이 오탐 사례처럼 인원수가 많아도 L이 기준 미달이면 상향하지 않고,
    반대로 인원수가 적어도 L이 높으면 상향한다.
    """
    low_l_high_count = determine_dense_scene_assessment(
        official_local_peak_congestion_score=30.0,
        shadow_local_peak_congestion_score=45.0,
    )
    high_l_low_count = determine_dense_scene_assessment(
        official_local_peak_congestion_score=None,
        shadow_local_peak_congestion_score=100.0,
    )

    assert low_l_high_count.suspected is False
    assert high_l_low_count.suspected is True
