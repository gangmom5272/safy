package com.ojimasam.safy.report.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportRoutingStatus;

public record ReportDetailView(
		Long reportId,
		ReportType reportType,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		ReportStatus status,
		ReportRoutingStatus routingStatus,
		Long administrativeAreaId,
		String administrativeAreaCode,
		String administrativeAreaName,
		AssignedControlCenterView assignedControlCenter,
		Instant reportedAt,
		Instant createdAt,
		Instant updatedAt,
		List<ReportImageView> images
) {
}
