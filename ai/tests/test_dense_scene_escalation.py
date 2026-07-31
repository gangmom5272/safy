from app.services.dense_scene_escalation import (
    CONGESTION_CORRECTION_ENABLED_VARIABLE,
    CONGESTION_SOURCE_CORRECTED,
    CONGESTION_SOURCE_OFFICIAL,
    DENSE_SCENE_LOCAL_PEAK_THRESHOLD,
    correct_congestion_scores,
    determine_dense_scene_assessment,
    is_congestion_correction_enabled,
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


def build_correction(**overrides):
    """기본값은 고밀도 장면에서 보정이 일어나는 조건이다."""
    arguments = {
        "official_visual_congestion_score": 27.2,
        "official_local_peak_congestion_score": 45.0,
        "shadow_analysis_status": "COMPLETED",
        "shadow_visual_congestion_score": 82.3,
        "shadow_local_peak_congestion_score": 100.0,
        "shadow_low_confidence_signal": False,
    }
    arguments.update(overrides)

    return correct_congestion_scores(**arguments)


def test_correction_is_disabled_by_default(monkeypatch) -> None:
    monkeypatch.delenv(
        CONGESTION_CORRECTION_ENABLED_VARIABLE,
        raising=False,
    )

    assert is_congestion_correction_enabled() is False


def test_correction_flag_is_read_from_environment(monkeypatch) -> None:
    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "true")

    assert is_congestion_correction_enabled() is True

    monkeypatch.setenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "off")

    assert is_congestion_correction_enabled() is False


def test_correction_raises_scores_in_dense_scene() -> None:
    # 0086.jpg 사례: 실제 172명, YOLO L=45 / P2PNet L=100
    correction = build_correction()

    assert correction.visual_congestion_score == 82.3
    assert correction.local_peak_congestion_score == 100.0
    assert correction.source == CONGESTION_SOURCE_CORRECTED
    assert correction.applied is True


def test_correction_never_lowers_scores() -> None:
    """그림자 값이 더 낮으면 공식 값을 유지해야 한다."""
    correction = build_correction(
        official_visual_congestion_score=60.0,
        official_local_peak_congestion_score=90.0,
        shadow_visual_congestion_score=5.0,
        shadow_local_peak_congestion_score=10.0,
    )

    assert correction.visual_congestion_score == 60.0
    assert correction.local_peak_congestion_score == 90.0
    assert correction.source == CONGESTION_SOURCE_OFFICIAL
    assert correction.applied is False


def test_gate_blocks_correction_for_large_false_positive() -> None:
    """색종이 사례: 실제 0명인데 P2PNet L=100. 자동 등급을 움직여선 안 된다."""
    correction = build_correction(
        official_visual_congestion_score=0.0,
        official_local_peak_congestion_score=0.0,
        shadow_visual_congestion_score=15.1,
        shadow_local_peak_congestion_score=100.0,
        shadow_low_confidence_signal=True,
    )

    assert correction.visual_congestion_score == 0.0
    assert correction.local_peak_congestion_score == 0.0
    assert correction.source == CONGESTION_SOURCE_OFFICIAL


def test_failed_shadow_analysis_does_not_correct() -> None:
    correction = build_correction(
        shadow_analysis_status="FAILED",
        shadow_visual_congestion_score=None,
        shadow_local_peak_congestion_score=None,
        shadow_low_confidence_signal=None,
    )

    assert correction.visual_congestion_score == 27.2
    assert correction.local_peak_congestion_score == 45.0
    assert correction.source == CONGESTION_SOURCE_OFFICIAL


def test_correction_applies_per_field() -> None:
    """C는 공식값이 더 크고 L은 그림자가 더 큰 경우."""
    correction = build_correction(
        official_visual_congestion_score=70.0,
        official_local_peak_congestion_score=40.0,
        shadow_visual_congestion_score=30.0,
        shadow_local_peak_congestion_score=100.0,
    )

    assert correction.visual_congestion_score == 70.0
    assert correction.local_peak_congestion_score == 100.0
    assert correction.source == CONGESTION_SOURCE_CORRECTED


def test_correction_handles_missing_official_scores() -> None:
    correction = build_correction(
        official_visual_congestion_score=None,
        official_local_peak_congestion_score=None,
    )

    assert correction.visual_congestion_score == 82.3
    assert correction.local_peak_congestion_score == 100.0
    assert correction.source == CONGESTION_SOURCE_CORRECTED
