package com.ojimasam.safy.report.presentation;

import java.time.Instant;

import com.ojimasam.safy.report.application.CreateReportResult;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.domain.ReportRoutingStatus;

public record CreateReportResponse(
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
		ReportStatus status,
		ReportRoutingStatus routingStatus,
		Instant reportedAt
) {
	public static CreateReportResponse from(CreateReportResult result) {
		return new CreateReportResponse(
				result.reportId(),
				result.type(),
				result.category(),
				result.subcategory(),
				result.status(),
				result.routingStatus(),
				result.reportedAt()
		);
	}
}
