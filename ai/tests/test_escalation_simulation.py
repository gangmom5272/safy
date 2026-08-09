import pytest

from app.services.escalation_simulation import (
    STRATEGY_ASYMMETRIC_GATED,
    STRATEGY_ASYMMETRIC_UNGATED,
    STRATEGY_OFFICIAL_ONLY,
    FrameObservation,
    evaluate_strategy,
    find_new_false_alarms,
    find_newly_caught_samples,
    is_actually_dense,
    outcome_to_dict,
    should_escalate,
)


COUNT_THRESHOLD = 51.0
LOCAL_PEAK_THRESHOLD = 80.0


def build_observation(
    *,
    sample_id: str = "sample-1",
    scenario: str = "CROWDED",
    ground_truth_value: float = 100.0,
    official_value: float | None = 10.0,
    shadow_value: float | None = 95.0,
    low_confidence_signal: bool = False,
) -> FrameObservation:
    return FrameObservation(
        sample_id=sample_id,
        scenario=scenario,
        ground_truth_value=ground_truth_value,
        official_value=official_value,
        shadow_value=shadow_value,
        shadow_low_confidence_signal=low_confidence_signal,
    )


def test_is_actually_dense_uses_ground_truth_only() -> None:
    dense = build_observation(ground_truth_value=51)
    sparse = build_observation(ground_truth_value=50)

    assert is_actually_dense(dense, threshold=COUNT_THRESHOLD) is True
    assert is_actually_dense(sparse, threshold=COUNT_THRESHOLD) is False


def test_official_only_misses_undercounted_crowded_scene() -> None:
    # 광화문 집회 사례: 실제 L=100인데 YOLO L=7.5, P2PNet L=100
    observation = build_observation(
        ground_truth_value=100.0,
        official_value=7.5,
        shadow_value=100.0,
    )

    assert (
        should_escalate(
            observation,
            strategy=STRATEGY_OFFICIAL_ONLY,
            threshold=LOCAL_PEAK_THRESHOLD,
        )
        is False
    )


def test_asymmetric_catches_what_official_model_misses() -> None:
    observation = build_observation(
        ground_truth_value=100.0,
        official_value=7.5,
        shadow_value=100.0,
        low_confidence_signal=False,
    )

    assert (
        should_escalate(
            observation,
            strategy=STRATEGY_ASYMMETRIC_GATED,
            threshold=LOCAL_PEAK_THRESHOLD,
        )
        is True
    )


def test_gate_suppresses_shadow_signal_when_low_confidence() -> None:
    # 색종이 오탐 사례: 실제 L=0인데 P2PNet L=100
    observation = build_observation(
        scenario="EMPTY",
        ground_truth_value=0.0,
        official_value=0.0,
        shadow_value=100.0,
        low_confidence_signal=True,
    )

    assert (
        should_escalate(
            observation,
            strategy=STRATEGY_ASYMMETRIC_UNGATED,
            threshold=LOCAL_PEAK_THRESHOLD,
        )
        is True
    )
    assert (
        should_escalate(
            observation,
            strategy=STRATEGY_ASYMMETRIC_GATED,
            threshold=LOCAL_PEAK_THRESHOLD,
        )
        is False
    )


def test_official_signal_is_honored_even_when_gate_blocks_shadow() -> None:
    observation = build_observation(
        ground_truth_value=100.0,
        official_value=90.0,
        shadow_value=100.0,
        low_confidence_signal=True,
    )

    assert (
        should_escalate(
            observation,
            strategy=STRATEGY_ASYMMETRIC_GATED,
            threshold=LOCAL_PEAK_THRESHOLD,
        )
        is True
    )


