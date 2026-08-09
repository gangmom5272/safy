import statistics
from dataclasses import dataclass


CONFIDENCE_BANDS = [
    (0.5, 0.6),
    (0.6, 0.7),
    (0.7, 0.8),
    (0.8, 0.9),
    (0.9, 1.01),
]

BORDERLINE_BAND_LABEL = "0.5-0.6"

# 138번 홀드아웃의 대형 오탐 23건과 정상 21건으로 임계값을 튜닝한 결과다.
# 경계 구간(0.5~0.6) 점 비율이 15% 이상이면 대형 오탐 의심으로 판정할 때
# 이 표본에서 정확도 81.8%(재현율 82.6%, 정밀도 82.6%)를 보였다.
# 표본이 작아(23+21건) 실제 운영 데이터가 쌓이면 재조정이 필요하다.
LOW_CONFIDENCE_BORDERLINE_FRACTION_THRESHOLD = 0.15


@dataclass(frozen=True)
class ConfidenceSummary:
    sample_id: str
    point_count: int
    mean_confidence: float
    median_confidence: float
    band_fractions: dict[str, float]


@dataclass(frozen=True)
class GroupComparison:
    flagged_sample_count: int
    normal_sample_count: int
    flagged_mean_of_means: float
    normal_mean_of_means: float
    flagged_mean_of_medians: float
    normal_mean_of_medians: float
    flagged_band_fractions: dict[str, float]
    normal_band_fractions: dict[str, float]


def _band_label(lower: float, upper: float) -> str:
    return f"{lower:.1f}-{upper:.1f}"


def summarize_point_confidences(
    *,
    sample_id: str,
    confidences: list[float],
) -> ConfidenceSummary:
    if not confidences:
        return ConfidenceSummary(
            sample_id=sample_id,
            point_count=0,
            mean_confidence=0.0,
            median_confidence=0.0,
            band_fractions={
                _band_label(lower, upper): 0.0 for lower, upper in CONFIDENCE_BANDS
            },
        )

    point_count = len(confidences)
    band_fractions = {}

    for lower, upper in CONFIDENCE_BANDS:
        matching = [value for value in confidences if lower <= value < upper]
        band_fractions[_band_label(lower, upper)] = round(
            len(matching) / point_count,
            4,
        )

    return ConfidenceSummary(
        sample_id=sample_id,
        point_count=point_count,
        mean_confidence=round(statistics.fmean(confidences), 4),
        median_confidence=round(statistics.median(confidences), 4),
        band_fractions=band_fractions,
    )


def determine_low_confidence_signal(
    *,
    confidences: list[float],
    threshold: float = LOW_CONFIDENCE_BORDERLINE_FRACTION_THRESHOLD,
) -> bool:
    if not confidences:
        return False

    borderline_count = sum(1 for value in confidences if 0.5 <= value < 0.6)

    return (borderline_count / len(confidences)) >= threshold


def compare_groups(
    *,
    flagged_summaries: list[ConfidenceSummary],
    normal_summaries: list[ConfidenceSummary],
) -> GroupComparison:
    if not flagged_summaries or not normal_summaries:
        raise ValueError("두 그룹 모두 한 건 이상의 요약이 필요합니다.")

    def _mean_of(
        summaries: list[ConfidenceSummary],
        selector,
    ) -> float:
        return round(
            statistics.fmean(selector(summary) for summary in summaries),
            4,
        )

    def _band_average(
        summaries: list[ConfidenceSummary],
    ) -> dict[str, float]:
        labels = summaries[0].band_fractions.keys()
        return {
            label: round(
                statistics.fmean(
                    summary.band_fractions[label] for summary in summaries
                ),
                4,
            )
            for label in labels
        }

    return GroupComparison(
        flagged_sample_count=len(flagged_summaries),
        normal_sample_count=len(normal_summaries),
        flagged_mean_of_means=_mean_of(
            flagged_summaries,
            lambda summary: summary.mean_confidence,
        ),
        normal_mean_of_means=_mean_of(
            normal_summaries,
            lambda summary: summary.mean_confidence,
        ),
        flagged_mean_of_medians=_mean_of(
            flagged_summaries,
            lambda summary: summary.median_confidence,
        ),
        normal_mean_of_medians=_mean_of(
            normal_summaries,
            lambda summary: summary.median_confidence,
        ),
        flagged_band_fractions=_band_average(flagged_summaries),
        normal_band_fractions=_band_average(normal_summaries),
    )
