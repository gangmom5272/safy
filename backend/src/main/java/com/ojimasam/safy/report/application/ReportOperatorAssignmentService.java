package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
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
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
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
			ReportOperatorAssignmentStatus.ACCEPTED,
			ReportOperatorAssignmentStatus.IN_PROGRESS
	);

	private final ReportRepository reportRepository;
	private final ReportCenterRouteRepository routeRepository;
	private final AdminCenterMembershipRepository membershipRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportStatusHistoryRepository historyRepository;
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
			ReportRealtimeNotifier realtimeNotifier,
			ApplicationEventPublisher eventPublisher
	) {
		this(reportRepository, routeRepository, membershipRepository, assignmentRepository, historyRepository,
				realtimeNotifier, eventPublisher, Clock.systemUTC());
	}

	ReportOperatorAssignmentService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			AdminCenterMembershipRepository membershipRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			ReportRealtimeNotifier realtimeNotifier,
			ApplicationEventPublisher eventPublisher,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.routeRepository = routeRepository;
		this.membershipRepository = membershipRepository;
		this.assignmentRepository = assignmentRepository;
		this.historyRepository = historyRepository;
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
		if (assignmentRepository.existsByReport_IdAndStatusIn(report.getId(), ACTIVE_STATUSES)) {
			return Optional.empty();
		}
		// 이 제보를 이미 놓아준(재배정으로 CANCELLED된) 담당자는 즉시·스케줄러 경로 모두에서 제외해 되돌아옴 방지
		Set<Long> excludedOperatorIds = new HashSet<>();
		if (excludeOperatorId != null) excludedOperatorIds.add(excludeOperatorId);
		assignmentRepository.findAllByReport_IdAndStatus(report.getId(), ReportOperatorAssignmentStatus.CANCELLED)
				.forEach(cancelled -> excludedOperatorIds.add(cancelled.getOperator().getId()));

		List<AdminCenterMembership> memberships = membershipRepository.findEligibleOperatorsForAssignment(
				route.getControlCenter().getId(),
				MembershipStatus.ACTIVE,
				UserRole.OPERATOR,
				UserStatus.ACTIVE
		);
		Optional<User> selected = memberships.stream()
				.map(AdminCenterMembership::getUser)
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
		return assignmentRepository.findAllByOperator_IdAndStatusIn(operatorId, targetStatuses, pageable)
				.map(OperatorAssignmentResponse::from);
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
			assignment.cancel(now);
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
			assignment.cancel(now);
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
