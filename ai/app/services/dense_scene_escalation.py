"""고밀도 장면 위험 상향(escalation) 신호.

이 모듈은 "이 프레임이 고밀도 장면으로 의심되는가"만 판정한다.
최종 위험도와 위험등급은 백엔드가 계산하며(`S15P11A503-102`), AI는 판단
근거가 되는 신호만 제공한다.

핵심 설계 원칙: SAFY는 기존 안전관리 프로세스를 대체하지 않고 보조한다.
최종 판단은 사람이 하므로, 위험 상향 신호는 관대하게 내보내고 확신도가
낮다는 사실은 별도 필드로 알린다. 확신도가 낮다는 이유로 위험 신호 자체를
막지 않는다.
"""

from dataclasses import dataclass


# 프로젝트 시나리오 분류의 CROWDED 기준(51명 이상)을 그대로 사용한다.
DENSE_SCENE_PERSON_THRESHOLD = 51


@dataclass(frozen=True)
class DenseSceneAssessment:
    suspected: bool
    official_says_dense: bool
    shadow_says_dense: bool
    person_threshold: int


def determine_dense_scene_assessment(
    *,
    official_person_count: int | None,
    shadow_person_count: int | None,
    person_threshold: int = DENSE_SCENE_PERSON_THRESHOLD,
) -> DenseSceneAssessment:
    """두 모델 중 하나라도 고밀도를 지목하면 고밀도 의심으로 판정한다.

    YOLO는 고밀도에서 사람을 크게 놓치고(undercount) P2PNet은 저밀도에서
    과대 계수하는 경향이 있어, 어느 한 모델의 인원수만으로는 고밀도를
    판정할 수 없다. 위험을 놓치지 않는 쪽을 우선해 OR 조건을 사용한다.

    P2PNet의 저밀도 대형 오탐 의심 신호(`lowConfidenceSignal`)는 이 판정을
    막지 않는다. 그 신호는 신뢰도 정보로만 함께 기록한다.
    """
    official_says_dense = (
        official_person_count is not None and official_person_count >= person_threshold
    )
    shadow_says_dense = (
        shadow_person_count is not None and shadow_person_count >= person_threshold
    )

    return DenseSceneAssessment(
        suspected=(official_says_dense or shadow_says_dense),
        official_says_dense=official_says_dense,
        shadow_says_dense=shadow_says_dense,
        person_threshold=person_threshold,
    )
