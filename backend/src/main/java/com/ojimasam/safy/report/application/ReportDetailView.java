package com.ojimasam.safy.report.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.domain.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus;

public record ReportDetailView(
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
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
		List<ReportImageView> images,
		/**
		 * 조회한 담당자 본인의 최신 배정 상태. 재배정으로 넘긴 제보는 내 배정만 CANCELLED 되고
		 * 제보는 다른 담당자에게서 계속 진행되므로, 제보 상태만으로는 내 처리 결과를 알 수 없다.
		 * 시민 조회와 SUPER_ADMIN 조회에서는 null 이다.
		 */
		ReportOperatorAssignmentStatus myAssignmentStatus
) {
}
