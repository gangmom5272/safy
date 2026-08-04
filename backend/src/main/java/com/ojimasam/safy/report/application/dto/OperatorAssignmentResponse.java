package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.analysis.application.dto.RiskSummaryView;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;

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
				assignment.getAssignedAt(),
				assignment.getAcceptedAt(),
				assignment.getStartedAt(),
				assignment.getCompletedAt(),
				risk
		);
	}
}
