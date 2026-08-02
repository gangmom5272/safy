package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

public record ReportAnalysisResponse(
		Long reportId,
		String status,
		String summary,
		String riskLevel,
		Double riskScore,
		Integer crowdCount,
		Double confidence,
		Boolean confirmRequired,
		Double peakVisualCongestionScore,
		Double peakLocalCongestionScore,
		Double persistenceScore,
		Double growthScore,
		Integer totalFrameCount,
		Integer completedFrameCount,
		Integer invalidFrameCount,
		Integer failedFrameCount,
		String riskRuleVersion,
		Instant analyzedAt
) {
	public static ReportAnalysisResponse pending(Long reportId) {
		return new ReportAnalysisResponse(reportId, "NOT_STARTED", null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null);
	}
}
