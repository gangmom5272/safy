package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;

public record ReportListItemView(
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
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
