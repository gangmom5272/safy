package com.ojimasam.safy.report.domain;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportOperatorAssignmentRepository {
	ReportOperatorAssignment save(ReportOperatorAssignment assignment);

	Optional<ReportOperatorAssignment> findByReport_IdAndStatusIn(
			Long reportId,
			Collection<ReportOperatorAssignmentStatus> statuses
	);

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
}
