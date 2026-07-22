package com.ojimasam.safy.report.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;

public record ReportDetailView(
		Long reportId,
		ReportType reportType,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		ReportStatus status,
		Instant reportedAt,
		Instant createdAt,
		Instant updatedAt,
		List<ReportImageView> images
) {
}
