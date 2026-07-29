package com.ojimasam.safy.report.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

public interface ReportOperatorAssignmentRepository {
	ReportOperatorAssignment save(ReportOperatorAssignment assignment);

	List<ReportOperatorAssignment> findAllByReport_IdAndStatus(Long reportId, ReportOperatorAssignmentStatus status);

	Optional<ReportOperatorAssignment> findByReport_IdAndStatusIn(
			Long reportId,
			Collection<ReportOperatorAssignmentStatus> statuses
	);

	/**
	 * 재배정이 있었다면 한 제보에 담당자별 배정 이력이 여러 건 남는다.
	 * 종결 건까지 포함해 조회 권한을 판단할 때 사용한다.
	 */
	List<ReportOperatorAssignment> findAllByReport_IdAndOperator_Id(Long reportId, Long operatorId);

	long countByOperator_IdAndStatusIn(
			Long operatorId,
			Collection<ReportOperatorAssignmentStatus> statuses
	);

	Optional<ReportOperatorAssignment> findTopByOperator_IdOrderByAssignedAtDesc(Long operatorId);

	boolean existsByReport_IdAndStatusIn(
			Long reportId,
			Collection<ReportOperatorAssignmentStatus> statuses
	);

	Page<ReportOperatorAssignment> findAllByOperator_IdAndStatusIn(
			Long operatorId,
			Collection<ReportOperatorAssignmentStatus> statuses,
			Pageable pageable
	);

	Page<ReportOperatorAssignment> findAllByStatusIn(
			Collection<ReportOperatorAssignmentStatus> statuses,
			Pageable pageable
	);

	Page<ReportOperatorAssignment> findDashboardAssignmentsForOperator(
			Long operatorId,
			Collection<ReportOperatorAssignmentStatus> statuses,
			Pageable pageable
	);
}
