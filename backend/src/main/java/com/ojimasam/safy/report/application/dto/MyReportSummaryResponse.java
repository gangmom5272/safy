package com.ojimasam.safy.report.application.dto;

public record MyReportSummaryResponse(
		long totalCount,
		long activeCount,
		long closedCount
) {
}
