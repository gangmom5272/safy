package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRole;
import com.ojimasam.safy.user.domain.UserStatus;

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
	private final Clock clock;

	@Autowired
	public ReportOperatorAssignmentService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			AdminCenterMembershipRepository membershipRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository
	) {
		this(reportRepository, routeRepository, membershipRepository, assignmentRepository, historyRepository,
				Clock.systemUTC());
	}

	ReportOperatorAssignmentService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			AdminCenterMembershipRepository membershipRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.routeRepository = routeRepository;
		this.membershipRepository = membershipRepository;
		this.assignmentRepository = assignmentRepository;
		this.historyRepository = historyRepository;
		this.clock = clock;
	}

	public Optional<ReportOperatorAssignment> assignAutomatically(Report report, ReportCenterRoute route) {
		if (report.getStatus() != ReportStatus.RECEIVED) return Optional.empty();
		if (assignmentRepository.existsByReport_IdAndStatusIn(report.getId(), ACTIVE_STATUSES)) {
			return Optional.empty();
		}
		List<AdminCenterMembership> memberships = membershipRepository.findEligibleOperatorsForAssignment(
				route.getControlCenter().getId(),
				MembershipStatus.ACTIVE,
				UserRole.OPERATOR,
				UserStatus.ACTIVE
		);
		Optional<User> selected = memberships.stream()
				.map(AdminCenterMembership::getUser)
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
				com.ojimasam.safy.report.domain.ReportRoutingStatus.ROUTED,
				ReportStatus.RECEIVED,
				List.copyOf(ACTIVE_STATUSES),
				PageRequest.of(0, batchSize)
		);
	}

	@Transactional(readOnly = true)
	public Page<OperatorAssignmentResponse> listMine(Long operatorId, Pageable pageable) {
		return assignmentRepository.findAllByOperator_IdAndStatusIn(operatorId, ACTIVE_STATUSES, pageable)
				.map(OperatorAssignmentResponse::from);
	}

	@Transactional
	public OperatorAssignmentResponse accept(Long operatorId, Long reportId) {
		return transition(operatorId, reportId, Action.ACCEPT);
	}

	@Transactional
	public OperatorAssignmentResponse start(Long operatorId, Long reportId) {
		return transition(operatorId, reportId, Action.START);
	}

	@Transactional
	public OperatorAssignmentResponse complete(Long operatorId, Long reportId) {
		return transition(operatorId, reportId, Action.COMPLETE);
	}

	private OperatorAssignmentResponse transition(Long operatorId, Long reportId, Action action) {
		Report report = reportRepository.findByIdForUpdate(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		ReportOperatorAssignment assignment = assignmentRepository
				.findByReport_IdAndStatusIn(reportId, ACTIVE_STATUSES)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (!assignment.getOperator().getId().equals(operatorId)) {
			throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
		}
		validateActiveMembership(operatorId, assignment.getRoute().getControlCenter().getId());

		Instant now = clock.instant();
		ReportStatus previous = report.getStatus();
		try {
			switch (action) {
				case ACCEPT -> {
					assignment.accept(operatorId, now);
					report.accept();
				}
				case START -> {
					assignment.start(operatorId, now);
					report.startWork();
				}
				case COMPLETE -> {
					assignment.complete(operatorId, now);
					report.completeWork(now);
					assignment.getRoute().close(now);
				}
			}
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), assignment.getOperator(), action.reason, now
		));
		return OperatorAssignmentResponse.from(assignment);
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

	private enum Action {
		ACCEPT("OPERATOR_ACCEPTED"),
		START("WORK_STARTED"),
		COMPLETE("WORK_COMPLETED");

		private final String reason;

		Action(String reason) {
			this.reason = reason;
		}
	}
}
