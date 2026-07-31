"""고밀도 장면 위험 상향(escalation) 신호.

이 모듈은 "이 프레임이 고밀도 장면으로 의심되는가"만 판정한다.
최종 위험도와 위험등급은 백엔드가 계산하며(`S15P11A503-102`), AI는 판단
근거가 되는 신호만 제공한다.

핵심 설계 원칙: SAFY는 기존 안전관리 프로세스를 대체하지 않고 보조한다.
최종 판단은 사람이 하므로, 위험 상향 신호는 관대하게 내보내고 확신도가
낮다는 사실은 별도 필드로 알린다. 확신도가 낮다는 이유로 위험 신호 자체를
막지 않는다.

판정 기준은 인원수가 아니라 국소 최대 혼잡도 L을 사용한다. 인원수는
면적 정보가 없어 위험도를 직접 결정하지 못하며(프로젝트 설계상 보조값),
138번 홀드아웃 실측에서도 L 기반이 인원수 기반보다 놓침을 절반 가까이
줄였다. 자세한 근거는 `docs/dense-scene-escalation.md`를 참고한다.
"""

import os
from dataclasses import dataclass


# 프로젝트 최종 위험등급 예외 규칙의 "L >= 80 → 최소 CAUTION" 기준을
# 그대로 사용한다. 신호가 먹이는 규칙과 같은 경계선을 쓰는 것이 의미가
# 분명하기 때문이다.
DENSE_SCENE_LOCAL_PEAK_THRESHOLD = 80.0

CONGESTION_CORRECTION_ENABLED_VARIABLE = "P2PNET_CONGESTION_CORRECTION_ENABLED"

CONGESTION_SOURCE_OFFICIAL = "YOLO"
CONGESTION_SOURCE_CORRECTED = "P2PNET_CORRECTED"

COMPLETED_STATUS = "COMPLETED"


@dataclass(frozen=True)
class DenseSceneAssessment:
    suspected: bool
    official_says_dense: bool
    shadow_says_dense: bool
    local_peak_threshold: float


def determine_dense_scene_assessment(
    *,
    official_local_peak_congestion_score: float | None,
    shadow_local_peak_congestion_score: float | None,
    local_peak_threshold: float = DENSE_SCENE_LOCAL_PEAK_THRESHOLD,
) -> DenseSceneAssessment:
    """두 모델 중 하나라도 L 기준을 넘으면 고밀도 의심으로 판정한다.

    YOLO는 고밀도에서 사람을 크게 놓쳐 L이 기준에 도달하지 못하고, P2PNet은
    질감이 복잡한 저밀도 장면에서 L을 과대 계산한다. 어느 한 모델의 L만으로는
    고밀도를 판정할 수 없으므로, 위험을 놓치지 않는 쪽을 우선해 OR 조건을
    사용한다.

    P2PNet의 저밀도 대형 오탐 의심 신호(`lowConfidenceSignal`)는 이 판정을
    막지 않는다. 그 신호는 신뢰도 정보로만 함께 기록한다.
    """
    official_says_dense = (
        official_local_peak_congestion_score is not None
        and official_local_peak_congestion_score >= local_peak_threshold
    )
    shadow_says_dense = (
        shadow_local_peak_congestion_score is not None
        and shadow_local_peak_congestion_score >= local_peak_threshold
    )

    return DenseSceneAssessment(
        suspected=(official_says_dense or shadow_says_dense),
        official_says_dense=official_says_dense,
        shadow_says_dense=shadow_says_dense,
        local_peak_threshold=local_peak_threshold,
    )


@dataclass(frozen=True)
class CongestionCorrection:
    """보정 후 C·L과 그 출처."""

    visual_congestion_score: float | None
    local_peak_congestion_score: float | None
    source: str

    @property
    def applied(self) -> bool:
        return self.source == CONGESTION_SOURCE_CORRECTED


def is_congestion_correction_enabled() -> bool:
    """공식 C·L 보정 활성화 여부. 기본값은 비활성화다.

    보정을 켜면 P2PNet이 공식 응답의 크리티컬 패스로 들어와 지연시간이
    크게 늘고(CPU 680~1,726ms) 동시 요청이 직렬화된다. 서버 실측
    (`S15P11A503-106`)으로 5초 프레임 예산을 확인한 뒤에만 켠다.
    """
    raw_value = os.getenv(CONGESTION_CORRECTION_ENABLED_VARIABLE, "false")

    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


def correct_congestion_scores(
    *,
    official_visual_congestion_score: float | None,
    official_local_peak_congestion_score: float | None,
    shadow_analysis_status: str | None,
    shadow_visual_congestion_score: float | None,
    shadow_local_peak_congestion_score: float | None,
    shadow_low_confidence_signal: bool | None,
) -> CongestionCorrection:
    """위험을 올리는 방향으로만 공식 C·L을 P2PNet 값으로 보정한다.

    고밀도 장면에서 YOLO의 C·L 오차가 매우 커(C 44.15, L 80.62) 위험등급
    예외 규칙이 발동하지 않는 문제를 완화한다. 자세한 근거는
    `docs/official-congestion-correction-proposal.md`를 참고한다.

    보정 조건:

    1. P2PNet 분석이 `COMPLETED`다.
    2. 저밀도 대형 오탐 의심 신호(`lowConfidenceSignal`)가 없다.
    3. P2PNet 값이 공식 값보다 크다(항목별로 판단).

    2번을 조건에 넣는 이유는 공식값 대체가 자동 위험등급을 직접 바꾸기
    때문이다. 색종이를 사람으로 오인해 L=100이 된 프레임이 CAUTION을
    자동 발동시키면 안 된다. 진단용 `denseScene.suspected` 신호에는 이
    게이트를 적용하지 않으므로 사람은 여전히 고밀도 의심을 확인할 수 있다.
    """
    correctable = (
        shadow_analysis_status == COMPLETED_STATUS
        and shadow_low_confidence_signal is not True
    )

    if not correctable:
        return CongestionCorrection(
            visual_congestion_score=(official_visual_congestion_score),
            local_peak_congestion_score=(official_local_peak_congestion_score),
            source=CONGESTION_SOURCE_OFFICIAL,
        )

    visual_score = _raise_only(
        official_value=official_visual_congestion_score,
        shadow_value=shadow_visual_congestion_score,
    )
    local_peak_score = _raise_only(
        official_value=official_local_peak_congestion_score,
        shadow_value=shadow_local_peak_congestion_score,
    )

    raised = (
        visual_score != official_visual_congestion_score
        or local_peak_score != official_local_peak_congestion_score
    )

    return CongestionCorrection(
        visual_congestion_score=visual_score,
        local_peak_congestion_score=local_peak_score,
        source=(CONGESTION_SOURCE_CORRECTED if raised else CONGESTION_SOURCE_OFFICIAL),
    )


def _raise_only(
    *,
    official_value: float | None,
    shadow_value: float | None,
) -> float | None:
    """그림자 값이 더 클 때만 대체한다. 낮추는 방향으로는 쓰지 않는다."""
    if shadow_value is None:
        return official_value

    if official_value is None:
        return shadow_value

    return max(official_value, shadow_value)
