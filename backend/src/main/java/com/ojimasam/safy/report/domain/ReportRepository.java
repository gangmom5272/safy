package com.ojimasam.safy.report.domain;

import java.math.BigDecimal;
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
	/**
	 * 시민 피드: 공개 상태만 조회한다. 처리 진행 상태는 따지지 않는다.
	 *
	 * <p>{@code from} 은 최근 창의 시작이고 {@code cursor} 는 페이지 경계다. 둘 다
	 * {@code reportedAt} 을 자르지만 역할이 달라 하나로 합치지 않는다.
	 */
	List<Report> findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			ReportPublicVisibility publicVisibility,
			Instant from,
			Instant cursor,
			Pageable pageable
	);
	List<Report> findByAdministrativeArea_IdAndPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
			Long administrativeAreaId,
			ReportPublicVisibility publicVisibility,
			Instant from,
			Instant cursor,
			Pageable pageable
	);
	/** 상황 요약의 '최근 제보' 수. 지도 마커와 같은 조건이어야 하므로 공개 + 최근 창으로 센다. */
	long countByAdministrativeArea_IdAndPublicVisibilityAndReportedAtAfter(
			Long administrativeAreaId,
			ReportPublicVisibility publicVisibility,
			Instant from
	);

	/**
	 * 장소 검색의 '최근 제보' 수를 계산하기 위한 전체 후보. 장소는 자치구보다 훨씬 좁아
	 * 행정구역으로 미리 거를 수 없으므로, 창 안의 공개 제보를 전부 읽어 애플리케이션에서
	 * 장소 폴리곤으로 직접 판정한다({@code PlaceRecentReportCounter}). 같은 30분 창을
	 * 도시 전체에 한 번 읽는 것이라 건수가 많지 않다.
	 */
	List<Report> findByPublicVisibilityAndReportedAtAfter(ReportPublicVisibility publicVisibility, Instant from);

	/**
	 * 좌표 반경 조회의 1차 필터. 반경을 감싸는 사각형으로 좁힌다.
	 *
	 * <p>사각형은 원보다 넓으므로 통과한 결과를 실제 거리로 한 번 더 걸러야 한다
	 * ({@code RadiusRecentReportCounter}). DB 에서 삼각함수를 쓰지 않으려고 이렇게 나눴다.
	 */
	List<Report> findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
			ReportPublicVisibility publicVisibility,
			Instant from,
			BigDecimal minLatitude,
			BigDecimal maxLatitude,
			BigDecimal minLongitude,
			BigDecimal maxLongitude
	);
	Optional<Report> findByIdAndPublicVisibility(Long id, ReportPublicVisibility publicVisibility);

	/**
	 * 격자 위험도 계산용. 시간 창 안에 접수된 제보를 셀 코드가 있는 것만 가져온다.
	 *
	 * <p>창을 자르는 기준은 {@code reportedAt} 이다. 라이브 제보는 프레임이 계속
	 * 들어와 위험도 평가 시각이 갱신되므로, {@code assessedAt} 으로 자르면 30분 전
	 * 제보가 현재 위험으로 잡힌다. "지금 이 격자에 무슨 일이 벌어지고 있나"를 재는
	 * 값이라 접수 시각을 쓴다.
	 */
	List<Report> findByGridCellCodeIsNotNullAndReportedAtBetween(Instant from, Instant to);

	long countByAdministrativeArea_IdAndStatusIn(Long administrativeAreaId, Collection<ReportStatus> statuses);
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
