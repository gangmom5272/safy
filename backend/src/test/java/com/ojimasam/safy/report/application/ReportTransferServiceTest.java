package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenterAreaRepository;
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
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteType;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;

class ReportTransferServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Test
	void manualTransferMovesReportToTargetCenterAndKeepsChain() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ControlCenter centerB = center(21L, "서초");
		ReportCenterRoute currentRoute = ReportCenterRoute.createInitial(report, centerA, 1, "AUTO", NOW.minusSeconds(60));
		User operator = operator(30L);
		ReportOperatorAssignment assignment =
				ReportOperatorAssignment.automatic(report, currentRoute, operator, NOW.minusSeconds(30));
		report.assign();
		AdminCenterMembership membership = activeMembership(operator, 20L);
		ControlCenterArea currentCoverage = coverage(centerA, 1);
		ControlCenterArea targetCoverage = coverage(centerB, 2);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		when(fixture.controlCenterAreaRepository.findRoutingCandidates(any(), any(), any(), any(), any()))
				.thenReturn(List.of(currentCoverage, targetCoverage));
		when(fixture.routeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.assignmentService.assignAutomatically(eq(report), any(), eq(30L)))
				.thenReturn(Optional.empty());

		ReportTransferResponse response = fixture.service.transferManually(30L, 10L, 21L, "센터 마비로 이관");

		assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(assignment.getStatus()).isEqualTo(ReportOperatorAssignmentStatus.CANCELLED);
		assertThat(currentRoute.getStatus()).isEqualTo(ReportCenterRouteStatus.REROUTED);
		ArgumentCaptor<ReportCenterRoute> savedRoute = ArgumentCaptor.forClass(ReportCenterRoute.class);
		verify(fixture.routeRepository).save(savedRoute.capture());
		assertThat(savedRoute.getValue().getRouteType()).isEqualTo(ReportCenterRouteType.MANUAL);
		assertThat(savedRoute.getValue().getPreviousRoute()).isSameAs(currentRoute);
		assertThat(savedRoute.getValue().getControlCenter()).isSameAs(centerB);
		assertThat(response.controlCenterId()).isEqualTo(21L);
		assertThat(response.assigned()).isFalse();
		verify(fixture.realtimeNotifier).reportReassigned(10L, report.getReporterId());
	}

	@Test
	void manualTransferRejectsVisitedCenterWithoutOnDutyOperator() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ControlCenter centerB = center(21L, "서초");
		// B → A 로 이미 한 번 이관된 체인: B에 근무중 담당자가 없으면 되돌릴 수 없다.
		ReportCenterRoute firstRoute = ReportCenterRoute.createInitial(report, centerB, 1, "AUTO", NOW.minusSeconds(120));
		ReportCenterRoute currentRoute = ReportCenterRoute.reroute(
				firstRoute, centerA, ReportCenterRouteType.MANUAL, 1, "이관", NOW.minusSeconds(60));
		User operator = operator(30L);
		ReportOperatorAssignment assignment =
				ReportOperatorAssignment.automatic(report, currentRoute, operator, NOW.minusSeconds(30));
		report.assign();
		AdminCenterMembership membership = activeMembership(operator, 20L);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(21L), any(), any(), any()))
				.thenReturn(List.of());

		assertThatThrownBy(() -> fixture.service.transferManually(30L, 10L, 21L, "다시 서초로"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("이미 거쳐온 관제센터");
	}

	@Test
	void manualTransferAllowsVisitedCenterWithOnDutyOperator() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ControlCenter centerB = center(21L, "서초");
		// B → A 이관 뒤 B 근무자가 복귀했다면 B로 되돌릴 수 있어야 한다(영구 차단 시 교착).
		ReportCenterRoute firstRoute = ReportCenterRoute.createInitial(report, centerB, 1, "AUTO", NOW.minusSeconds(120));
		ReportCenterRoute currentRoute = ReportCenterRoute.reroute(
				firstRoute, centerA, ReportCenterRouteType.MANUAL, 1, "이관", NOW.minusSeconds(60));
		User operator = operator(30L);
		ReportOperatorAssignment assignment =
				ReportOperatorAssignment.automatic(report, currentRoute, operator, NOW.minusSeconds(30));
		report.assign();
		AdminCenterMembership membership = activeMembership(operator, 20L);
		AdminCenterMembership membershipB = activeMembership(operator(40L), 21L);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		ControlCenterArea currentCoverage = coverage(centerA, 1);
		ControlCenterArea targetCoverage = coverage(centerB, 2);
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(21L), any(), any(), any()))
				.thenReturn(List.of(membershipB));
		when(fixture.presenceRepository.findAllByOperatorIdIn(List.of(40L)))
				.thenReturn(List.of(OperatorPresence.connect(40L, NOW)));
		when(fixture.controlCenterAreaRepository.findRoutingCandidates(any(), any(), any(), any(), any()))
				.thenReturn(List.of(currentCoverage, targetCoverage));
		when(fixture.routeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fixture.assignmentService.assignAutomatically(eq(report), any(), eq(30L)))
				.thenReturn(Optional.empty());

		ReportTransferResponse response = fixture.service.transferManually(30L, 10L, 21L, "서초 근무자 복귀");

		assertThat(response.controlCenterId()).isEqualTo(21L);
		assertThat(currentRoute.getStatus()).isEqualTo(ReportCenterRouteStatus.REROUTED);
	}

	@Test
	void manualTransferRejectsCenterNotCoveringArea() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ReportCenterRoute currentRoute = ReportCenterRoute.createInitial(report, centerA, 1, "AUTO", NOW.minusSeconds(60));
		User operator = operator(30L);
		ReportOperatorAssignment assignment =
				ReportOperatorAssignment.automatic(report, currentRoute, operator, NOW.minusSeconds(30));
		report.assign();
		AdminCenterMembership membership = activeMembership(operator, 20L);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.findByReport_IdAndStatusIn(any(), any()))
				.thenReturn(Optional.of(assignment));
		ControlCenterArea currentCoverage = coverage(centerA, 1);
		when(fixture.membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		when(fixture.controlCenterAreaRepository.findRoutingCandidates(any(), any(), any(), any(), any()))
				.thenReturn(List.of(currentCoverage));

		assertThatThrownBy(() -> fixture.service.transferManually(30L, 10L, 99L, "등록 안 된 센터로"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("담당하지 않는 관제센터");
	}

	@Test
	void failoverMovesUnassignedReportWhenCenterHasNoOnDutyOperator() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ControlCenter centerB = center(21L, "서초");
		ReportCenterRoute currentRoute = ReportCenterRoute.createInitial(report, centerA, 1, "AUTO", NOW.minusSeconds(60));
		AdminCenterMembership membershipA = activeMembership(operator(30L), 20L);
		AdminCenterMembership membershipB = activeMembership(operator(40L), 21L);
		List<OperatorPresence> onDutyB = List.of(OperatorPresence.connect(40L, NOW));
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.existsByReport_IdAndStatusIn(any(), any())).thenReturn(false);
		when(fixture.routeRepository.findByReport_IdAndStatus(10L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(currentRoute));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(20L), any(), any(), any()))
				.thenReturn(List.of(membershipA));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(21L), any(), any(), any()))
				.thenReturn(List.of(membershipB));
		// 강남(30번)은 오프라인, 서초(40번)만 근무중이다.
		when(fixture.presenceRepository.findAllByOperatorIdIn(List.of(30L))).thenReturn(List.of());
		when(fixture.presenceRepository.findAllByOperatorIdIn(List.of(40L))).thenReturn(onDutyB);
		ControlCenterArea currentCoverage = coverage(centerA, 1);
		ControlCenterArea backupCoverage = coverage(centerB, 2);
		when(fixture.controlCenterAreaRepository.findRoutingCandidates(any(), any(), any(), any(), any()))
				.thenReturn(List.of(currentCoverage, backupCoverage));
		when(fixture.routeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		boolean transferred = fixture.service.failoverIfCenterUnavailable(10L);

		assertThat(transferred).isTrue();
		assertThat(currentRoute.getStatus()).isEqualTo(ReportCenterRouteStatus.REROUTED);
		ArgumentCaptor<ReportCenterRoute> savedRoute = ArgumentCaptor.forClass(ReportCenterRoute.class);
		verify(fixture.routeRepository).save(savedRoute.capture());
		assertThat(savedRoute.getValue().getRouteType()).isEqualTo(ReportCenterRouteType.FAILOVER);
		assertThat(savedRoute.getValue().getControlCenter()).isSameAs(centerB);
		verify(fixture.assignmentService).assignAutomatically(report, savedRoute.getValue());
	}

	@Test
	void failoverReturnsToPreviouslyVisitedCenterWhenItRecovered() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ControlCenter centerB = center(21L, "서초");
		// A → B 페일오버 후 B도 비고 A 근무자가 복귀한 상황: A로 되돌아갈 수 있어야 한다.
		ReportCenterRoute firstRoute = ReportCenterRoute.createInitial(report, centerA, 1, "AUTO", NOW.minusSeconds(120));
		ReportCenterRoute currentRoute = ReportCenterRoute.reroute(
				firstRoute, centerB, ReportCenterRouteType.FAILOVER, 2, "이관", NOW.minusSeconds(60));
		AdminCenterMembership membershipA = activeMembership(operator(30L), 20L);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.existsByReport_IdAndStatusIn(any(), any())).thenReturn(false);
		when(fixture.routeRepository.findByReport_IdAndStatus(10L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(currentRoute));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(21L), any(), any(), any()))
				.thenReturn(List.of());
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(20L), any(), any(), any()))
				.thenReturn(List.of(membershipA));
		ControlCenterArea recoveredCoverage = coverage(centerA, 1);
		ControlCenterArea currentCoverage = coverage(centerB, 2);
		when(fixture.presenceRepository.findAllByOperatorIdIn(List.of(30L)))
				.thenReturn(List.of(OperatorPresence.connect(30L, NOW)));
		when(fixture.controlCenterAreaRepository.findRoutingCandidates(any(), any(), any(), any(), any()))
				.thenReturn(List.of(recoveredCoverage, currentCoverage));
		when(fixture.routeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		boolean transferred = fixture.service.failoverIfCenterUnavailable(10L);

		assertThat(transferred).isTrue();
		ArgumentCaptor<ReportCenterRoute> savedRoute = ArgumentCaptor.forClass(ReportCenterRoute.class);
		verify(fixture.routeRepository).save(savedRoute.capture());
		assertThat(savedRoute.getValue().getControlCenter()).isSameAs(centerA);
	}

	@Test
	void failoverDoesNothingWhenCurrentCenterHasOnDutyOperator() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ReportCenterRoute currentRoute = ReportCenterRoute.createInitial(report, centerA, 1, "AUTO", NOW.minusSeconds(60));
		AdminCenterMembership membershipA = activeMembership(operator(30L), 20L);
		List<OperatorPresence> onDutyA = List.of(OperatorPresence.connect(30L, NOW));
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.existsByReport_IdAndStatusIn(any(), any())).thenReturn(false);
		when(fixture.routeRepository.findByReport_IdAndStatus(10L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(currentRoute));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(20L), any(), any(), any()))
				.thenReturn(List.of(membershipA));
		when(fixture.presenceRepository.findAllByOperatorIdIn(List.of(30L))).thenReturn(onDutyA);

		boolean transferred = fixture.service.failoverIfCenterUnavailable(10L);

		assertThat(transferred).isFalse();
		assertThat(currentRoute.getStatus()).isEqualTo(ReportCenterRouteStatus.ACTIVE);
		verify(fixture.routeRepository, never()).save(any());
	}

	@Test
	void failoverNotifiesSuperAdminsOnceWhenNoTargetCenterExists() {
		Fixture fixture = new Fixture();
		Report report = fixture.routedReport(10L);
		ControlCenter centerA = center(20L, "강남");
		ReportCenterRoute currentRoute = ReportCenterRoute.createInitial(report, centerA, 1, "AUTO", NOW.minusSeconds(60));
		User admin = operator(1L);
		when(fixture.reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(fixture.assignmentRepository.existsByReport_IdAndStatusIn(any(), any())).thenReturn(false);
		when(fixture.routeRepository.findByReport_IdAndStatus(10L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(currentRoute));
		when(fixture.membershipRepository.findEligibleOperatorsForAssignment(eq(20L), any(), any(), any()))
				.thenReturn(List.of());
		ControlCenterArea currentCoverage = coverage(centerA, 1);
		when(fixture.controlCenterAreaRepository.findRoutingCandidates(any(), any(), any(), any(), any()))
				.thenReturn(List.of(currentCoverage));
		when(fixture.realtimeEventRepository.existsByReportIdAndEventType(
				10L, RealtimeEventType.REPORT_ASSIGNMENT_STALLED))
				.thenReturn(false)
				.thenReturn(true);
		when(fixture.userRepository.findByRoleIn(any(), any())).thenReturn(new PageImpl<>(List.of(admin)));

		assertThat(fixture.service.failoverIfCenterUnavailable(10L)).isFalse();
		assertThat(fixture.service.failoverIfCenterUnavailable(10L)).isFalse();

		verify(fixture.realtimeEventService, times(1)).record(
				1L, RealtimeEventType.REPORT_ASSIGNMENT_STALLED, 10L, null, null);
	}

	private static final class Fixture {
		private final ReportRepository reportRepository = mock(ReportRepository.class);
		private final ReportCenterRouteRepository routeRepository = mock(ReportCenterRouteRepository.class);
		private final ReportOperatorAssignmentRepository assignmentRepository =
				mock(ReportOperatorAssignmentRepository.class);
		private final ReportStatusHistoryRepository historyRepository = mock(ReportStatusHistoryRepository.class);
		private final AdminCenterMembershipRepository membershipRepository =
				mock(AdminCenterMembershipRepository.class);
		private final OperatorPresenceRepository presenceRepository = mock(OperatorPresenceRepository.class);
		private final ControlCenterAreaRepository controlCenterAreaRepository =
				mock(ControlCenterAreaRepository.class);
		private final ReportOperatorAssignmentService assignmentService =
				mock(ReportOperatorAssignmentService.class);
		private final ReportRealtimeNotifier realtimeNotifier = mock(ReportRealtimeNotifier.class);
		private final RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
		private final RealtimeEventRepository realtimeEventRepository = mock(RealtimeEventRepository.class);
		private final UserRepository userRepository = mock(UserRepository.class);
		private final ReportTransferService service = new ReportTransferService(
				reportRepository,
				routeRepository,
				assignmentRepository,
				historyRepository,
				membershipRepository,
				presenceRepository,
				controlCenterAreaRepository,
				assignmentService,
				realtimeNotifier,
				realtimeEventService,
				realtimeEventRepository,
				userRepository,
				Clock.fixed(NOW, ZoneOffset.UTC)
		);

		private Report routedReport(Long id) {
			Report report = Report.create(
					1L, com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
					com.ojimasam.safy.report.domain.type.ReportCategory.FLOOD_WEATHER,
					com.ojimasam.safy.report.domain.type.ReportSubcategory.ROAD_FLOODING, "침수 제보",
					new BigDecimal("37.5"), new BigDecimal("127.0"), "서울"
			);
			ReflectionTestUtils.setField(report, "id", id);
			ReflectionTestUtils.setField(report, "routingStatus", ReportRoutingStatus.ROUTED);
			AdministrativeArea area = mock(AdministrativeArea.class);
			when(area.getId()).thenReturn(5L);
			ReflectionTestUtils.setField(report, "administrativeArea", area);
			return report;
		}
	}

	private static ControlCenter center(Long id, String name) {
		ControlCenter center = mock(ControlCenter.class);
		when(center.getId()).thenReturn(id);
		when(center.getName()).thenReturn(name);
		return center;
	}

	private static User operator(Long id) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		return user;
	}

	private static AdminCenterMembership activeMembership(User operator, Long centerId) {
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		when(membership.getUser()).thenReturn(operator);
		when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
		when(membership.getControlCenterId()).thenReturn(centerId);
		return membership;
	}

	private static ControlCenterArea coverage(ControlCenter center, int priority) {
		ControlCenterArea coverage = mock(ControlCenterArea.class);
		when(coverage.getControlCenter()).thenReturn(center);
		when(coverage.getPriority()).thenReturn(priority);
		return coverage;
	}
}
