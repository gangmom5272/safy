package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.report.application.dto.ReportHandlingEventType;
import com.ojimasam.safy.report.application.dto.ReportHandlingEventView;
import com.ojimasam.safy.report.application.dto.ReportHandlingHistoryView;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteType;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.user.domain.User;

class ReportHandlingHistoryServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
	private static final Long REPORT_ID = 10L;

	/**
	 * 재배정 한 번, 이관 한 번을 거친 제보. 상태 축만 보면 ASSIGNED 와 RECEIVED 를 세 번
	 * 왕복할 뿐이라 "누가 누구에게 넘겼는지"가 남지 않는다. 담당 이력은 그걸 복원해야 한다.
	 */
	@Test
	void timelineKeepsEveryOperatorAcrossReassignAndTransfer() {
		Fixture fixture = new Fixture();
		Report report = fixture.report();
		ControlCenter gangnam = center(20L, "강남구 재난안전상황실");
		ControlCenter seocho = center(21L, "서초구 재난안전상황실");
		User kim = operator(30L, "김담당");
		User park = operator(31L, "박담당");
		User choi = operator(32L, "최담당");

		ReportCenterRoute gangnamRoute = route(100L,
				ReportCenterRoute.createInitial(report, gangnam, 1, "AUTO", NOW));

		// 1) 김담당 자동 배정 → 확인 → 재배정
		report.assign();
		ReportOperatorAssignment first = ReportOperatorAssignment.automatic(report, gangnamRoute, kim, at(60));
		report.beginHandling();
		first.beginHandling(30L, at(120));
		report.releaseForReassignment();
		first.cancel(at(180), ReportAssignmentCancelReason.REASSIGN_REQUESTED, "현장이 우리 관할이 아님");

		// 2) 박담당이 같은 센터에서 이어받아 확인 → 서초로 이관
		report.assign();
		ReportOperatorAssignment second = ReportOperatorAssignment.automatic(report, gangnamRoute, park, at(180));
		report.beginHandling();
		second.beginHandling(31L, at(240));
		report.releaseForReassignment();
		second.cancel(at(300), ReportAssignmentCancelReason.TRANSFERRED, "서초 관할 도로");
		ReportCenterRoute seochoRoute = route(101L, ReportCenterRoute.reroute(
				gangnamRoute, seocho, ReportCenterRouteType.MANUAL, 1, "서초 관할 도로", at(300)));

		// 3) 최담당이 서초에서 확인하고 종료
		report.assign();
		ReportOperatorAssignment third = ReportOperatorAssignment.automatic(report, seochoRoute, choi, at(300));
		report.beginHandling();
		third.beginHandling(32L, at(360));
		report.completeWork(at(420));
		third.complete(32L, at(420));

		fixture.given(report, List.of(first, second, third), List.of(gangnamRoute, seochoRoute));

		ReportHandlingHistoryView history = fixture.service.getForOperator(32L, REPORT_ID);

		assertThat(history.events())
				.extracting(ReportHandlingEventView::type, ReportHandlingEventView::occurredAt,
						ReportHandlingEventView::operatorName, ReportHandlingEventView::controlCenterName)
				.containsExactly(
						tuple(ReportHandlingEventType.AUTO_ASSIGNED, at(60), "김담당", "강남구 재난안전상황실"),
						tuple(ReportHandlingEventType.VIEWED, at(120), "김담당", "강남구 재난안전상황실"),
						tuple(ReportHandlingEventType.REASSIGNED, at(180), "김담당", "강남구 재난안전상황실"),
						tuple(ReportHandlingEventType.AUTO_ASSIGNED, at(180), "박담당", "강남구 재난안전상황실"),
						tuple(ReportHandlingEventType.VIEWED, at(240), "박담당", "강남구 재난안전상황실"),
						tuple(ReportHandlingEventType.TRANSFERRED, at(300), "박담당", "강남구 재난안전상황실"),
						tuple(ReportHandlingEventType.AUTO_ASSIGNED, at(300), "최담당", "서초구 재난안전상황실"),
						tuple(ReportHandlingEventType.VIEWED, at(360), "최담당", "서초구 재난안전상황실"),
						tuple(ReportHandlingEventType.COMPLETED, at(420), "최담당", "서초구 재난안전상황실"));
	}

	/** 담당자가 적은 원본 사유가 시민용 문구로 갈리지 않고 그대로 나와야 한다. */
	@Test
	void releaseReasonAndTransferDestinationAreExposedAsIs() {
		Fixture fixture = new Fixture();
		Report report = fixture.report();
		ControlCenter gangnam = center(20L, "강남구 재난안전상황실");
		ControlCenter seocho = center(21L, "서초구 재난안전상황실");
		User kim = operator(30L, "김담당");

		ReportCenterRoute gangnamRoute = route(100L,
				ReportCenterRoute.createInitial(report, gangnam, 1, "AUTO", NOW));
		report.assign();
		ReportOperatorAssignment assignment =
				ReportOperatorAssignment.automatic(report, gangnamRoute, kim, at(60));
		report.releaseForReassignment();
		assignment.cancel(at(120), ReportAssignmentCancelReason.TRANSFERRED, "서초 관할 도로");
		ReportCenterRoute seochoRoute = route(101L, ReportCenterRoute.reroute(
				gangnamRoute, seocho, ReportCenterRouteType.MANUAL, 1, "서초 관할 도로", at(120)));

		fixture.given(report, List.of(assignment), List.of(gangnamRoute, seochoRoute));

		ReportHandlingEventView transferred = fixture.service.getForOperator(30L, REPORT_ID).events().stream()
				.filter(event -> event.type() == ReportHandlingEventType.TRANSFERRED)
				.findFirst()
				.orElseThrow();

		assertThat(transferred.reason()).isEqualTo("서초 관할 도로");
		// 목적지는 센터 이름 비교가 아니라 previousRoute FK 로 따라간 값이다.
		assertThat(transferred.toControlCenterId()).isEqualTo(21L);
		assertThat(transferred.toControlCenterName()).isEqualTo("서초구 재난안전상황실");
	}

	/** 자동 이관은 제보 상태가 그대로라 상태 이력에도 배정에도 남지 않는다. 라우팅에서만 드러난다. */
	@Test
	void automaticFailoverAppearsEvenThoughNoAssignmentExists() {
		Fixture fixture = new Fixture();
		Report report = fixture.report();
		ControlCenter gangnam = center(20L, "강남구 재난안전상황실");
		ControlCenter seocho = center(21L, "서초구 재난안전상황실");
		User kim = operator(30L, "김담당");

		ReportCenterRoute gangnamRoute = route(100L,
				ReportCenterRoute.createInitial(report, gangnam, 1, "AUTO", NOW));
		ReportCenterRoute seochoRoute = route(101L, ReportCenterRoute.reroute(
				gangnamRoute, seocho, ReportCenterRouteType.FAILOVER, 1,
				"AUTO_FAILOVER: 근무중 담당자 없음(centerId=20)", at(60)));
		report.assign();
		ReportOperatorAssignment assignment =
				ReportOperatorAssignment.automatic(report, seochoRoute, kim, at(120));

		fixture.given(report, List.of(assignment), List.of(gangnamRoute, seochoRoute));

		ReportHandlingHistoryView history = fixture.service.getForOperator(30L, REPORT_ID);

		assertThat(history.events())
				.extracting(ReportHandlingEventView::type, ReportHandlingEventView::operatorId,
						ReportHandlingEventView::controlCenterName, ReportHandlingEventView::toControlCenterName)
				.containsExactly(
						tuple(ReportHandlingEventType.AUTO_TRANSFERRED, null,
								"강남구 재난안전상황실", "서초구 재난안전상황실"),
						tuple(ReportHandlingEventType.AUTO_ASSIGNED, 30L, "서초구 재난안전상황실", null));
		assertThat(history.stages().transferCount()).isEqualTo(1);
	}

	/** 왕복해도 단계 시각은 최초 확인 시각으로 고정되고, 왕복한 사실은 횟수로 남는다. */
	@Test
	void stagesReportFirstInProgressTimeAndRoundTripCounts() {
		Fixture fixture = new Fixture();
		Report report = fixture.report();
		ControlCenter gangnam = center(20L, "강남구 재난안전상황실");
		ControlCenter seocho = center(21L, "서초구 재난안전상황실");
		User kim = operator(30L, "김담당");
		User park = operator(31L, "박담당");

		ReportCenterRoute gangnamRoute = route(100L,
				ReportCenterRoute.createInitial(report, gangnam, 1, "AUTO", NOW));
		report.assign();
		ReportOperatorAssignment first = ReportOperatorAssignment.automatic(report, gangnamRoute, kim, at(60));
		report.beginHandling();
		first.beginHandling(30L, at(120));
		report.releaseForReassignment();
		first.cancel(at(180), ReportAssignmentCancelReason.REASSIGN_REQUESTED, "다른 건 처리 중");

		report.assign();
		ReportOperatorAssignment second = ReportOperatorAssignment.automatic(report, gangnamRoute, park, at(180));
		report.beginHandling();
		second.beginHandling(31L, at(240));
		report.releaseForReassignment();
		second.cancel(at(300), ReportAssignmentCancelReason.TRANSFERRED, "서초 관할");
		ReportCenterRoute seochoRoute = route(101L, ReportCenterRoute.reroute(
				gangnamRoute, seocho, ReportCenterRouteType.MANUAL, 1, "서초 관할", at(300)));

		fixture.given(report, List.of(first, second), List.of(gangnamRoute, seochoRoute));

		var stages = fixture.service.getForOperator(30L, REPORT_ID).stages();

		assertThat(stages.receivedAt()).isEqualTo(NOW);
		// 마지막(at(240))이 아니라 최초 확인 시각이어야 한다.
		assertThat(stages.firstInProgressAt()).isEqualTo(at(120));
		assertThat(stages.closedAt()).isNull();
		assertThat(stages.currentStatus()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(stages.reassignCount()).isEqualTo(1);
		// 최초 AUTO 라우팅은 이관이 아니므로 세지 않는다.
		assertThat(stages.transferCount()).isEqualTo(1);
	}

	private static Instant at(int plusSeconds) {
		return NOW.plusSeconds(plusSeconds);
	}

	private static ReportCenterRoute route(Long id, ReportCenterRoute route) {
		ReflectionTestUtils.setField(route, "id", id);
		return route;
	}

	private static ControlCenter center(Long id, String name) {
		ControlCenter center = mock(ControlCenter.class);
		when(center.getId()).thenReturn(id);
		when(center.getName()).thenReturn(name);
		return center;
	}

	private static User operator(Long id, String displayName) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getDisplayName()).thenReturn(displayName);
		return user;
	}

	private static class Fixture {
		private final ReportRepository reportRepository = mock(ReportRepository.class);
		private final ReportOperatorAssignmentRepository assignmentRepository =
				mock(ReportOperatorAssignmentRepository.class);
		private final ReportCenterRouteRepository routeRepository = mock(ReportCenterRouteRepository.class);
		private final OperatorReportAccessService accessService = mock(OperatorReportAccessService.class);
		private final ReportHandlingHistoryService service = new ReportHandlingHistoryService(
				reportRepository, assignmentRepository, routeRepository, accessService);

		private Report report() {
			Report report = Report.create(
					1L, ReportType.IMAGE, ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING,
					"침수 제보", new BigDecimal("37.5"), new BigDecimal("127.0"), "서울"
			);
			ReflectionTestUtils.setField(report, "id", REPORT_ID);
			ReflectionTestUtils.setField(report, "reportedAt", NOW);
			return report;
		}

		private void given(Report report, List<ReportOperatorAssignment> assignments,
				List<ReportCenterRoute> routes) {
			when(reportRepository.findOneById(REPORT_ID)).thenReturn(Optional.of(report));
			when(assignmentRepository.findAllByReport_IdOrderByAssignedAtAscIdAsc(REPORT_ID))
					.thenReturn(assignments);
			when(routeRepository.findAllByReport_IdOrderByRoutedAtAscIdAsc(REPORT_ID)).thenReturn(routes);
		}
	}
}
