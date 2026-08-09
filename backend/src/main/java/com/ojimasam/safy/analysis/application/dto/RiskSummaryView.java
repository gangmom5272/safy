package com.ojimasam.safy.analysis.application.dto;

import java.time.Instant;

import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.analysis.domain.type.RiskAssessmentStatus;

public record RiskSummaryView(
		RiskAssessmentStatus status,
		Double score,
		RiskLevel level,
		double confidence,
		boolean confirmRequired,
		Instant assessedAt
) {
	public static RiskSummaryView from(RiskAssessment assessment) {
		return new RiskSummaryView(
				assessment.getAssessmentStatus(), assessment.getFinalRiskScore(), assessment.getRiskLevel(),
				assessment.getRiskConfidence(), assessment.isConfirmRequired(), assessment.getAssessedAt());
	}
}
