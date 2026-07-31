package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenterAreaRepository;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.type.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.type.CoverageStatus;
import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.RealtimeEventRepository;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;
import com.ojimasam.safy.report.application.dto.ReportTransferResponse;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteType;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;

/**
 * 관제센터 간 제보 이관. 담당자 개인 교체(반납·재배정)와 달리 센터 자체가 처리할 수
 * 없을 때 쓰는 별도 절차다. 이관 대상은 같은 행정구역을 담당(커버리지 등록)하는
 * 센터로 제한된다. 거쳐온 센터라도 상태는 변한다(근무자 복귀 등)는 전제로 영구
 * 차단하지 않는다 — 자동 이관은 근무자 있는 센터만 고르고 체인 깊이 상한으로 폭주를
 * 막으며, 수동 이관은 거쳐온 센터의 경우 근무중 담당자가 있을 때만 허용한다.
 * 이관 후에는 최초 라우팅과 동일하게 대상 센터에서 자동 배정을 시도하고,
 * 실패하면 대상 센터 공용 큐에 남는다.
 */
@Service
public class ReportTransferService {
	/** 자동 이관 연쇄가 커버리지 등록 실수(순환 등)로 폭주하지 않게 막는 상한. */
	private static final int MAX_ROUTE_CHAIN_DEPTH = 20;
	private static final int STALL_NOTIFY_ADMIN_LIMIT = 50;

