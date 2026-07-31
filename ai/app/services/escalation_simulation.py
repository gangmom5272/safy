"""위험 상향 신호 전략 비교 시뮬레이션.

"어떤 신호로 고밀도를 판정해야 하는가"를 홀드아웃 표본에서 비교한다.
신호 종류(인원수, 국소 최대 혼잡도 L 등)에 무관하게 동작하도록 값과
임계값만 받는다. 덕분에 인원수 기반 분석과 L 기반 분석이 같은 코드로
재현된다.

`S15P11A503-140`에서 두 신호를 비교한 결과와 결론은
`docs/dense-scene-escalation.md`에 정리했다.
"""

from dataclasses import dataclass


STRATEGY_OFFICIAL_ONLY = "OFFICIAL_ONLY"
STRATEGY_ASYMMETRIC_GATED = "ASYMMETRIC_GATED"
STRATEGY_ASYMMETRIC_UNGATED = "ASYMMETRIC_UNGATED"

ALL_STRATEGIES = (
    STRATEGY_OFFICIAL_ONLY,
    STRATEGY_ASYMMETRIC_GATED,
    STRATEGY_ASYMMETRIC_UNGATED,
)


@dataclass(frozen=True)
class FrameObservation:
    """한 프레임의 정답 신호값과 두 모델의 예측 신호값.

    `ground_truth_value`, `official_value`, `shadow_value`는 같은 종류의
    신호여야 한다(예: 셋 다 인원수, 또는 셋 다 L).
    """

    sample_id: str
    scenario: str
    ground_truth_value: float
    official_value: float | None
    shadow_value: float | None
    shadow_low_confidence_signal: bool


@dataclass(frozen=True)
class StrategyOutcome:
    strategy: str
    signal_name: str
    threshold: float
    ground_truth_threshold: float
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
    threshold: float,
) -> bool:
    return observation.ground_truth_value >= threshold


def should_escalate(
    observation: FrameObservation,
    *,
    strategy: str,
    threshold: float,
) -> bool:
    if strategy not in ALL_STRATEGIES:
        raise ValueError(f"지원하지 않는 전략입니다: {strategy}")

    official_says_dense = (
        observation.official_value is not None
        and observation.official_value >= threshold
    )

    if strategy == STRATEGY_OFFICIAL_ONLY:
        return official_says_dense

    shadow_says_dense = (
        observation.shadow_value is not None and observation.shadow_value >= threshold
    )

    if strategy == STRATEGY_ASYMMETRIC_UNGATED:
        return official_says_dense or shadow_says_dense

    # ASYMMETRIC_GATED: 그림자 신호는 140번 게이트를 통과한 경우에만 인정한다.
    gate_passed = not observation.shadow_low_confidence_signal

    return official_says_dense or (shadow_says_dense and gate_passed)


def evaluate_strategy(
    observations: list[FrameObservation],
    *,
    strategy: str,
    threshold: float,
    ground_truth_threshold: float | None = None,
    signal_name: str = "unknown",
) -> StrategyOutcome:
    """전략을 평가한다.

    정답 신호와 예측 신호가 다른 종류일 수 있으므로(예: 정답은 L, 예측은
    인원수) 임계값을 따로 받는다. `ground_truth_threshold`를 생략하면
    `threshold`를 그대로 쓴다.
    """
    if not observations:
        raise ValueError("시뮬레이션 표본이 한 건 이상 필요합니다.")

    if ground_truth_threshold is None:
        ground_truth_threshold = threshold

    true_positive = 0
    false_positive = 0
    false_negative = 0
    true_negative = 0

    for observation in observations:
        actually_dense = is_actually_dense(
            observation,
            threshold=ground_truth_threshold,
        )
        escalated = should_escalate(
            observation,
            strategy=strategy,
            threshold=threshold,
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
        signal_name=signal_name,
        threshold=threshold,
        ground_truth_threshold=(ground_truth_threshold),
        sample_count=len(observations),
        actually_dense_count=(true_positive + false_negative),
        escalated_count=(true_positive + false_positive),
        true_positive=true_positive,
        false_positive=false_positive,
        false_negative=false_negative,
        true_negative=true_negative,
    )


def _difference_against_official(
    observations: list[FrameObservation],
    *,
    threshold: float,
    ground_truth_threshold: float | None = None,
    want_dense: bool,
) -> list[FrameObservation]:
    if ground_truth_threshold is None:
        ground_truth_threshold = threshold

    selected = []

    for observation in observations:
        is_dense = is_actually_dense(
            observation,
            threshold=ground_truth_threshold,
        )

        if is_dense is not want_dense:
            continue

        official_escalated = should_escalate(
            observation,
            strategy=STRATEGY_OFFICIAL_ONLY,
            threshold=threshold,
        )
        gated_escalated = should_escalate(
            observation,
            strategy=STRATEGY_ASYMMETRIC_GATED,
            threshold=threshold,
        )

        if gated_escalated and not official_escalated:
            selected.append(observation)

    return selected


def find_newly_caught_samples(
    observations: list[FrameObservation],
    *,
    threshold: float,
    ground_truth_threshold: float | None = None,
) -> list[FrameObservation]:
    """공식 모델 단독으로는 놓쳤지만 게이트 적용 비대칭 규칙이 잡아낸 표본."""
    return _difference_against_official(
        observations,
        threshold=threshold,
        ground_truth_threshold=(ground_truth_threshold),
        want_dense=True,
    )


def find_new_false_alarms(
    observations: list[FrameObservation],
    *,
    threshold: float,
    ground_truth_threshold: float | None = None,
) -> list[FrameObservation]:
    """공식 모델 단독에서는 없었으나 비대칭 규칙이 새로 만든 오경보."""
    return _difference_against_official(
        observations,
        threshold=threshold,
        ground_truth_threshold=(ground_truth_threshold),
        want_dense=False,
    )


def outcome_to_dict(
    outcome: StrategyOutcome,
) -> dict:
    return {
        "strategy": outcome.strategy,
        "signalName": outcome.signal_name,
        "threshold": outcome.threshold,
        "groundTruthThreshold": (outcome.ground_truth_threshold),
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
