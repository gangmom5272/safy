package com.ojimasam.safy.report.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;

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
	/** 시민 피드: 공개 상태만 조회한다. 처리 진행 상태는 따지지 않는다. */
	List<Report> findByPublicVisibilityAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			ReportPublicVisibility publicVisibility,
			Instant cursor,
			Pageable pageable
	);
	List<Report> findByAdministrativeArea_IdAndPublicVisibilityAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			ReportPublicVisibility publicVisibility,
			Instant cursor,
			Pageable pageable
	);
	Optional<Report> findByIdAndPublicVisibility(Long id, ReportPublicVisibility publicVisibility);

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

	/** 센터 공용 큐: 해당 관제센터로 라우팅됐지만 아직 담당자가 없는 제보. 오래된 것부터. */
	Page<Report> findCenterQueue(
			ReportRoutingStatus routingStatus,
			ReportStatus reportStatus,
			Long controlCenterId,
			ReportCenterRouteStatus routeStatus,
			List<ReportOperatorAssignmentStatus> activeAssignmentStatuses,
			Pageable pageable
	);
}
