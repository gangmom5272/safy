package com.ojimasam.safy.report.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.domain.ReportType;

public record OperatorDashboardTileView(
		Long assignmentId,
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		ReportStatus reportStatus,
		ReportOperatorAssignmentStatus assignmentStatus,
		Long controlCenterId,
		String controlCenterName,
		Long operatorId,
		String operatorName,
		Instant assignedAt,
		DashboardImageView image,
		DashboardLiveView live
) {
}
