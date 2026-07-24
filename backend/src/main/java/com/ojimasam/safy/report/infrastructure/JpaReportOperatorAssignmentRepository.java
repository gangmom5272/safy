package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;

public interface JpaReportOperatorAssignmentRepository
		extends JpaRepository<ReportOperatorAssignment, Long>, ReportOperatorAssignmentRepository {
	@Override
	@EntityGraph(attributePaths = {"operator", "route", "route.controlCenter"})
	java.util.Optional<ReportOperatorAssignment> findByReport_IdAndStatusIn(
			Long reportId,
			java.util.Collection<com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus> statuses
	);

	@Override
	@EntityGraph(attributePaths = "operator")
	java.util.Optional<ReportOperatorAssignment> findTopByOperator_IdOrderByAssignedAtDesc(Long operatorId);

	@Override
	@EntityGraph(attributePaths = {"report", "report.administrativeArea", "route", "route.controlCenter", "operator"})
	org.springframework.data.domain.Page<ReportOperatorAssignment> findAllByOperator_IdAndStatusIn(
			Long operatorId,
			java.util.Collection<com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus> statuses,
			org.springframework.data.domain.Pageable pageable
	);
}