def test_should_escalate_handles_missing_values() -> None:
    observation = build_observation(
        official_value=None,
        shadow_value=None,
    )

    for strategy in (
        STRATEGY_OFFICIAL_ONLY,
        STRATEGY_ASYMMETRIC_GATED,
        STRATEGY_ASYMMETRIC_UNGATED,
    ):
        assert (
            should_escalate(
                observation,
                strategy=strategy,
                threshold=LOCAL_PEAK_THRESHOLD,
            )
            is False
        )


def test_should_escalate_rejects_unknown_strategy() -> None:
    with pytest.raises(ValueError, match="지원하지 않는 전략입니다"):
        should_escalate(
            build_observation(),
            strategy="MAGIC",
            threshold=LOCAL_PEAK_THRESHOLD,
        )


def test_evaluate_strategy_builds_confusion_matrix() -> None:
    observations = [
        # 실제 고밀도, 공식 모델은 놓침, 그림자는 게이트 통과하며 잡음
        build_observation(
            sample_id="dense-caught",
            ground_truth_value=100.0,
            official_value=12.0,
            shadow_value=100.0,
            low_confidence_signal=False,
        ),
        # 실제 고밀도, 두 모델 모두 놓침
        build_observation(
            sample_id="dense-missed",
            ground_truth_value=90.0,
            official_value=8.0,
            shadow_value=20.0,
            low_confidence_signal=False,
        ),
        # 실제 저밀도, 아무도 escalate 안 함
        build_observation(
            sample_id="sparse-quiet",
            scenario="SPARSE",
            ground_truth_value=15.0,
            official_value=12.0,
            shadow_value=20.0,
            low_confidence_signal=False,
        ),
        # 실제 저밀도인데 그림자 대형 오탐 → 게이트가 막아줌
        build_observation(
            sample_id="sparse-gated",
            scenario="EMPTY",
            ground_truth_value=0.0,
            official_value=0.0,
            shadow_value=100.0,
            low_confidence_signal=True,
        ),
    ]

    gated = evaluate_strategy(
        observations,
        strategy=STRATEGY_ASYMMETRIC_GATED,
        threshold=LOCAL_PEAK_THRESHOLD,
    )

    assert gated.sample_count == 4
    assert gated.actually_dense_count == 2
    assert gated.true_positive == 1
    assert gated.false_negative == 1
    assert gated.false_positive == 0
    assert gated.true_negative == 2
    assert gated.recall == 0.5
    assert gated.precision == 1.0
    assert gated.false_alarm_rate == 0.0

    ungated = evaluate_strategy(
        observations,
        strategy=STRATEGY_ASYMMETRIC_UNGATED,
        threshold=LOCAL_PEAK_THRESHOLD,
    )

    # 게이트가 없으면 저밀도 대형 오탐이 오경보로 넘어온다.
    assert ungated.false_positive == 1
    assert ungated.precision == 0.5

    official_only = evaluate_strategy(
        observations,
        strategy=STRATEGY_OFFICIAL_ONLY,
        threshold=LOCAL_PEAK_THRESHOLD,
    )

    assert official_only.true_positive == 0
    assert official_only.recall == 0.0


def test_evaluate_strategy_requires_observations() -> None:
    with pytest.raises(ValueError, match="시뮬레이션 표본이 한 건 이상 필요합니다"):
        evaluate_strategy(
            [],
            strategy=STRATEGY_OFFICIAL_ONLY,
            threshold=LOCAL_PEAK_THRESHOLD,
        )


def test_same_module_works_for_both_signal_types() -> None:
    """인원수와 L 어느 신호로도 같은 코드가 동작해야 한다."""
    count_observation = build_observation(
        ground_truth_value=172,
        official_value=13,
        shadow_value=218,
    )
    local_peak_observation = build_observation(
        ground_truth_value=100.0,
        official_value=45.0,
        shadow_value=100.0,
    )

    count_outcome = evaluate_strategy(
        [count_observation],
        strategy=STRATEGY_ASYMMETRIC_GATED,
        threshold=COUNT_THRESHOLD,
        signal_name="personCount",
    )
    local_peak_outcome = evaluate_strategy(
        [local_peak_observation],
        strategy=STRATEGY_ASYMMETRIC_GATED,
        threshold=LOCAL_PEAK_THRESHOLD,
        signal_name="localPeakCongestionScore",
    )

    assert count_outcome.true_positive == 1
    assert count_outcome.signal_name == "personCount"
    assert local_peak_outcome.true_positive == 1
    assert local_peak_outcome.signal_name == "localPeakCongestionScore"


