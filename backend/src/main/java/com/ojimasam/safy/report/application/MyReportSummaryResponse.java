package com.ojimasam.safy.report.application;

public record MyReportSummaryResponse(
		long totalCount,
		long activeCount,
		long closedCount
) {
}
