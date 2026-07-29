package com.ojimasam.safy.report.presentation.dto;

import java.time.Instant;

import com.ojimasam.safy.report.application.dto.CreateReportResult;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;

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
