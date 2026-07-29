package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.report.application.event.ReportTerminatedEvent;

@ExtendWith(MockitoExtension.class)
class CancelReportServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock ReportCenterRouteRepository routeRepository;
	@Mock ReportOperatorAssignmentRepository assignmentRepository;
	@Mock ReportStatusHistoryRepository historyRepository;
	@Mock ReportRealtimeNotifier realtimeNotifier;
	@Mock ApplicationEventPublisher eventPublisher;

	@Test
	void cancelsOwnedReceivedReport() {
		Report report = report();
		ReportCenterRoute route = org.mockito.Mockito.mock(ReportCenterRoute.class);
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(routeRepository.findByReport_IdAndStatus(42L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(route));

		new CancelReportService(
				reportRepository,
				routeRepository,
				assignmentRepository,
				historyRepository,
				realtimeNotifier,
				eventPublisher,
				Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC)
		).cancel(1L, 42L);

		assertThat(report.getStatus()).isEqualTo(ReportStatus.CANCELLED);
		verify(route).close(Instant.parse("2026-07-23T00:00:00Z"));
		verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(ReportTerminatedEvent.class));
	}

	@Test
	void hidesReportNotOwnedByUser() {
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> new CancelReportService(
				reportRepository,
				routeRepository,
				assignmentRepository,
				historyRepository,
				realtimeNotifier,
				eventPublisher,
				Clock.systemUTC()
		).cancel(1L, 42L))
				.isInstanceOf(BusinessException.class);
	}

	private Report report() {
		Report report = Report.create(1L, com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
				com.ojimasam.safy.report.domain.type.ReportCategory.OTHER,
				com.ojimasam.safy.report.domain.type.ReportSubcategory.OTHER_SITUATION, "기존 내용",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 42L);
		return report;
	}
}
