package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.analysis.application.dto.RiskSummaryView;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;
import com.ojimasam.safy.report.application.dto.OperatorAssignmentResponse;
import com.ojimasam.safy.report.application.event.ReportTerminatedEvent;

@Service
public class ReportOperatorAssignmentService {
	private static final Set<ReportOperatorAssignmentStatus> ACTIVE_STATUSES = Set.of(
			ReportOperatorAssignmentStatus.ASSIGNED,
			ReportOperatorAssignmentStatus.IN_PROGRESS
	);

	private final ReportRepository reportRepository;
	private final ReportCenterRouteRepository routeRepository;
	private final AdminCenterMembershipRepository membershipRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportStatusHistoryRepository historyRepository;
	private final OperatorPresenceRepository presenceRepository;
	private final RiskAssessmentRepository riskAssessmentRepository;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public ReportOperatorAssignmentService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			AdminCenterMembershipRepository membershipRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			OperatorPresenceRepository presenceRepository,
			RiskAssessmentRepository riskAssessmentRepository,
			ReportRealtimeNotifier realtimeNotifier,
			ApplicationEventPublisher eventPublisher
	) {
		this(reportRepository, routeRepository, membershipRepository, assignmentRepository, historyRepository,
				presenceRepository, riskAssessmentRepository, realtimeNotifier, eventPublisher, Clock.systemUTC());
	}

	ReportOperatorAssignmentService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			AdminCenterMembershipRepository membershipRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			OperatorPresenceRepository presenceRepository,
			RiskAssessmentRepository riskAssessmentRepository,
			ReportRealtimeNotifier realtimeNotifier,
			ApplicationEventPublisher eventPublisher,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.routeRepository = routeRepository;
		this.membershipRepository = membershipRepository;
		this.assignmentRepository = assignmentRepository;
		this.historyRepository = historyRepository;
		this.presenceRepository = presenceRepository;
		this.riskAssessmentRepository = riskAssessmentRepository;
		this.realtimeNotifier = realtimeNotifier;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	public Optional<ReportOperatorAssignment> assignAutomatically(Report report, ReportCenterRoute route) {
		return assignAutomatically(report, route, null);
	}

	public Optional<ReportOperatorAssignment> assignAutomatically(Report report, ReportCenterRoute route,
			Long excludeOperatorId) {
		if (report.getStatus() != ReportStatus.RECEIVED) return Optional.empty();
		// 이 조회는 중복 배정을 막는 것 외에 순서도 보장한다. 호출한 쪽(재배정·이관)이 직전에
		// 옛 배정을 CANCELLED 로 바꿔둔 상태인데, 배정 id 가 IDENTITY 라 아래 save() 가 INSERT 를
		// 그 자리에서 실행한다. 이 조회가 같은 테이블을 건드려 대기 중인 UPDATE 를 먼저 밀어내지
		// 않으면, 옛 배정이 아직 활성인 채로 새 배정이 들어가
		// uq_report_operator_assignments_active_report 위반이 난다.
		// 이 조회를 지우거나 뒤로 옮기려면 그 자리에 명시적 flush 가 필요하다.
		if (assignmentRepository.existsByReport_IdAndStatusIn(report.getId(), ACTIVE_STATUSES)) {
			return Optional.empty();
		}
		// 스스로 놓아준(재배정 요청·이관) 담당자만 되돌아옴 방지로 제외한다. 무응답 회수(타임아웃·
		// 오프라인)까지 영구 제외하면 후보가 말라 배정이 조용히 죽으므로, 그쪽은 excludeOperatorId 로
		// 직후 재배정만 피한다 — 같은 담당자에게 다시 가는 것이 아무에게도 안 가는 것보다 낫다.
		Set<Long> excludedOperatorIds = new HashSet<>();
		if (excludeOperatorId != null) excludedOperatorIds.add(excludeOperatorId);
		assignmentRepository.findAllByReport_IdAndStatus(report.getId(), ReportOperatorAssignmentStatus.CANCELLED)
				.stream()
				.filter(cancelled -> cancelled.getCancelReason() != null
						&& cancelled.getCancelReason().isVoluntaryRelease())
				.forEach(cancelled -> excludedOperatorIds.add(cancelled.getOperator().getId()));

		List<AdminCenterMembership> memberships = membershipRepository.findEligibleOperatorsForAssignment(
				route.getControlCenter().getId(),
				MembershipStatus.ACTIVE,
				UserRole.OPERATOR,
				UserStatus.ACTIVE
		);
		// 자동 배정은 근무중(ON_DUTY)인 담당자에게만 간다. 접속 이력이 없으면 오프라인으로 본다.
		Set<Long> onDutyOperatorIds = onDutyOperatorIds(memberships);
		Optional<User> selected = memberships.stream()
				.map(AdminCenterMembership::getUser)
				.filter(operator -> onDutyOperatorIds.contains(operator.getId()))
				.filter(operator -> !excludedOperatorIds.contains(operator.getId()))
				.map(this::candidate)
				.min(Comparator.comparingLong(OperatorCandidate::activeWorkload)
						.thenComparing(OperatorCandidate::lastAssignedAt,
								Comparator.nullsFirst(Comparator.naturalOrder()))
						.thenComparing(candidate -> candidate.operator().getId()))
				.map(OperatorCandidate::operator);
		if (selected.isEmpty()) return Optional.empty();

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		report.assign();
		ReportOperatorAssignment assignment = assignmentRepository.save(
				ReportOperatorAssignment.automatic(report, route, selected.get(), now)
		);
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), null, "AUTO_OPERATOR_ASSIGNMENT", now
		));
		realtimeNotifier.reportAssigned(assignment);
		return Optional.of(assignment);
	}

	@Transactional
	public Optional<ReportOperatorAssignment> retryAutomaticAssignment(Long reportId) {
		Report report = reportRepository.findByIdForUpdate(reportId).orElse(null);
		if (report == null || report.getStatus() != ReportStatus.RECEIVED) return Optional.empty();
		ReportCenterRoute route = routeRepository
				.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.orElse(null);
		return route == null ? Optional.empty() : assignAutomatically(report, route);
	}

	@Transactional(readOnly = true)
	public List<Long> findUnassignedReportIds(int batchSize) {
		return reportRepository.findRoutedUnassignedIds(
				com.ojimasam.safy.report.domain.type.ReportRoutingStatus.ROUTED,
				ReportStatus.RECEIVED,
				List.copyOf(ACTIVE_STATUSES),
				PageRequest.of(0, batchSize)
		);
	}

	@Transactional(readOnly = true)
	/**
	 * 담당자가 맡은 제보 목록. statuses 를 주지 않으면 종결 건까지 포함한 전체 이력을 돌려준다.
	 */
	public Page<OperatorAssignmentResponse> listMine(Long operatorId,
			Collection<ReportOperatorAssignmentStatus> statuses, Pageable pageable) {
		Collection<ReportOperatorAssignmentStatus> targetStatuses =
				statuses == null || statuses.isEmpty()
						? EnumSet.allOf(ReportOperatorAssignmentStatus.class)
						: statuses;
		Page<ReportOperatorAssignment> assignments =
				assignmentRepository.findAllByOperator_IdAndStatusIn(operatorId, targetStatuses, pageable);
		Map<Long, RiskSummaryView> risksByReportId = risksByReportId(assignments.getContent().stream()
				.map(assignment -> assignment.getReport().getId())
				.toList());
		return assignments.map(assignment -> OperatorAssignmentResponse.from(
				assignment, risksByReportId.get(assignment.getReport().getId())));
	}

	private Map<Long, RiskSummaryView> risksByReportId(List<Long> reportIds) {
		if (reportIds.isEmpty()) return Map.of();
		Map<Long, RiskSummaryView> result = new HashMap<>();
		riskAssessmentRepository.findAllByReportIdIn(reportIds)
				.forEach(assessment -> result.put(assessment.getReportId(), RiskSummaryView.from(assessment)));
		return result;
	}

	/**
	 * 담당자가 제보를 확인(타일 열람·라이브 시청)한 시점의 자동 확인.
	 * 별도 트랜잭션에서 멱등하게 ASSIGNED → IN_PROGRESS로 전이한다. 조건 불충족 시 조용히 무시.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markHandling(Long operatorId, Long reportId) {
		Report report = reportRepository.findByIdForUpdate(reportId).orElse(null);
		if (report == null || report.getStatus() != ReportStatus.ASSIGNED) return;
		ReportOperatorAssignment assignment = assignmentRepository
				.findByReport_IdAndStatusIn(reportId, ACTIVE_STATUSES).orElse(null);
		if (assignment == null || !assignment.getOperator().getId().equals(operatorId)) return;

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		report.beginHandling();
		assignment.beginHandling(operatorId, now);
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), assignment.getOperator(), "OPERATOR_VIEWING", now
		));
		realtimeNotifier.reportInProgress(report.getId(), report.getReporterId());
	}

	@Transactional
	public OperatorAssignmentResponse complete(Long operatorId, Long reportId) {
		Report report = reportForUpdate(reportId);
		ReportOperatorAssignment assignment = requireOwnedActiveAssignment(operatorId, reportId);
		validateActiveMembership(operatorId, assignment.getRoute().getControlCenter().getId());

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		try {
			report.completeWork(now);
			assignment.complete(operatorId, now);
			assignment.getRoute().close(now);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), assignment.getOperator(), "WORK_COMPLETED", now
		));
		realtimeNotifier.reportClosed(report.getId(), report.getReporterId());
		eventPublisher.publishEvent(new ReportTerminatedEvent(report.getId(), report.getReporterId(), report.getStatus()));
		return OperatorAssignmentResponse.from(assignment);
	}

	/** 무효 제보 반려(종결). 라이브가 있으면 종료 이벤트로 세션을 닫고 제보자에게 통지한다. */
	@Transactional
	public OperatorAssignmentResponse reject(Long operatorId, Long reportId, String reason) {
		Report report = reportForUpdate(reportId);
		ReportOperatorAssignment assignment = requireOwnedActiveAssignment(operatorId, reportId);
		validateActiveMembership(operatorId, assignment.getRoute().getControlCenter().getId());

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		try {
			assignment.cancel(now, ReportAssignmentCancelReason.REPORT_REJECTED, reason);
			report.reject(now);
			assignment.getRoute().close(now);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), assignment.getOperator(), reason, now
		));
		realtimeNotifier.reportRejected(report.getId(), report.getReporterId());
		eventPublisher.publishEvent(new ReportTerminatedEvent(report.getId(), report.getReporterId(), report.getStatus()));
		return OperatorAssignmentResponse.from(assignment);
	}

	/**
	 * 담당자가 처리를 놓고 다른 담당자에게 재배정을 요청(제보는 살아있음).
	 * 현재 배정을 취소하고 접수 상태로 되돌린 뒤, 같은 관제센터에서 요청자를 제외하고 즉시 재배정을 시도한다.
	 * 후보가 없으면 미배정 재시도 스케줄러가 이후 다시 배정한다. 라이브 송출은 유지된다.
	 */
	@Transactional
	public OperatorAssignmentResponse reassign(Long operatorId, Long reportId, String reason) {
		Report report = reportForUpdate(reportId);
		ReportOperatorAssignment assignment = requireOwnedActiveAssignment(operatorId, reportId);
		validateActiveMembership(operatorId, assignment.getRoute().getControlCenter().getId());

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		try {
			assignment.cancel(now, ReportAssignmentCancelReason.REASSIGN_REQUESTED, reason);
			report.releaseForReassignment();
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), assignment.getOperator(), reason, now
		));
		realtimeNotifier.reportReassigned(report.getId(), report.getReporterId());
		routeRepository.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.ifPresent(route -> assignAutomatically(report, route, operatorId));
		return OperatorAssignmentResponse.from(assignment);
	}

	/**
	 * 공용 큐에서 담당자가 직접 담당(claim)한다. 자동 배정과 달리 본인이 손을 든 것이므로
	 * 근무 상태나 과거에 이 제보를 놓아준 이력(제외 목록)을 따지지 않는다.
	 * 동시에 두 명이 누르면 제보 잠금으로 한 명만 통과하고 나머지는 409를 받는다.
	 */
	@Transactional
	public OperatorAssignmentResponse claim(Long operatorId, Long reportId) {
		Report report = reportForUpdate(reportId);
		if (report.getStatus() != ReportStatus.RECEIVED
				|| assignmentRepository.existsByReport_IdAndStatusIn(reportId, ACTIVE_STATUSES)) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "이미 다른 담당자가 담당한 제보입니다.");
		}
		ReportCenterRoute route = routeRepository
				.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.CONFLICT, "배정 가능한 관제센터 라우팅이 없습니다."));
		AdminCenterMembership membership = membershipRepository.findByUserIdAndIsPrimaryTrue(operatorId)
				.filter(found -> found.getStatus() == MembershipStatus.ACTIVE)
				.filter(found -> found.getControlCenterId().equals(route.getControlCenter().getId()))
				.orElseThrow(() -> new BusinessException(CommonErrorCode.CONFLICT, "현재 관제센터 소속 담당자가 아닙니다."));

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		report.assign();
		ReportOperatorAssignment assignment = assignmentRepository.save(
				ReportOperatorAssignment.claimed(report, route, membership.getUser(), now)
		);
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), membership.getUser(), "OPERATOR_CLAIMED", now
		));
		// 큐에서 손을 든 것 자체가 접속 신호다. 오프라인 상태로 남아 있으면 스위퍼가
		// 방금 담당한 건까지 회수하므로, 근무중으로 복귀시켜 둔다.
		presenceRepository.findByOperatorId(operatorId)
				.ifPresent(presence -> presence.markConnected(now));
		return OperatorAssignmentResponse.from(assignment);
	}

	/**
	 * 담당자 무응답(오프라인 전환·미확인 방치)으로 배정을 회수해 재배정 대상으로 되돌린다.
	 * 별도 트랜잭션에서 제보를 잠근 뒤 현재 상태를 다시 확인하므로,
	 * 그 사이 처리·종결된 제보는 건드리지 않는다. 멱등.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void releaseStaleAssignment(Long reportId, Long operatorId,
			ReportOperatorAssignmentStatus expectedStatus, ReportAssignmentCancelReason reason) {
		Report report = reportRepository.findByIdForUpdate(reportId).orElse(null);
		if (report == null) return;
		ReportOperatorAssignment assignment = assignmentRepository
				.findByReport_IdAndStatusIn(reportId, ACTIVE_STATUSES).orElse(null);
		if (assignment == null || assignment.getStatus() != expectedStatus) return;
		if (!assignment.getOperator().getId().equals(operatorId)) return;

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		try {
			report.releaseForReassignment();
			assignment.cancel(now, reason);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return;
		}
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), assignment.getOperator(), reason.name(), now
		));
		realtimeNotifier.reportReassigned(report.getId(), report.getReporterId());
		routeRepository.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.ifPresent(route -> assignAutomatically(report, route, operatorId));
	}

	private Set<Long> onDutyOperatorIds(List<AdminCenterMembership> memberships) {
		List<Long> operatorIds = memberships.stream()
				.map(membership -> membership.getUser().getId())
				.toList();
		if (operatorIds.isEmpty()) return Set.of();
		Set<Long> onDuty = new HashSet<>();
		for (OperatorPresence presence : presenceRepository.findAllByOperatorIdIn(operatorIds)) {
			if (presence.isOnDuty()) onDuty.add(presence.getOperatorId());
		}
		return onDuty;
	}

	private Report reportForUpdate(Long reportId) {
		return reportRepository.findByIdForUpdate(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}

	private ReportOperatorAssignment requireOwnedActiveAssignment(Long operatorId, Long reportId) {
		ReportOperatorAssignment assignment = assignmentRepository
				.findByReport_IdAndStatusIn(reportId, ACTIVE_STATUSES)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (!assignment.getOperator().getId().equals(operatorId)) {
			throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
		}
		return assignment;
	}

	private void validateActiveMembership(Long operatorId, Long centerId) {
		boolean valid = membershipRepository.findByUserIdAndIsPrimaryTrue(operatorId)
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.filter(membership -> membership.getControlCenterId().equals(centerId))
				.isPresent();
		if (!valid) throw new BusinessException(CommonErrorCode.CONFLICT, "현재 관제센터 소속 담당자가 아닙니다.");
	}

	private OperatorCandidate candidate(User operator) {
		long activeWorkload = assignmentRepository.countByOperator_IdAndStatusIn(operator.getId(), ACTIVE_STATUSES);
		Instant lastAssignedAt = assignmentRepository.findTopByOperator_IdOrderByAssignedAtDesc(operator.getId())
				.map(ReportOperatorAssignment::getAssignedAt)
				.orElse(null);
		return new OperatorCandidate(operator, activeWorkload, lastAssignedAt);
	}

	private record OperatorCandidate(User operator, long activeWorkload, Instant lastAssignedAt) {
	}
}
