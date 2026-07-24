package com.ojimasam.safy.report.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportRepository {
	Report save(Report report);
	Optional<Report> findOneById(Long id);
	Optional<Report> findByIdForUpdate(Long id);
	Optional<Report> findByIdAndReporterId(Long id, Long reporterId);
	Page<Report> findAllByReporterId(Long reporterId, Pageable pageable);
	List<Long> findPendingRoutingIds(ReportRoutingStatus routingStatus, Instant dueAt, Pageable pageable);
	List<Long> findRoutedUnassignedIds(
			ReportRoutingStatus routingStatus,
			ReportStatus reportStatus,
			List<ReportOperatorAssignmentStatus> activeAssignmentStatuses,
			Pageable pageable
	);
}
