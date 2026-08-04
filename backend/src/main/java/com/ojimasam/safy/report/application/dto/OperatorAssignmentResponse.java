package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.analysis.application.dto.RiskSummaryView;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;

/**
 * 담당자 배정 한 건.
 *
 * <p>시각 필드가 둘인 이유: {@code reportedAt} 은 시민이 제보한 시각이고,
 * {@code assignedAt} 은 이 담당자에게 배정된 시각이다. 재배정되면 배정 시각만 뒤로
 * 밀리므로 "최신 제보순"으로 줄 세울 때는 반드시 {@code reportedAt} 을 써야 한다.
 */
public record OperatorAssignmentResponse(
		Long assignmentId,
		Long reportId,
		ReportOperatorAssignmentStatus assignmentStatus,
		ReportStatus reportStatus,
		String reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
		String description,
		String address,
		String administrativeAreaName,
		Long controlCenterId,
		String controlCenterName,
		Long operatorId,
		String operatorName,
		/** 시민이 제보한 시각. 목록 정렬·표시 기준이다. */
		Instant reportedAt,
		/** 이 담당자에게 배정된 시각. 재배정되면 뒤로 밀린다. */
		Instant assignedAt,
		Instant acceptedAt,
		Instant startedAt,
		Instant completedAt,
		/** 위험도 평가가 아직 없으면 null. 목록은 이 값으로 위험도 배지를 그린다. */
		RiskSummaryView risk
) {
	/** 단건 응답(배정·완료·반려 등)에는 위험도를 채우지 않는다. 화면이 곧바로 목록으로 돌아가기 때문이다. */
	public static OperatorAssignmentResponse from(ReportOperatorAssignment assignment) {
		return from(assignment, null);
	}

	public static OperatorAssignmentResponse from(ReportOperatorAssignment assignment, RiskSummaryView risk) {
		Report report = assignment.getReport();
		return new OperatorAssignmentResponse(
				assignment.getId(),
				report.getId(),
				assignment.getStatus(),
				report.getStatus(),
				report.getType().name(),
				report.getCategory(),
				report.getSubcategory(),
				report.getDescription(),
				report.getAddress(),
				report.getAdministrativeArea() == null ? null : report.getAdministrativeArea().getName(),
				assignment.getRoute().getControlCenter().getId(),
				assignment.getRoute().getControlCenter().getName(),
				assignment.getOperator().getId(),
				assignment.getOperator().getDisplayName(),
				report.getReportedAt(),
				assignment.getAssignedAt(),
				assignment.getAcceptedAt(),
				assignment.getStartedAt(),
				assignment.getCompletedAt(),
				risk
		);
	}
}
