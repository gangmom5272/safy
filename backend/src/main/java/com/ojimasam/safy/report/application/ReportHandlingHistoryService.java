package com.ojimasam.safy.report.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.report.application.dto.ReportHandlingEventType;
import com.ojimasam.safy.report.application.dto.ReportHandlingEventView;
import com.ojimasam.safy.report.application.dto.ReportHandlingHistoryView;
import com.ojimasam.safy.report.application.dto.ReportHandlingStagesView;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteType;
import com.ojimasam.safy.user.domain.User;

/**
 * 담당자 화면용 제보 처리 이력.
 *
 * <p>시민용 {@code ReportQueryService#getMyStatusHistory} 와 별개인 이유는 셋이다.
 * 시민용은 제보자 본인만 볼 수 있고, 상태를 접수/처리중/완료 3단계로 접어 재배정·이관을
 * 지워버리며, 담당자가 적은 사유를 안내 문구로 갈아끼운다. 관리자에게는 그 셋이 모두
 * 필요한 정보라 시민용을 손대지 않고 여기서 따로 만든다.
 *
 * <p>출처가 상태 이력이 아니라 배정과 라우팅인 것도 의도한 것이다. 상태 이력의
 * {@code changedBy} 는 "조작한 사람"이라 자동 배정 행에서는 null 이고, 그래서 상태 이력만
 * 읽으면 "누구에게 넘어갔는가"를 끝내 알 수 없다. 배정 행에는 담당자·센터·시각·회수 사유가
 * 모두 들어 있다.
 */
@Service
public class ReportHandlingHistoryService {
	private final ReportRepository reportRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportCenterRouteRepository routeRepository;
	private final OperatorReportAccessService operatorAccessService;

	public ReportHandlingHistoryService(
			ReportRepository reportRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportCenterRouteRepository routeRepository,
			OperatorReportAccessService operatorAccessService
	) {
		this.reportRepository = reportRepository;
		this.assignmentRepository = assignmentRepository;
		this.routeRepository = routeRepository;
		this.operatorAccessService = operatorAccessService;
	}

