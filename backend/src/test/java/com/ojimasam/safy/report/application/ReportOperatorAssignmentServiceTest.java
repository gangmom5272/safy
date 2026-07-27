package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ojimasam.safy.admin.domain.MembershipStatus;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
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

	private static final class Fixture {
		private final ReportRepository reportRepository = mock(ReportRepository.class);
		private final ReportCenterRouteRepository routeRepository = mock(ReportCenterRouteRepository.class);
		private final AdminCenterMembershipRepository membershipRepository =
				mock(AdminCenterMembershipRepository.class);
		private final ReportOperatorAssignmentRepository assignmentRepository =
				mock(ReportOperatorAssignmentRepository.class);
		private final ReportStatusHistoryRepository historyRepository = mock(ReportStatusHistoryRepository.class);
		private final ReportRealtimeNotifier realtimeNotifier = mock(ReportRealtimeNotifier.class);
		private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		private final ReportOperatorAssignmentService service = new ReportOperatorAssignmentService(
				reportRepository,
				routeRepository,
				membershipRepository,
				assignmentRepository,
				historyRepository,
				realtimeNotifier,
				eventPublisher,
				Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	private Report report(Long id) {
		Report report = Report.create(
				1L, com.ojimasam.safy.report.domain.ReportType.IMAGE,
				com.ojimasam.safy.report.domain.ReportCategory.FLOOD_WEATHER,
				com.ojimasam.safy.report.domain.ReportSubcategory.ROAD_FLOODING, "침수 제보",
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
