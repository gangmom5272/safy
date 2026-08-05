package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

import jakarta.persistence.LockModeType;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;

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
			com.ojimasam.safy.report.domain.type.ReportType type,
			java.util.Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = "administrativeArea")
	List<Report> findByAdministrativeArea_IdAndTypeAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			com.ojimasam.safy.report.domain.type.ReportType type,
			java.util.Collection<ReportStatus> statuses,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = {"administrativeArea", "representativeBlurAsset"})
	List<Report> findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			ReportPublicVisibility publicVisibility,
			Instant from,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = {"administrativeArea", "representativeBlurAsset"})
	List<Report> findByAdministrativeArea_IdAndPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			ReportPublicVisibility publicVisibility,
			Instant from,
			Instant cursor,
			Pageable pageable
	);

	@Override
	@EntityGraph(attributePaths = {"administrativeArea", "representativeBlurAsset"})
	Optional<Report> findByIdAndPublicVisibility(Long id, ReportPublicVisibility publicVisibility);

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

	@Override
	@Query(value = """
			select report
			from Report report
			where report.routingStatus = :routingStatus
			  and report.status = :reportStatus
			  and exists (
			    select route.id
			    from ReportCenterRoute route
			    where route.report.id = report.id
			      and route.status = :routeStatus
			      and route.controlCenter.id = :controlCenterId
			  )
			  and not exists (
			    select assignment.id
			    from ReportOperatorAssignment assignment
			    where assignment.report.id = report.id
			      and assignment.status in :activeAssignmentStatuses
			  )
			order by report.reportedAt asc, report.id asc
			""", countQuery = """
			select count(report)
			from Report report
			where report.routingStatus = :routingStatus
			  and report.status = :reportStatus
			  and exists (
			    select route.id
			    from ReportCenterRoute route
			    where route.report.id = report.id
			      and route.status = :routeStatus
			      and route.controlCenter.id = :controlCenterId
			  )
			  and not exists (
			    select assignment.id
			    from ReportOperatorAssignment assignment
			    where assignment.report.id = report.id
			      and assignment.status in :activeAssignmentStatuses
			  )
			""")
	Page<Report> findCenterQueue(
			@Param("routingStatus") ReportRoutingStatus routingStatus,
			@Param("reportStatus") ReportStatus reportStatus,
			@Param("controlCenterId") Long controlCenterId,
			@Param("routeStatus") ReportCenterRouteStatus routeStatus,
			@Param("activeAssignmentStatuses") List<ReportOperatorAssignmentStatus> activeAssignmentStatuses,
			Pageable pageable
	);
}
