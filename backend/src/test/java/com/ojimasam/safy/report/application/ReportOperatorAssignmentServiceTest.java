package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.report.application.dto.OperatorAssignmentResponse;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;
import com.ojimasam.safy.report.application.event.ReportTerminatedEvent;

class ReportOperatorAssignmentServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

	@Test
	void assignsOperatorWithLowestActiveWorkload() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User busy = operator(30L, "바쁜 담당자");
		User idle = operator(31L, "여유 담당자");
		AdminCenterMembership busyMembership = membership(busy);
		AdminCenterMembership idleMembership = membership(idle);
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(
				20L, MembershipStatus.ACTIVE, UserRole.OPERATOR, UserStatus.ACTIVE
		)).thenReturn(List.of(busyMembership, idleMembership));
		when(fixture.assignmentRepository.countByOperator_IdAndStatusIn(any(), any()))
				.thenAnswer(invocation -> invocation.<Long>getArgument(0).equals(30L) ? 3L : 1L);
		when(fixture.assignmentRepository.findTopByOperator_IdOrderByAssignedAtDesc(any()))
				.thenReturn(Optional.empty());
		when(fixture.assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<ReportOperatorAssignment> result = fixture.service.assignAutomatically(report, route);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().getOperator()).isSameAs(idle);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.ASSIGNED);
		ArgumentCaptor<ReportStatusHistory> history = ArgumentCaptor.forClass(ReportStatusHistory.class);
		verify(fixture.historyRepository).save(history.capture());
		assertThat(history.getValue().getFromStatus()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(history.getValue().getToStatus()).isEqualTo(ReportStatus.ASSIGNED);
		verify(fixture.realtimeNotifier).reportAssigned(result.orElseThrow());
	}

	@Test
	void assignedOperatorCompletesWork() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ReflectionTestUtils.setField(report, "reportedAt", NOW.minusSeconds(60));
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "담당자");
		ReportOperatorAssignment assignment = ReportOperatorAssignment.automatic(report, route, operator, NOW);
		AdminCenterMembership membership = activeMembership(operator, center);
		report.assign();
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L))
				.thenReturn(Optional.of(membership));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		fixture.service.complete(30L, 10L);

		assertThat(assignment.getStatus()).isEqualTo(ReportOperatorAssignmentStatus.COMPLETED);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.CLOSED);
		verify(route).close(NOW);
		verify(fixture.realtimeNotifier).reportClosed(10L, report.getReporterId());
		verify(fixture.eventPublisher).publishEvent(any(ReportTerminatedEvent.class));
	}

	@Test
	void reassignReleasesReportAndExcludesRequestingOperator() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "담당자");
		ReportOperatorAssignment assignment = ReportOperatorAssignment.automatic(report, route, operator, NOW);
		AdminCenterMembership membership = activeMembership(operator, center);
		report.assign();
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L))
				.thenReturn(Optional.of(membership));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.routeRepository.findByReport_IdAndStatus(any(), any())).thenReturn(Optional.empty());

		fixture.service.reassign(30L, 10L, "관할 구역 오배정");

		assertThat(assignment.getStatus()).isEqualTo(ReportOperatorAssignmentStatus.CANCELLED);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
	}

	@Test
	void assignsFiveIndependentReportsToOnlyAvailableOperator() {
		Fixture fixture = new Fixture();
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "강남 담당자");
		AdminCenterMembership membership = membership(operator);
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(
				20L, MembershipStatus.ACTIVE, UserRole.OPERATOR, UserStatus.ACTIVE))
				.thenReturn(List.of(membership));
		when(fixture.assignmentRepository.countByOperator_IdAndStatusIn(any(), any())).thenReturn(0L);
		when(fixture.assignmentRepository.findTopByOperator_IdOrderByAssignedAtDesc(any()))
				.thenReturn(Optional.empty());
		when(fixture.assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		for (long reportId = 1L; reportId <= 5L; reportId++) {
			Optional<ReportOperatorAssignment> result = fixture.service.assignAutomatically(report(reportId), route);
			assertThat(result).isPresent();
			assertThat(result.orElseThrow().getOperator()).isSameAs(operator);
		}

		verify(fixture.realtimeNotifier, times(5)).reportAssigned(any(ReportOperatorAssignment.class));
	}

	@Test
	void claimAssignsQueueReportToRequestingOperator() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "담당자");
		AdminCenterMembership membership = activeMembership(operator, center);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.existsByReport_IdAndStatusIn(any(), any())).thenReturn(false);
		when(fixture.routeRepository.findByReport_IdAndStatus(10L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(route));
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L))
				.thenReturn(Optional.of(membership));
		when(fixture.assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OperatorAssignmentResponse response = fixture.service.claim(30L, 10L);

		assertThat(report.getStatus()).isEqualTo(ReportStatus.ASSIGNED);
		assertThat(response.operatorId()).isEqualTo(30L);
		assertThat(response.assignmentStatus()).isEqualTo(ReportOperatorAssignmentStatus.ASSIGNED);
		ArgumentCaptor<ReportStatusHistory> history = ArgumentCaptor.forClass(ReportStatusHistory.class);
		verify(fixture.historyRepository).save(history.capture());
		assertThat(history.getValue().getReason()).isEqualTo("OPERATOR_CLAIMED");
	}

	@Test
	void claimConflictsWhenAnotherOperatorAlreadyTookReport() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		report.assign();
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));

		assertThatThrownBy(() -> fixture.service.claim(30L, 10L))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("이미 다른 담당자가 담당한 제보입니다.");
	}

	@Test
	void claimConflictsWhenOperatorBelongsToDifferentCenter() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ControlCenter otherCenter = center(21L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "담당자");
		AdminCenterMembership membership = activeMembership(operator, otherCenter);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.existsByReport_IdAndStatusIn(any(), any())).thenReturn(false);
		when(fixture.routeRepository.findByReport_IdAndStatus(10L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(route));
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L))
				.thenReturn(Optional.of(membership));

		assertThatThrownBy(() -> fixture.service.claim(30L, 10L))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("현재 관제센터 소속 담당자가 아닙니다.");
	}

	@Test
	void skipsOperatorsWhoAreNotOnDuty() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User offline = operator(30L, "퇴근한 담당자");
		User onDuty = operator(31L, "근무중 담당자");
		AdminCenterMembership offlineMembership = membership(offline);
		AdminCenterMembership onDutyMembership = membership(onDuty);
		List<OperatorPresence> presences = List.of(OperatorPresence.connect(31L, NOW));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(
				20L, MembershipStatus.ACTIVE, UserRole.OPERATOR, UserStatus.ACTIVE
		)).thenReturn(List.of(offlineMembership, onDutyMembership));
		// 30번은 접속 이력이 없고(오프라인 취급) 31번만 근무중이다.
		when(fixture.presenceRepository.findAllByOperatorIdIn(any())).thenReturn(presences);
		when(fixture.assignmentRepository.countByOperator_IdAndStatusIn(any(), any())).thenReturn(0L);
		when(fixture.assignmentRepository.findTopByOperator_IdOrderByAssignedAtDesc(any()))
				.thenReturn(Optional.empty());
		when(fixture.assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<ReportOperatorAssignment> result = fixture.service.assignAutomatically(report, route);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().getOperator()).isSameAs(onDuty);
	}

	@Test
	void returnsEmptyWhenNoOperatorIsOnDuty() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		AdminCenterMembership offlineMembership = membership(operator(30L, "담당자"));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(
				20L, MembershipStatus.ACTIVE, UserRole.OPERATOR, UserStatus.ACTIVE
		)).thenReturn(List.of(offlineMembership));
		when(fixture.presenceRepository.findAllByOperatorIdIn(any())).thenReturn(List.of());

		Optional<ReportOperatorAssignment> result = fixture.service.assignAutomatically(report, route);

		assertThat(result).isEmpty();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
	}

	@Test
	void timedOutOperatorRemainsEligibleForNextAutomaticAssignment() {
		// 무응답 회수(타임아웃·오프라인)는 되돌아옴 방지 대상이 아니다 — 영구 제외하면 후보가 마른다.
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "알림을 놓쳤던 담당자");
		ReportOperatorAssignment timedOut = ReportOperatorAssignment.automatic(report, route, operator, NOW);
		timedOut.cancel(NOW, ReportAssignmentCancelReason.ASSIGNMENT_TIMEOUT);
		AdminCenterMembership membership = membership(operator);
		when(fixture.assignmentRepository.findAllByReport_IdAndStatus(10L,
				ReportOperatorAssignmentStatus.CANCELLED)).thenReturn(List.of(timedOut));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(
				20L, MembershipStatus.ACTIVE, UserRole.OPERATOR, UserStatus.ACTIVE
		)).thenReturn(List.of(membership));
		when(fixture.assignmentRepository.countByOperator_IdAndStatusIn(any(), any())).thenReturn(0L);
		when(fixture.assignmentRepository.findTopByOperator_IdOrderByAssignedAtDesc(any()))
				.thenReturn(Optional.empty());
		when(fixture.assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<ReportOperatorAssignment> result = fixture.service.assignAutomatically(report, route);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().getOperator()).isSameAs(operator);
	}

	@Test
	void voluntarilyReleasedOperatorIsExcludedFromNextAutomaticAssignment() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "재배정을 요청한 담당자");
		ReportOperatorAssignment released = ReportOperatorAssignment.automatic(report, route, operator, NOW);
		released.cancel(NOW, ReportAssignmentCancelReason.REASSIGN_REQUESTED);
		AdminCenterMembership membership = membership(operator);
		when(fixture.assignmentRepository.findAllByReport_IdAndStatus(10L,
				ReportOperatorAssignmentStatus.CANCELLED)).thenReturn(List.of(released));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(
				20L, MembershipStatus.ACTIVE, UserRole.OPERATOR, UserStatus.ACTIVE
		)).thenReturn(List.of(membership));

		Optional<ReportOperatorAssignment> result = fixture.service.assignAutomatically(report, route);

		assertThat(result).isEmpty();
	}

	@Test
	void releaseStaleAssignmentReturnsReportToReceivedAndNotifiesReporter() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "담당자");
		ReportOperatorAssignment assignment = ReportOperatorAssignment.automatic(report, route, operator, NOW);
		report.assign();
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.routeRepository.findByReport_IdAndStatus(any(), any())).thenReturn(Optional.empty());

		fixture.service.releaseStaleAssignment(10L, 30L,
				ReportOperatorAssignmentStatus.ASSIGNED, ReportAssignmentCancelReason.OPERATOR_OFFLINE);

		assertThat(assignment.getStatus()).isEqualTo(ReportOperatorAssignmentStatus.CANCELLED);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
		verify(fixture.realtimeNotifier).reportReassigned(10L, report.getReporterId());
	}

	@Test
	void releaseStaleAssignmentIgnoresAssignmentInDifferentState() {
		Fixture fixture = new Fixture();
		Report report = report(10L);
		ControlCenter center = center(20L);
		ReportCenterRoute route = route(center);
		User operator = operator(30L, "담당자");
		ReportOperatorAssignment assignment = ReportOperatorAssignment.automatic(report, route, operator, NOW);
		report.assign();
		report.beginHandling();
		assignment.beginHandling(30L, NOW);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));

		// 확인(IN_PROGRESS)까지 간 배정은 ASSIGNED 회수 대상이 아니다.
		fixture.service.releaseStaleAssignment(10L, 30L,
				ReportOperatorAssignmentStatus.ASSIGNED, ReportAssignmentCancelReason.ASSIGNMENT_TIMEOUT);

		assertThat(assignment.getStatus()).isEqualTo(ReportOperatorAssignmentStatus.IN_PROGRESS);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
	}

	private static final class Fixture {
		private final ReportRepository reportRepository = mock(ReportRepository.class);
		private final ReportCenterRouteRepository routeRepository = mock(ReportCenterRouteRepository.class);
		private final AdminCenterMembershipRepository membershipRepository =
				mock(AdminCenterMembershipRepository.class);
		private final ReportOperatorAssignmentRepository assignmentRepository =
				mock(ReportOperatorAssignmentRepository.class);
		private final ReportStatusHistoryRepository historyRepository = mock(ReportStatusHistoryRepository.class);
		private final OperatorPresenceRepository presenceRepository = mock(OperatorPresenceRepository.class);
		private final ReportRealtimeNotifier realtimeNotifier = mock(ReportRealtimeNotifier.class);
		private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		private final ReportOperatorAssignmentService service = new ReportOperatorAssignmentService(
				reportRepository,
				routeRepository,
				membershipRepository,
				assignmentRepository,
				historyRepository,
				presenceRepository,
				realtimeNotifier,
				eventPublisher,
				Clock.fixed(NOW, ZoneOffset.UTC)
		);

		private Fixture() {
			// 별도 지정이 없으면 모든 후보를 근무중으로 취급해 기존 시나리오를 유지한다.
			when(presenceRepository.findAllByOperatorIdIn(any())).thenAnswer(invocation -> {
				java.util.Collection<Long> operatorIds = invocation.getArgument(0);
				if (operatorIds == null) return List.<OperatorPresence>of();
				return operatorIds.stream()
						.map(operatorId -> OperatorPresence.connect(operatorId, NOW))
						.toList();
			});
		}
	}

	private Report report(Long id) {
		Report report = Report.create(
				1L, com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
				com.ojimasam.safy.report.domain.type.ReportCategory.FLOOD_WEATHER,
				com.ojimasam.safy.report.domain.type.ReportSubcategory.ROAD_FLOODING, "침수 제보",
				new BigDecimal("37.5"), new BigDecimal("127.0"), "서울"
		);
		ReflectionTestUtils.setField(report, "id", id);
		return report;
	}

	private ControlCenter center(Long id) {
		ControlCenter center = mock(ControlCenter.class);
		when(center.getId()).thenReturn(id);
		when(center.getName()).thenReturn("중구 재난안전상황실");
		return center;
	}

	private ReportCenterRoute route(ControlCenter center) {
		ReportCenterRoute route = mock(ReportCenterRoute.class);
		when(route.getControlCenter()).thenReturn(center);
		return route;
	}

	private User operator(Long id, String name) {
		User operator = mock(User.class);
		when(operator.getId()).thenReturn(id);
		when(operator.getDisplayName()).thenReturn(name);
		return operator;
	}

	private AdminCenterMembership membership(User operator) {
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		when(membership.getUser()).thenReturn(operator);
		return membership;
	}

	private AdminCenterMembership activeMembership(User operator, ControlCenter center) {
		AdminCenterMembership membership = membership(operator);
		Long centerId = center.getId();
		when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
		when(membership.getControlCenterId()).thenReturn(centerId);
		return membership;
	}
}
