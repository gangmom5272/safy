package com.ojimasam.safy.report.application;

import java.time.Instant;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.ReportStatus;

public record OperatorAssignmentResponse(
		Long assignmentId,
		Long reportId,
		ReportOperatorAssignmentStatus assignmentStatus,
		ReportStatus reportStatus,
		String reportType,
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
		Instant completedAt
) {
	public static OperatorAssignmentResponse from(ReportOperatorAssignment assignment) {
		Report report = assignment.getReport();
		return new OperatorAssignmentResponse(
				assignment.getId(),
				report.getId(),
				assignment.getStatus(),
				report.getStatus(),
				report.getType().name(),
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
				assignment.getCompletedAt()
		);
	}
}
