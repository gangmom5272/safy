from dataclasses import dataclass

from app.services.dense_scene_escalation import (
    DENSE_SCENE_PERSON_THRESHOLD,
)


# 런타임 판정과 시뮬레이션이 서로 어긋나지 않도록 임계값 출처를 하나로 둔다.
DEFAULT_DENSE_THRESHOLD = DENSE_SCENE_PERSON_THRESHOLD

STRATEGY_YOLO_ONLY = "YOLO_ONLY"
STRATEGY_ASYMMETRIC_GATED = "ASYMMETRIC_GATED"
STRATEGY_ASYMMETRIC_UNGATED = "ASYMMETRIC_UNGATED"

ALL_STRATEGIES = (
    STRATEGY_YOLO_ONLY,
    STRATEGY_ASYMMETRIC_GATED,
    STRATEGY_ASYMMETRIC_UNGATED,
)


@dataclass(frozen=True)
class FrameObservation:
    sample_id: str
    scenario: str
    ground_truth_count: int
    yolo_count: int | None
    p2pnet_count: int | None
    p2pnet_low_confidence_signal: bool


@dataclass(frozen=True)
class StrategyOutcome:
    strategy: str
    dense_threshold: int
    sample_count: int
    actually_dense_count: int
    escalated_count: int
    true_positive: int
    false_positive: int
    false_negative: int
    true_negative: int

    @property
    def recall(self) -> float:
        positives = self.true_positive + self.false_negative

        if positives == 0:
            return 0.0

        return round(self.true_positive / positives, 4)

    @property
    def precision(self) -> float:
        predicted_positives = self.true_positive + self.false_positive

        if predicted_positives == 0:
            return 0.0

        return round(self.true_positive / predicted_positives, 4)

    @property
    def false_alarm_rate(self) -> float:
        negatives = self.false_positive + self.true_negative

        if negatives == 0:
            return 0.0

        return round(self.false_positive / negatives, 4)


def is_actually_dense(
    observation: FrameObservation,
    *,
    dense_threshold: int = DEFAULT_DENSE_THRESHOLD,
) -> bool:
    return observation.ground_truth_count >= dense_threshold


def should_escalate(
    observation: FrameObservation,
    *,
    strategy: str,
    dense_threshold: int = DEFAULT_DENSE_THRESHOLD,
) -> bool:
    if strategy not in ALL_STRATEGIES:
        raise ValueError(f"지원하지 않는 전략입니다: {strategy}")

    yolo_says_dense = (
        observation.yolo_count is not None and observation.yolo_count >= dense_threshold
    )

    if strategy == STRATEGY_YOLO_ONLY:
        return yolo_says_dense

    p2pnet_says_dense = (
        observation.p2pnet_count is not None
        and observation.p2pnet_count >= dense_threshold
    )

    if strategy == STRATEGY_ASYMMETRIC_UNGATED:
        return yolo_says_dense or p2pnet_says_dense

    # ASYMMETRIC_GATED: P2PNet 신호는 140번 게이트를 통과한 경우에만 인정한다.
    gate_passed = not observation.p2pnet_low_confidence_signal

    return yolo_says_dense or (p2pnet_says_dense and gate_passed)


def evaluate_strategy(
    observations: list[FrameObservation],
    *,
    strategy: str,
    dense_threshold: int = DEFAULT_DENSE_THRESHOLD,
) -> StrategyOutcome:
    if not observations:
        raise ValueError("시뮬레이션 표본이 한 건 이상 필요합니다.")

    true_positive = 0
    false_positive = 0
    false_negative = 0
    true_negative = 0

    for observation in observations:
        actually_dense = is_actually_dense(
            observation,
            dense_threshold=dense_threshold,
        )
        escalated = should_escalate(
            observation,
            strategy=strategy,
            dense_threshold=dense_threshold,
        )

        if actually_dense and escalated:
            true_positive += 1
        elif actually_dense and not escalated:
            false_negative += 1
        elif not actually_dense and escalated:
            false_positive += 1
        else:
            true_negative += 1

    return StrategyOutcome(
        strategy=strategy,
        dense_threshold=dense_threshold,
        sample_count=len(observations),
        actually_dense_count=(true_positive + false_negative),
        escalated_count=(true_positive + false_positive),
        true_positive=true_positive,
        false_positive=false_positive,
        false_negative=false_negative,
        true_negative=true_negative,
    )


def find_newly_caught_samples(
    observations: list[FrameObservation],
    *,
    dense_threshold: int = DEFAULT_DENSE_THRESHOLD,
) -> list[FrameObservation]:
    """YOLO 단독으로는 놓쳤지만 게이트 적용 비대칭 규칙이 잡아낸 고밀도 표본."""
    caught = []

    for observation in observations:
        if not is_actually_dense(observation, dense_threshold=dense_threshold):
            continue

        yolo_escalated = should_escalate(
            observation,
            strategy=STRATEGY_YOLO_ONLY,
            dense_threshold=dense_threshold,
        )
        gated_escalated = should_escalate(
            observation,
            strategy=STRATEGY_ASYMMETRIC_GATED,
            dense_threshold=dense_threshold,
        )

        if gated_escalated and not yolo_escalated:
            caught.append(observation)

    return caught


def find_new_false_alarms(
    observations: list[FrameObservation],
    *,
    dense_threshold: int = DEFAULT_DENSE_THRESHOLD,
) -> list[FrameObservation]:
    """YOLO 단독에서는 없었으나 게이트 적용 비대칭 규칙이 새로 만든 오경보."""
    alarms = []

    for observation in observations:
        if is_actually_dense(observation, dense_threshold=dense_threshold):
            continue

        yolo_escalated = should_escalate(
            observation,
            strategy=STRATEGY_YOLO_ONLY,
            dense_threshold=dense_threshold,
        )
        gated_escalated = should_escalate(
            observation,
            strategy=STRATEGY_ASYMMETRIC_GATED,
            dense_threshold=dense_threshold,
        )

        if gated_escalated and not yolo_escalated:
            alarms.append(observation)

    return alarms


def outcome_to_dict(
    outcome: StrategyOutcome,
) -> dict:
    return {
        "strategy": outcome.strategy,
        "denseThreshold": outcome.dense_threshold,
        "sampleCount": outcome.sample_count,
        "actuallyDenseCount": outcome.actually_dense_count,
        "escalatedCount": outcome.escalated_count,
        "truePositive": outcome.true_positive,
        "falsePositive": outcome.false_positive,
        "falseNegative": outcome.false_negative,
        "trueNegative": outcome.true_negative,
        "recall": outcome.recall,
        "precision": outcome.precision,
        "falseAlarmRate": outcome.false_alarm_rate,
    }
