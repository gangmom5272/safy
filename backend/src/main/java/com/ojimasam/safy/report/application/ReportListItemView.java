package com.ojimasam.safy.report.application;

import java.time.Instant;

import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportRoutingStatus;

public record ReportListItemView(
		Long reportId,
		ReportType reportType,
		String description,
		String address,
		ReportStatus status,
		ReportRoutingStatus routingStatus,
		String administrativeAreaName,
		Instant reportedAt,
		int imageCount,
		ReportImageView representativeImage
) {
}
