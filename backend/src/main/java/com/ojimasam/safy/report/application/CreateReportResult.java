package com.ojimasam.safy.report.application;

import java.time.Instant;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportRoutingStatus;

public record CreateReportResult(
		Long reportId,
		ReportType type,
		ReportStatus status,
		ReportRoutingStatus routingStatus,
		Instant reportedAt
) {
	public static CreateReportResult from(Report report) {
		return new CreateReportResult(
				report.getId(),
				report.getType(),
				report.getStatus(),
				report.getRoutingStatus(),
				report.getReportedAt()
		);
	}
}
