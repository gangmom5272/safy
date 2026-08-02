package com.ojimasam.safy.analysis.domain;

public record RiskAssessmentCounts(
		int total,
		int completed,
		int invalid,
		int failed,
		int qualityClean,
		boolean congestionSourceMixed
) {
	public RiskAssessmentCounts {
		if (total < 0 || completed < 0 || invalid < 0 || failed < 0 || qualityClean < 0) {
			throw new IllegalArgumentException("위험도 집계 건수는 0 이상이어야 합니다.");
		}
	}
}
