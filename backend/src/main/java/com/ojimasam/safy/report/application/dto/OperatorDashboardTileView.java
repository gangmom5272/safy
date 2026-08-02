package com.ojimasam.safy.report.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.analysis.application.dto.RiskSummaryView;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

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
		DashboardLiveView live,
		RiskSummaryView risk
) {
}
