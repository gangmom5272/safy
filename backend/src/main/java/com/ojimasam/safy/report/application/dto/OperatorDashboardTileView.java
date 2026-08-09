package com.ojimasam.safy.report.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.analysis.application.dto.RiskSummaryView;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

/**
 * 관제 대시보드의 제보 타일 하나.
 *
 * <p>시각 필드가 둘인 이유: {@code reportedAt} 은 시민이 제보한 시각이고,
 * {@code assignedAt} 은 이 담당자에게 배정된 시각이다. 재배정되면 배정 시각만 뒤로
 * 밀리므로 "최신 제보순"으로 줄 세울 때는 반드시 {@code reportedAt} 을 써야 한다.
 */
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
		/** 시민이 제보한 시각. 목록 정렬·표시 기준이다. */
		Instant reportedAt,
		/** 이 담당자에게 배정된 시각. 재배정되면 뒤로 밀린다. */
		Instant assignedAt,
		DashboardImageView image,
		DashboardLiveView live,
		RiskSummaryView risk
) {
}
