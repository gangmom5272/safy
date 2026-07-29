package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaStatus;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenterAreaRepository;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.type.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.type.CoverageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;

class ReportRoutingTransactionServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

	@Test
	void routesReportToFirstAvailableCandidate() {
		ReportRepository reportRepository = mock(ReportRepository.class);
		AdministrativeAreaRepository areaRepository = mock(AdministrativeAreaRepository.class);
		ControlCenterAreaRepository coverageRepository = mock(ControlCenterAreaRepository.class);
		ReportCenterRouteRepository routeRepository = mock(ReportCenterRouteRepository.class);
		Report report = report(10L);
		AdministrativeArea area = mock(AdministrativeArea.class);
		ControlCenter center = mock(ControlCenter.class);
		ControlCenterArea coverage = mock(ControlCenterArea.class);
		when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(area.getId()).thenReturn(20L);
		when(area.getCode()).thenReturn("1168000000");
		when(areaRepository.findByCodeAndStatus("1168000000", AdministrativeAreaStatus.ACTIVE))
				.thenReturn(Optional.of(area));
		when(coverage.getControlCenter()).thenReturn(center);
		when(coverage.getCoverageRole()).thenReturn(CoverageRole.PRIMARY);
		when(coverage.getPriority()).thenReturn(1);
		when(coverage.getId()).thenReturn(30L);
		when(center.getId()).thenReturn(40L);
		when(center.getCapacityLimit()).thenReturn(null);
		when(coverageRepository.findRoutingCandidates(
				20L, NOW, CoverageStatus.ACTIVE, ControlCenterStatus.ACTIVE, CoverageRole.PRIMARY
		)).thenReturn(List.of(coverage));
		ReportRoutingTransactionService service = service(
				reportRepository, areaRepository, coverageRepository, routeRepository
		);

		service.completeAutomaticRouting(10L, new ResolvedAdministrativeArea("1168000000", "강남구"));

		assertThat(report.getRoutingStatus()).isEqualTo(ReportRoutingStatus.ROUTED);
		assertThat(report.getAdministrativeArea()).isSameAs(area);
		ArgumentCaptor<ReportCenterRoute> captor = ArgumentCaptor.forClass(ReportCenterRoute.class);
		verify(routeRepository).save(captor.capture());
		assertThat(captor.getValue().getControlCenter()).isSameAs(center);
		assertThat(captor.getValue().getStatus()).isEqualTo(ReportCenterRouteStatus.ACTIVE);
	}

	@Test
	void requiresManualRoutingWhenCoverageIsMissing() {
		ReportRepository reportRepository = mock(ReportRepository.class);
		AdministrativeAreaRepository areaRepository = mock(AdministrativeAreaRepository.class);
		ControlCenterAreaRepository coverageRepository = mock(ControlCenterAreaRepository.class);
		Report report = report(10L);
		AdministrativeArea area = mock(AdministrativeArea.class);
		when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(area.getId()).thenReturn(20L);
		when(area.getCode()).thenReturn("1168000000");
		when(areaRepository.findByCodeAndStatus("1168000000", AdministrativeAreaStatus.ACTIVE))
				.thenReturn(Optional.of(area));
		when(coverageRepository.findRoutingCandidates(
				20L, NOW, CoverageStatus.ACTIVE, ControlCenterStatus.ACTIVE, CoverageRole.PRIMARY
		)).thenReturn(List.of());
		ReportRoutingTransactionService service = service(
				reportRepository, areaRepository, coverageRepository, mock(ReportCenterRouteRepository.class)
		);

		service.completeAutomaticRouting(10L, new ResolvedAdministrativeArea("1168000000", "강남구"));

		assertThat(report.getRoutingStatus()).isEqualTo(ReportRoutingStatus.MANUAL_REQUIRED);
		assertThat(report.getRoutingFailureReason()).contains("담당 관제센터");
	}

	private ReportRoutingTransactionService service(
			ReportRepository reportRepository,
			AdministrativeAreaRepository areaRepository,
			ControlCenterAreaRepository coverageRepository,
			ReportCenterRouteRepository routeRepository
	) {
		return new ReportRoutingTransactionService(
				reportRepository,
				areaRepository,
				coverageRepository,
				routeRepository,
				new ReportRoutingProperties(5, Duration.ofMinutes(1), 50),
				mock(ReportOperatorAssignmentService.class),
				Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	private Report report(Long id) {
		Report report = Report.create(
				1L,
				com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
				com.ojimasam.safy.report.domain.type.ReportCategory.FLOOD_WEATHER,
				com.ojimasam.safy.report.domain.type.ReportSubcategory.ROAD_FLOODING,
				"침수 제보",
				new BigDecimal("37.5"),
				new BigDecimal("127.0"),
				null
		);
		ReflectionTestUtils.setField(report, "id", id);
		return report;
	}
}
