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

from dataclasses import dataclass


# 프로젝트 최종 위험등급 예외 규칙의 "L >= 80 → 최소 CAUTION" 기준을
# 그대로 사용한다. 신호가 먹이는 규칙과 같은 경계선을 쓰는 것이 의미가
# 분명하기 때문이다.
DENSE_SCENE_LOCAL_PEAK_THRESHOLD = 80.0


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
