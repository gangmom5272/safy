package com.ojimasam.safy.report.presentation;

import java.time.Instant;

import com.ojimasam.safy.report.application.CreateReportResult;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;

public record CreateReportResponse(
		Long reportId,
		ReportType reportType,
		ReportStatus status,
		Instant reportedAt
) {
	public static CreateReportResponse from(CreateReportResult result) {
		return new CreateReportResponse(
				result.reportId(),
				result.type(),
				result.status(),
				result.reportedAt()
		);
	}
}
