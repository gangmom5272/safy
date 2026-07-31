import pytest

from app.services.escalation_simulation import (
    STRATEGY_ASYMMETRIC_GATED,
    STRATEGY_ASYMMETRIC_UNGATED,
    STRATEGY_YOLO_ONLY,
    FrameObservation,
    evaluate_strategy,
    find_new_false_alarms,
    find_newly_caught_samples,
    is_actually_dense,
    outcome_to_dict,
    should_escalate,
)


def build_observation(
    *,
    sample_id: str = "sample-1",
    scenario: str = "CROWDED",
    ground_truth_count: int = 100,
    yolo_count: int | None = 10,
    p2pnet_count: int | None = 95,
    low_confidence_signal: bool = False,
) -> FrameObservation:
    return FrameObservation(
        sample_id=sample_id,
        scenario=scenario,
        ground_truth_count=ground_truth_count,
        yolo_count=yolo_count,
        p2pnet_count=p2pnet_count,
        p2pnet_low_confidence_signal=low_confidence_signal,
    )


def test_is_actually_dense_uses_ground_truth_only() -> None:
    dense = build_observation(ground_truth_count=51)
    sparse = build_observation(ground_truth_count=50)

    assert is_actually_dense(dense) is True
    assert is_actually_dense(sparse) is False


def test_yolo_only_misses_undercounted_crowded_scene() -> None:
    # 실제 172명인데 YOLO는 13명으로 봄 (ShanghaiTech IMG_1 실제 사례)
    observation = build_observation(
        ground_truth_count=172,
        yolo_count=13,
        p2pnet_count=218,
    )

    assert should_escalate(observation, strategy=STRATEGY_YOLO_ONLY) is False


def test_gated_asymmetric_catches_undercounted_crowded_scene() -> None:
    observation = build_observation(
        ground_truth_count=172,
        yolo_count=13,
        p2pnet_count=218,
        low_confidence_signal=False,
    )

    assert should_escalate(observation, strategy=STRATEGY_ASYMMETRIC_GATED) is True


def test_gate_suppresses_p2pnet_signal_when_low_confidence() -> None:
    # 저밀도 대형 오탐: 실제로는 1명인데 P2PNet이 398명으로 봄
    observation = build_observation(
        scenario="SPARSE",
        ground_truth_count=1,
        yolo_count=1,
        p2pnet_count=398,
        low_confidence_signal=True,
    )

    assert should_escalate(observation, strategy=STRATEGY_ASYMMETRIC_UNGATED) is True
    assert should_escalate(observation, strategy=STRATEGY_ASYMMETRIC_GATED) is False


def test_yolo_signal_is_honored_even_when_gate_blocks_p2pnet() -> None:
    observation = build_observation(
        ground_truth_count=80,
        yolo_count=60,
        p2pnet_count=400,
        low_confidence_signal=True,
    )

    assert should_escalate(observation, strategy=STRATEGY_ASYMMETRIC_GATED) is True


def test_should_escalate_handles_missing_predictions() -> None:
    observation = build_observation(
        yolo_count=None,
        p2pnet_count=None,
    )

    for strategy in (
        STRATEGY_YOLO_ONLY,
        STRATEGY_ASYMMETRIC_GATED,
        STRATEGY_ASYMMETRIC_UNGATED,
    ):
        assert should_escalate(observation, strategy=strategy) is False


def test_should_escalate_rejects_unknown_strategy() -> None:
    with pytest.raises(ValueError, match="지원하지 않는 전략입니다"):
        should_escalate(build_observation(), strategy="MAGIC")


def test_evaluate_strategy_builds_confusion_matrix() -> None:
    observations = [
        # 실제 고밀도, YOLO는 놓침, P2PNet은 게이트 통과하며 잡음
        build_observation(
            sample_id="dense-caught",
            ground_truth_count=200,
            yolo_count=12,
            p2pnet_count=190,
            low_confidence_signal=False,
        ),
        # 실제 고밀도, 두 모델 모두 놓침
        build_observation(
            sample_id="dense-missed",
            ground_truth_count=90,
            yolo_count=8,
            p2pnet_count=20,
            low_confidence_signal=False,
        ),
        # 실제 저밀도, 아무도 escalate 안 함
        build_observation(
            sample_id="sparse-quiet",
            scenario="SPARSE",
            ground_truth_count=3,
            yolo_count=3,
            p2pnet_count=4,
            low_confidence_signal=False,
        ),
        # 실제 저밀도인데 P2PNet 대형 오탐 → 게이트가 막아줌
        build_observation(
            sample_id="sparse-gated",
            scenario="SPARSE",
            ground_truth_count=2,
            yolo_count=2,
            p2pnet_count=300,
            low_confidence_signal=True,
        ),
    ]

    gated = evaluate_strategy(
        observations,
        strategy=STRATEGY_ASYMMETRIC_GATED,
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
    )

    # 게이트가 없으면 저밀도 대형 오탐이 오경보로 넘어온다.
    assert ungated.false_positive == 1
    assert ungated.precision == 0.5

    yolo_only = evaluate_strategy(
        observations,
        strategy=STRATEGY_YOLO_ONLY,
    )

    assert yolo_only.true_positive == 0
    assert yolo_only.recall == 0.0


def test_evaluate_strategy_requires_observations() -> None:
    with pytest.raises(ValueError, match="시뮬레이션 표본이 한 건 이상 필요합니다"):
        evaluate_strategy([], strategy=STRATEGY_YOLO_ONLY)


def test_find_newly_caught_samples_lists_only_gate_gains() -> None:
    caught_by_gate = build_observation(
        sample_id="gain",
        ground_truth_count=200,
        yolo_count=10,
        p2pnet_count=180,
    )
    already_caught_by_yolo = build_observation(
        sample_id="no-gain",
        ground_truth_count=200,
        yolo_count=120,
        p2pnet_count=180,
    )

    results = find_newly_caught_samples([caught_by_gate, already_caught_by_yolo])

    assert [item.sample_id for item in results] == ["gain"]


def test_find_new_false_alarms_lists_only_low_density_escalations() -> None:
    new_alarm = build_observation(
        sample_id="alarm",
        scenario="LOCAL_DENSE",
        ground_truth_count=20,
        yolo_count=18,
        p2pnet_count=90,
        low_confidence_signal=False,
    )
    blocked_alarm = build_observation(
        sample_id="blocked",
        scenario="SPARSE",
        ground_truth_count=1,
        yolo_count=1,
        p2pnet_count=300,
        low_confidence_signal=True,
    )

    results = find_new_false_alarms([new_alarm, blocked_alarm])

    assert [item.sample_id for item in results] == ["alarm"]


def test_outcome_to_dict_exposes_camel_case_metrics() -> None:
    outcome = evaluate_strategy(
        [build_observation()],
        strategy=STRATEGY_ASYMMETRIC_GATED,
    )

    payload = outcome_to_dict(outcome)

    assert payload["strategy"] == STRATEGY_ASYMMETRIC_GATED
    assert payload["denseThreshold"] == 51
    assert "recall" in payload
    assert "falseAlarmRate" in payload
