package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;

public record CreateReportResult(
		Long reportId,
		ReportType type,
		ReportCategory category,
		ReportSubcategory subcategory,
		ReportStatus status,
		ReportRoutingStatus routingStatus,
		Instant reportedAt
) {
	public static CreateReportResult from(Report report) {
		return new CreateReportResult(
				report.getId(),
				report.getType(),
				report.getCategory(),
				report.getSubcategory(),
				report.getStatus(),
				report.getRoutingStatus(),
				report.getReportedAt()
		);
	}
}
