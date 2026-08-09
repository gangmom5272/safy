import pytest

from app.services.p2pnet_confidence_signal import (
    ConfidenceSummary,
    compare_groups,
    determine_low_confidence_signal,
    summarize_point_confidences,
)


def test_summarize_point_confidences_computes_mean_median_and_bands() -> None:
    summary = summarize_point_confidences(
        sample_id="sample-1",
        confidences=[0.51, 0.55, 0.72, 0.95, 0.99],
    )

    assert summary.point_count == 5
    assert summary.mean_confidence == pytest.approx(0.744, abs=1e-3)
    assert summary.median_confidence == 0.72
    assert summary.band_fractions["0.5-0.6"] == pytest.approx(0.4)
    assert summary.band_fractions["0.7-0.8"] == pytest.approx(0.2)
    assert summary.band_fractions["0.9-1.0"] == pytest.approx(0.4)
    assert sum(summary.band_fractions.values()) == pytest.approx(1.0)


def test_summarize_point_confidences_handles_empty_points() -> None:
    summary = summarize_point_confidences(sample_id="sample-empty", confidences=[])

    assert summary.point_count == 0
    assert summary.mean_confidence == 0.0
    assert summary.median_confidence == 0.0
    assert all(value == 0.0 for value in summary.band_fractions.values())


def test_compare_groups_detects_borderline_heavy_flagged_group() -> None:
    flagged_summaries = [
        summarize_point_confidences(
            sample_id=f"flagged-{index}",
            confidences=[0.51, 0.52, 0.53, 0.54, 0.55],
        )
        for index in range(3)
    ]
    normal_summaries = [
        summarize_point_confidences(
            sample_id=f"normal-{index}",
            confidences=[0.9, 0.92, 0.95, 0.97, 0.99],
        )
        for index in range(3)
    ]

    comparison = compare_groups(
        flagged_summaries=flagged_summaries,
        normal_summaries=normal_summaries,
    )

    assert comparison.flagged_sample_count == 3
    assert comparison.normal_sample_count == 3
    assert comparison.flagged_mean_of_means < comparison.normal_mean_of_means
    assert (
        comparison.flagged_band_fractions["0.5-0.6"]
        > comparison.normal_band_fractions["0.5-0.6"]
    )
    assert (
        comparison.flagged_band_fractions["0.9-1.0"]
        < comparison.normal_band_fractions["0.9-1.0"]
    )


def test_compare_groups_requires_non_empty_groups() -> None:
    summary = summarize_point_confidences(sample_id="only", confidences=[0.6])

    with pytest.raises(ValueError, match="두 그룹 모두 한 건 이상의 요약이 필요합니다"):
        compare_groups(flagged_summaries=[], normal_summaries=[summary])

    with pytest.raises(ValueError, match="두 그룹 모두 한 건 이상의 요약이 필요합니다"):
        compare_groups(flagged_summaries=[summary], normal_summaries=[])


def test_determine_low_confidence_signal_flags_borderline_heavy_points() -> None:
    confidences = [0.51, 0.52, 0.53, 0.9, 0.95, 0.99, 0.98, 0.97]

    assert (
        determine_low_confidence_signal(
            confidences=confidences,
            threshold=0.3,
        )
        is True
    )


def test_determine_low_confidence_signal_passes_confident_points() -> None:
    confidences = [0.9, 0.92, 0.95, 0.97, 0.99]

    assert (
        determine_low_confidence_signal(
            confidences=confidences,
            threshold=0.15,
        )
        is False
    )


def test_determine_low_confidence_signal_handles_no_points() -> None:
    assert determine_low_confidence_signal(confidences=[]) is False


def test_confidence_summary_is_frozen_dataclass() -> None:
    summary = ConfidenceSummary(
        sample_id="s",
        point_count=1,
        mean_confidence=0.5,
        median_confidence=0.5,
        band_fractions={"0.5-0.6": 1.0},
    )

    with pytest.raises(AttributeError):
        summary.point_count = 2
