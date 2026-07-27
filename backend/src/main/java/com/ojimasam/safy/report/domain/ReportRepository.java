package com.ojimasam.safy.report.domain;

import java.time.Instant;
import java.util.Collection;
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
	List<Report> findByStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);
	List<Report> findByAdministrativeArea_IdAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);
	List<Report> findByTypeAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			ReportType type,
			Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);
	List<Report> findByAdministrativeArea_IdAndTypeAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			ReportType type,
			Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);
	long countByAdministrativeArea_IdAndStatusIn(Long administrativeAreaId, Collection<ReportStatus> statuses);
	long countByAdministrativeArea_IdAndTypeAndStatusIn(
			Long administrativeAreaId,
			ReportType type,
			Collection<ReportStatus> statuses
	);
	long countByReporterId(Long reporterId);
	long countByReporterIdAndStatusIn(Long reporterId, Collection<ReportStatus> statuses);
	List<Long> findPendingRoutingIds(ReportRoutingStatus routingStatus, Instant dueAt, Pageable pageable);
	List<Long> findRoutedUnassignedIds(
			ReportRoutingStatus routingStatus,
			ReportStatus reportStatus,
			List<ReportOperatorAssignmentStatus> activeAssignmentStatuses,
			Pageable pageable
	);
}