def test_ground_truth_threshold_is_independent_of_prediction_threshold() -> None:
    """정답 신호와 예측 신호가 다른 종류면 임계값도 달라야 한다.

    정답은 L(0~100), 예측은 인원수처럼 스케일이 다른 값을 비교할 때
    임계값을 하나만 쓰면 정답 집합 자체가 왜곡된다.
    """
    # 정답 L=60 → L>=80 기준으로는 저밀도, L>=51 기준으로는 고밀도
    observation = build_observation(
        ground_truth_value=60.0,
        official_value=10.0,
        shadow_value=100.0,
    )

    with_separate_thresholds = evaluate_strategy(
        [observation],
        strategy=STRATEGY_ASYMMETRIC_UNGATED,
        threshold=51.0,
        ground_truth_threshold=80.0,
    )
    with_shared_threshold = evaluate_strategy(
        [observation],
        strategy=STRATEGY_ASYMMETRIC_UNGATED,
        threshold=51.0,
    )

    # 정답 임계값 80 기준: 저밀도인데 상향했으므로 오경보
    assert with_separate_thresholds.actually_dense_count == 0
    assert with_separate_thresholds.false_positive == 1

    # 임계값을 공유하면 같은 표본이 고밀도로 잡혀 결과가 달라진다
    assert with_shared_threshold.actually_dense_count == 1
    assert with_shared_threshold.true_positive == 1


def test_find_newly_caught_samples_lists_only_gate_gains() -> None:
    caught_by_shadow = build_observation(
        sample_id="gain",
        ground_truth_value=100.0,
        official_value=10.0,
        shadow_value=100.0,
    )
    already_caught_by_official = build_observation(
        sample_id="no-gain",
        ground_truth_value=100.0,
        official_value=95.0,
        shadow_value=100.0,
    )

    results = find_newly_caught_samples(
        [caught_by_shadow, already_caught_by_official],
        threshold=LOCAL_PEAK_THRESHOLD,
    )

    assert [item.sample_id for item in results] == ["gain"]


def test_find_new_false_alarms_lists_only_low_density_escalations() -> None:
    new_alarm = build_observation(
        sample_id="alarm",
        scenario="LOCAL_DENSE",
        ground_truth_value=75.0,
        official_value=37.5,
        shadow_value=100.0,
        low_confidence_signal=False,
    )
    blocked_alarm = build_observation(
        sample_id="blocked",
        scenario="EMPTY",
        ground_truth_value=0.0,
        official_value=0.0,
        shadow_value=100.0,
        low_confidence_signal=True,
    )

    results = find_new_false_alarms(
        [new_alarm, blocked_alarm],
        threshold=LOCAL_PEAK_THRESHOLD,
    )

    assert [item.sample_id for item in results] == ["alarm"]


def test_outcome_to_dict_exposes_camel_case_metrics() -> None:
    outcome = evaluate_strategy(
        [build_observation()],
        strategy=STRATEGY_ASYMMETRIC_GATED,
        threshold=LOCAL_PEAK_THRESHOLD,
        signal_name="localPeakCongestionScore",
    )

    payload = outcome_to_dict(outcome)

    assert payload["strategy"] == STRATEGY_ASYMMETRIC_GATED
    assert payload["signalName"] == "localPeakCongestionScore"
    assert payload["threshold"] == 80.0
    assert "recall" in payload
    assert "falseAlarmRate" in payload
