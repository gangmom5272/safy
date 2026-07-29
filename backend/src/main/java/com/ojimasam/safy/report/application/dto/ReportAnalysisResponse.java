package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

public record ReportAnalysisResponse(
		Long reportId,
		String status,
		String summary,
		String riskLevel,
		Integer crowdCount,
		Double confidence,
		Instant analyzedAt
) {
	public static ReportAnalysisResponse pending(Long reportId) {
		return new ReportAnalysisResponse(reportId, "NOT_STARTED", null, null, null, null, null);
	}
}
