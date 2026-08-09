package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.admin.domain.AdminCenterMembership;

public interface JpaReportOperatorAssignmentRepository
		extends JpaRepository<ReportOperatorAssignment, Long>, ReportOperatorAssignmentRepository {
	@Override
	@EntityGraph(attributePaths = {"operator", "route", "route.controlCenter"})
	java.util.Optional<ReportOperatorAssignment> findByReport_IdAndStatusIn(
			Long reportId,
			java.util.Collection<com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus> statuses
	);

	@Override
	@EntityGraph(attributePaths = "operator")
	java.util.Optional<ReportOperatorAssignment> findTopByOperator_IdOrderByAssignedAtDesc(Long operatorId);

	@Override
	@EntityGraph(attributePaths = "operator")
	java.util.List<ReportOperatorAssignment> findAllByReport_IdAndStatus(
			Long reportId,
			com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus status
	);

	@Override
	@EntityGraph(attributePaths = {"operator", "route", "route.controlCenter"})
	java.util.List<ReportOperatorAssignment> findAllByReport_IdAndOperator_Id(Long reportId, Long operatorId);

	// assignedBy 까지 함께 읽는다. 자동 배정(null)과 큐에서 담당(본인)을 가르는 값이라
	// 타임라인 한 줄마다 필요하고, 지연 로딩으로 두면 배정 수만큼 쿼리가 늘어난다.
	@Override
	@EntityGraph(attributePaths = {"operator", "assignedBy", "route", "route.controlCenter"})
	java.util.List<ReportOperatorAssignment> findAllByReport_IdOrderByAssignedAtAscIdAsc(Long reportId);

	@Override
	@EntityGraph(attributePaths = {"report", "report.administrativeArea", "route", "route.controlCenter", "operator"})
	org.springframework.data.domain.Page<ReportOperatorAssignment> findAllByOperator_IdAndStatusIn(
			Long operatorId,
			java.util.Collection<com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus> statuses,
			org.springframework.data.domain.Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = {"report", "report.representativeBlurAsset",
			"report.representativeBlurAsset.sourceMediaAsset", "route", "route.controlCenter", "operator"})
	org.springframework.data.domain.Page<ReportOperatorAssignment> findAllByStatusIn(
			java.util.Collection<com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus> statuses,
			org.springframework.data.domain.Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = {"report", "report.representativeBlurAsset",
			"report.representativeBlurAsset.sourceMediaAsset", "route", "route.controlCenter", "operator"})
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
				  and membership.status = com.ojimasam.safy.admin.domain.type.MembershipStatus.ACTIVE
				  and membership.controlCenter.id = assignment.route.controlCenter.id
			  )
			""")
	org.springframework.data.domain.Page<ReportOperatorAssignment> findDashboardAssignmentsForOperator(
			@Param("operatorId") Long operatorId,
			@Param("statuses") java.util.Collection<com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus> statuses,
			org.springframework.data.domain.Pageable pageable
	);
}