	private final ReportRepository reportRepository;
	private final ReportCenterRouteRepository routeRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportStatusHistoryRepository historyRepository;
	private final AdminCenterMembershipRepository membershipRepository;
	private final OperatorPresenceRepository presenceRepository;
	private final ControlCenterAreaRepository controlCenterAreaRepository;
	private final ReportOperatorAssignmentService assignmentService;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final RealtimeEventService realtimeEventService;
	private final RealtimeEventRepository realtimeEventRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	@Autowired
	public ReportTransferService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			AdminCenterMembershipRepository membershipRepository,
			OperatorPresenceRepository presenceRepository,
			ControlCenterAreaRepository controlCenterAreaRepository,
			ReportOperatorAssignmentService assignmentService,
			ReportRealtimeNotifier realtimeNotifier,
			RealtimeEventService realtimeEventService,
			RealtimeEventRepository realtimeEventRepository,
			UserRepository userRepository
	) {
		this(reportRepository, routeRepository, assignmentRepository, historyRepository, membershipRepository,
				presenceRepository, controlCenterAreaRepository, assignmentService, realtimeNotifier,
				realtimeEventService, realtimeEventRepository, userRepository, Clock.systemUTC());
	}

	ReportTransferService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			AdminCenterMembershipRepository membershipRepository,
			OperatorPresenceRepository presenceRepository,
			ControlCenterAreaRepository controlCenterAreaRepository,
			ReportOperatorAssignmentService assignmentService,
			ReportRealtimeNotifier realtimeNotifier,
			RealtimeEventService realtimeEventService,
			RealtimeEventRepository realtimeEventRepository,
			UserRepository userRepository,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.routeRepository = routeRepository;
		this.assignmentRepository = assignmentRepository;
		this.historyRepository = historyRepository;
		this.membershipRepository = membershipRepository;
		this.presenceRepository = presenceRepository;
		this.controlCenterAreaRepository = controlCenterAreaRepository;
		this.assignmentService = assignmentService;
		this.realtimeNotifier = realtimeNotifier;
		this.realtimeEventService = realtimeEventService;
		this.realtimeEventRepository = realtimeEventRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/** 담당자가 사유를 적고 대상 센터를 골라 이관을 신청한다. */
	@Transactional
	public ReportTransferResponse transferManually(Long operatorId, Long reportId,
			Long targetControlCenterId, String reason) {
		Report report = reportRepository.findByIdForUpdate(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		ReportOperatorAssignment assignment = assignmentRepository
				.findByReport_IdAndStatusIn(reportId, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES)
				.filter(found -> found.getOperator().getId().equals(operatorId))
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		validateActiveMembership(operatorId, assignment.getRoute().getControlCenter().getId());

		ReportCenterRoute currentRoute = assignment.getRoute();
		if (currentRoute.getStatus() != ReportCenterRouteStatus.ACTIVE) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "이미 이관되었거나 종료된 라우팅입니다.");
		}
		ControlCenterArea targetCoverage = resolveTarget(report, currentRoute, targetControlCenterId);

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		try {
			report.releaseForReassignment();
			assignment.cancel(now, ReportAssignmentCancelReason.TRANSFERRED);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
		currentRoute.markRerouted(now);
		ReportCenterRoute newRoute = routeRepository.save(ReportCenterRoute.reroute(
				currentRoute, targetCoverage.getControlCenter(), ReportCenterRouteType.MANUAL,
				targetCoverage.getPriority(), reason, now
		));
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), assignment.getOperator(), reason, now
		));
		realtimeNotifier.reportReassigned(reportId, report.getReporterId());
		Optional<ReportOperatorAssignment> next = assignmentService.assignAutomatically(report, newRoute, operatorId);
		return ReportTransferResponse.from(newRoute, next.isPresent());
	}

	/**
	 * 근무중 담당자가 0명인 센터의 미배정 제보를 백업 센터로 자동 이관한다.
	 * "후보 0명"은 임계값이 아닌 이분법이라 튜닝 없이 안전하다. 이관할 곳도 없으면
	 * SUPER_ADMIN에게 멈춤(STALLED) 알림을 1회 보낸다. 재시도 스케줄러가 호출한다.
	 */
	@Transactional
	public boolean failoverIfCenterUnavailable(Long reportId) {
		Report report = reportRepository.findByIdForUpdate(reportId).orElse(null);
		if (report == null || report.getStatus() != ReportStatus.RECEIVED
				|| report.getRoutingStatus() != ReportRoutingStatus.ROUTED) {
			return false;
		}
		if (assignmentRepository.existsByReport_IdAndStatusIn(
				reportId, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES)) {
			return false;
		}
		ReportCenterRoute currentRoute = routeRepository
				.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.orElse(null);
		if (currentRoute == null) return false;
		// 센터에 근무중 담당자가 한 명이라도 있으면 센터 안에서 풀 문제다(공용 큐·재시도).
		if (hasOnDutyOperator(currentRoute.getControlCenter().getId())) return false;

		Instant now = clock.instant();
		// 거쳐온 센터도 근무자가 복귀했으면 다시 후보가 된다(영구 차단 시 복구된 센터로
		// 못 돌아가 교착). 무한 왕복은 근무자 필터가, 폭주는 체인 깊이 상한이 막는다.
		if (routeChainDepth(currentRoute) >= MAX_ROUTE_CHAIN_DEPTH) {
			notifyStalledOnce(report);
			return false;
		}
		Long currentCenterId = currentRoute.getControlCenter().getId();
		Optional<ControlCenterArea> target = controlCenterAreaRepository.findRoutingCandidates(
						report.getAdministrativeArea().getId(), now,
						CoverageStatus.ACTIVE, ControlCenterStatus.ACTIVE, CoverageRole.PRIMARY
				).stream()
				.filter(coverage -> !coverage.getControlCenter().getId().equals(currentCenterId))
				.filter(coverage -> hasOnDutyOperator(coverage.getControlCenter().getId()))
				.findFirst();
		if (target.isEmpty()) {
			notifyStalledOnce(report);
			return false;
		}

		ControlCenter previousCenter = currentRoute.getControlCenter();
		currentRoute.markRerouted(now);
		ReportCenterRoute newRoute = routeRepository.save(ReportCenterRoute.reroute(
				currentRoute, target.get().getControlCenter(), ReportCenterRouteType.FAILOVER,
				target.get().getPriority(),
				"AUTO_FAILOVER: 근무중 담당자 없음(centerId=" + previousCenter.getId() + ")", now
		));
		// 제보 상태는 RECEIVED 그대로라 상태 이력은 남기지 않는다. 이관 사유와 체인은 라우트가 가진다.
		assignmentService.assignAutomatically(report, newRoute);
		return true;
	}

	private ControlCenterArea resolveTarget(Report report, ReportCenterRoute currentRoute,
			Long targetControlCenterId) {
		if (report.getAdministrativeArea() == null) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "행정구역이 확정되지 않은 제보는 이관할 수 없습니다.");
		}
		if (targetControlCenterId.equals(currentRoute.getControlCenter().getId())) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "현재 관제센터로는 이관할 수 없습니다.");
		}
		// 거쳐온 센터라도 근무자가 복귀했으면 되돌릴 수 있어야 한다. 근무자 없는 거쳐온
		// 센터로의 이관만 막아 의미 없는 왕복을 차단한다.
		if (visitedCenterIds(currentRoute).contains(targetControlCenterId)
				&& !hasOnDutyOperator(targetControlCenterId)) {
			throw new BusinessException(CommonErrorCode.CONFLICT,
					"이미 거쳐온 관제센터는 근무중 담당자가 있을 때만 다시 이관할 수 있습니다.");
		}
		return controlCenterAreaRepository.findRoutingCandidates(
						report.getAdministrativeArea().getId(), clock.instant(),
						CoverageStatus.ACTIVE, ControlCenterStatus.ACTIVE, CoverageRole.PRIMARY
				).stream()
				.filter(coverage -> coverage.getControlCenter().getId().equals(targetControlCenterId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE,
						"해당 행정구역을 담당하지 않는 관제센터입니다."));
	}

	/** 현재 라우트부터 previousRoute 체인을 거슬러 거쳐온 센터를 모은다(현재 센터 포함). */
	private Set<Long> visitedCenterIds(ReportCenterRoute currentRoute) {
		Set<Long> centerIds = new HashSet<>();
		ReportCenterRoute cursor = currentRoute;
		for (int depth = 0; cursor != null && depth < MAX_ROUTE_CHAIN_DEPTH; depth++) {
			centerIds.add(cursor.getControlCenter().getId());
			cursor = cursor.getPreviousRoute();
		}
		return centerIds;
	}

	/** previousRoute 체인 길이. 자동 이관 연쇄의 폭주를 막는 상한 검사에 쓴다. */
	private int routeChainDepth(ReportCenterRoute currentRoute) {
		int depth = 0;
		ReportCenterRoute cursor = currentRoute;
		while (cursor != null && depth < MAX_ROUTE_CHAIN_DEPTH) {
			depth++;
			cursor = cursor.getPreviousRoute();
		}
		return depth;
	}

	private boolean hasOnDutyOperator(Long controlCenterId) {
		List<Long> operatorIds = membershipRepository.findEligibleOperatorsForAssignment(
						controlCenterId, MembershipStatus.ACTIVE, UserRole.OPERATOR, UserStatus.ACTIVE
				).stream()
				.map(membership -> membership.getUser().getId())
				.toList();
		if (operatorIds.isEmpty()) return false;
		return presenceRepository.findAllByOperatorIdIn(operatorIds).stream()
				.anyMatch(OperatorPresence::isOnDuty);
	}

	private void notifyStalledOnce(Report report) {
		if (realtimeEventRepository.existsByReportIdAndEventType(
				report.getId(), RealtimeEventType.REPORT_ASSIGNMENT_STALLED)) {
			return;
		}
		for (User admin : userRepository.findByRoleIn(
				List.of(UserRole.SUPER_ADMIN), PageRequest.of(0, STALL_NOTIFY_ADMIN_LIMIT))) {
			realtimeEventService.record(admin.getId(),
					RealtimeEventType.REPORT_ASSIGNMENT_STALLED, report.getId(), null, null);
		}
	}

	private void validateActiveMembership(Long operatorId, Long centerId) {
		boolean valid = membershipRepository.findByUserIdAndIsPrimaryTrue(operatorId)
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.filter(membership -> membership.getControlCenterId().equals(centerId))
				.isPresent();
		if (!valid) throw new BusinessException(CommonErrorCode.CONFLICT, "현재 관제센터 소속 담당자가 아닙니다.");
	}
}
