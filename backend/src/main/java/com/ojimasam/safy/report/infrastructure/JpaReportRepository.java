package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus;

import jakarta.persistence.LockModeType;

public interface JpaReportRepository extends JpaRepository<Report, Long>, ReportRepository {
	@Override
	@EntityGraph(attributePaths = "administrativeArea")
	Optional<Report> findByIdAndReporterId(Long id, Long reporterId);

	@Override
	@EntityGraph(attributePaths = "administrativeArea")
	org.springframework.data.domain.Page<Report> findAllByReporterId(Long reporterId, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = "administrativeArea")
	List<Report> findByStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			java.util.Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = "administrativeArea")
	List<Report> findByAdministrativeArea_IdAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			java.util.Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = "administrativeArea")
	List<Report> findByTypeAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			com.ojimasam.safy.report.domain.ReportType type,
			java.util.Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = "administrativeArea")
	List<Report> findByAdministrativeArea_IdAndTypeAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			com.ojimasam.safy.report.domain.ReportType type,
			java.util.Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select report from Report report where report.id = :id")
	Optional<Report> findByIdForUpdate(@Param("id") Long id);

	@Override
	@Query("""
			select report.id
			from Report report
			where report.routingStatus = :routingStatus
			  and report.nextRoutingAt <= :dueAt
			order by report.nextRoutingAt asc, report.id asc
			""")
	List<Long> findPendingRoutingIds(
			@Param("routingStatus") ReportRoutingStatus routingStatus,
			@Param("dueAt") Instant dueAt,
			Pageable pageable
	);

	@Override
	@Query("""
			select report.id
			from Report report
			where report.routingStatus = :routingStatus
			  and report.status = :reportStatus
			  and not exists (
			    select assignment.id
			    from ReportOperatorAssignment assignment
			    where assignment.report.id = report.id
			      and assignment.status in :activeAssignmentStatuses
			  )
			order by report.reportedAt asc, report.id asc
			""")
	List<Long> findRoutedUnassignedIds(
			@Param("routingStatus") ReportRoutingStatus routingStatus,
			@Param("reportStatus") ReportStatus reportStatus,
			@Param("activeAssignmentStatuses") List<ReportOperatorAssignmentStatus> activeAssignmentStatuses,
			Pageable pageable
	);
}
