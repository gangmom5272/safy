package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	@EntityGraph(attributePaths = "operator")
	java.util.List<ReportOperatorAssignment> findAllByReport_IdAndStatus(
			Long reportId,
			com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus status
	);

	@Override
	@EntityGraph(attributePaths = {"report", "report.administrativeArea", "route", "route.controlCenter", "operator"})
	org.springframework.data.domain.Page<ReportOperatorAssignment> findAllByOperator_IdAndStatusIn(
			Long operatorId,
			java.util.Collection<com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus> statuses,
			org.springframework.data.domain.Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = {"report", "route", "route.controlCenter", "operator"})
	org.springframework.data.domain.Page<ReportOperatorAssignment> findAllByStatusIn(
			java.util.Collection<com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus> statuses,
			org.springframework.data.domain.Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = {"report", "route", "route.controlCenter", "operator"})
	@Query("""
			select assignment
			from ReportOperatorAssignment assignment
			where assignment.operator.id = :operatorId
			  and assignment.status in :statuses
			  and exists (
				select membership.id
				from AdminCenterMembership membership
				where membership.user.id = :operatorId
				  and membership.isPrimary = true
				  and membership.status = com.ojimasam.safy.admin.domain.MembershipStatus.ACTIVE
				  and membership.controlCenter.id = assignment.route.controlCenter.id
			  )
			""")
	org.springframework.data.domain.Page<ReportOperatorAssignment> findDashboardAssignmentsForOperator(
			@Param("operatorId") Long operatorId,
			@Param("statuses") java.util.Collection<com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus> statuses,
			org.springframework.data.domain.Pageable pageable
	);
}