	@Transactional(readOnly = true)
	public ReportHandlingHistoryView getForOperator(Long userId, Long reportId) {
		operatorAccessService.requireAccess(userId, reportId);
		Report report = reportRepository.findOneById(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

		List<ReportOperatorAssignment> assignments =
				assignmentRepository.findAllByReport_IdOrderByAssignedAtAscIdAsc(reportId);
		List<ReportCenterRoute> routes = routeRepository.findAllByReport_IdOrderByRoutedAtAscIdAsc(reportId);

		List<ReportHandlingEventView> events = new ArrayList<>();
		for (ReportOperatorAssignment assignment : assignments) {
			addAssignmentEvents(events, assignment, routes);
		}
		for (ReportCenterRoute route : routes) {
			if (route.getRouteType() == ReportCenterRouteType.FAILOVER) {
				events.add(autoTransferEvent(route));
			}
		}
		if (report.getConfirmedAt() != null) {
			events.add(confirmedEvent(report));
		}
		// 안정 정렬이라 같은 시각이면 넣은 순서가 유지된다. 배정을 시간순으로 훑으며
		// 놓음을 먼저 넣으므로 "김담당이 놓음 → 박담당이 받음"의 순서가 뒤집히지 않는다.
		events.sort(Comparator.comparing(ReportHandlingEventView::occurredAt));

		return new ReportHandlingHistoryView(reportId, stages(report, assignments, routes), events);
	}

	private void addAssignmentEvents(List<ReportHandlingEventView> events,
			ReportOperatorAssignment assignment, List<ReportCenterRoute> routes) {
		User operator = assignment.getOperator();
		ControlCenter center = assignment.getRoute().getControlCenter();
		User assignedBy = assignment.getAssignedBy();
		boolean selfClaimed = assignedBy != null && assignedBy.getId().equals(operator.getId());

		events.add(event(selfClaimed ? ReportHandlingEventType.CLAIMED : ReportHandlingEventType.AUTO_ASSIGNED,
				assignment.getAssignedAt(), operator, center, null, null));
		if (assignment.getStartedAt() != null) {
			events.add(event(ReportHandlingEventType.VIEWED,
					assignment.getStartedAt(), operator, center, null, null));
		}
		if (assignment.getCompletedAt() != null) {
			events.add(event(ReportHandlingEventType.COMPLETED,
					assignment.getCompletedAt(), operator, center, null, null));
		}
		if (assignment.getCancelledAt() == null) return;

		ReportHandlingEventType type = cancelEventType(assignment.getCancelReason());
		ControlCenter destination = type == ReportHandlingEventType.TRANSFERRED
				? transferDestination(assignment, routes)
				: null;
		events.add(event(type, assignment.getCancelledAt(), operator, center,
				destination, assignment.getReleaseReason()));
	}

	/**
	 * 이관으로 놓은 배정의 목적지. 새 라우트가 옛 라우트를 previousRoute 로 가리키므로
	 * 센터 이름을 비교하지 않고 FK 로 따라간다.
	 */
	private ControlCenter transferDestination(ReportOperatorAssignment assignment,
			List<ReportCenterRoute> routes) {
		Long fromRouteId = assignment.getRoute().getId();
		return routes.stream()
				.filter(route -> route.getPreviousRoute() != null
						&& route.getPreviousRoute().getId().equals(fromRouteId))
				.findFirst()
				.map(ReportCenterRoute::getControlCenter)
				.orElse(null);
	}

	/**
	 * 승인은 배정이 아니라 제보에 기록된다({@code reports.confirmed_at}). 배정 시각에서
	 * 사건을 만드는 다른 항목과 달리 여기서 따로 만드는 이유다.
	 *
	 * <p>담당자를 채우지 않는다. 제보에는 승인 시각만 남고 누가 눌렀는지는 들어 있지 않다.
	 * 배정 이력에서 시각으로 역추적할 수는 있으나, 재배정으로 배정이 여러 개면 추측이 되므로
	 * 시스템 사건(AUTO_TRANSFERRED)처럼 담당자 없이 남긴다.
	 */
	private ReportHandlingEventView confirmedEvent(Report report) {
		return event(ReportHandlingEventType.CONFIRMED, report.getConfirmedAt(),
				null, null, null, null);
	}

	private ReportHandlingEventView autoTransferEvent(ReportCenterRoute route) {
		ControlCenter from = route.getPreviousRoute() == null
				? null
				: route.getPreviousRoute().getControlCenter();
		return event(ReportHandlingEventType.AUTO_TRANSFERRED, route.getRoutedAt(),
				null, from, route.getControlCenter(), route.getRoutingReason());
	}

	private ReportHandlingEventView event(ReportHandlingEventType type, Instant occurredAt,
			User operator, ControlCenter center, ControlCenter destination, String reason) {
		return new ReportHandlingEventView(
				type,
				occurredAt,
				operator == null ? null : operator.getId(),
				operator == null ? null : operator.getDisplayName(),
				center == null ? null : center.getId(),
				center == null ? null : center.getName(),
				destination == null ? null : destination.getId(),
				destination == null ? null : destination.getName(),
				reason
		);
	}

	private ReportHandlingEventType cancelEventType(ReportAssignmentCancelReason cancelReason) {
		return switch (cancelReason) {
			case REASSIGN_REQUESTED -> ReportHandlingEventType.REASSIGNED;
			case TRANSFERRED -> ReportHandlingEventType.TRANSFERRED;
			case REPORT_REJECTED -> ReportHandlingEventType.REJECTED;
			case ASSIGNMENT_TIMEOUT -> ReportHandlingEventType.ASSIGNMENT_TIMEOUT;
			case OPERATOR_OFFLINE -> ReportHandlingEventType.OPERATOR_OFFLINE;
			case OPERATOR_AWAY -> ReportHandlingEventType.OPERATOR_AWAY;
			case REPORTER_CANCELLED -> ReportHandlingEventType.REPORTER_CANCELLED;
		};
	}

	private ReportHandlingStagesView stages(Report report, List<ReportOperatorAssignment> assignments,
			List<ReportCenterRoute> routes) {
		Instant firstInProgressAt = assignments.stream()
				.map(ReportOperatorAssignment::getStartedAt)
				.filter(Objects::nonNull)
				.min(Comparator.naturalOrder())
				.orElse(null);
		int reassignCount = (int) assignments.stream()
				.filter(assignment -> assignment.getCancelReason()
						== ReportAssignmentCancelReason.REASSIGN_REQUESTED)
				.count();
		// 최초 라우팅(AUTO)은 이관이 아니다. 센터가 실제로 바뀐 횟수만 센다.
		int transferCount = (int) routes.stream()
				.filter(route -> route.getRouteType() != ReportCenterRouteType.AUTO)
				.count();
		return new ReportHandlingStagesView(
				report.getReportedAt(),
				firstInProgressAt,
				report.getClosedAt(),
				report.getStatus(),
				reassignCount,
				transferCount
		);
	}
}
