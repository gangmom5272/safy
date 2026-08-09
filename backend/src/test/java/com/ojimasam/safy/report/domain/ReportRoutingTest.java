package com.ojimasam.safy.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

class ReportRoutingTest {
	@Test
	void startsPendingAndCompletesWithAdministrativeArea() {
		Report report = report();
		AdministrativeArea area = mock(AdministrativeArea.class);

		report.completeRouting(area);

		assertThat(report.getRoutingStatus()).isEqualTo(ReportRoutingStatus.ROUTED);
		assertThat(report.getAdministrativeArea()).isSameAs(area);
		assertThat(report.getNextRoutingAt()).isNull();
	}

	@Test
	void requiresManualRoutingAfterMaximumAttempts() {
		Report report = report();
		Instant retryAt = Instant.now().plusSeconds(60);

		report.scheduleRoutingRetry("temporary failure", retryAt, 2);
		report.scheduleRoutingRetry("temporary failure", retryAt.plusSeconds(60), 2);

		assertThat(report.getRoutingAttempts()).isEqualTo(2);
		assertThat(report.getRoutingStatus()).isEqualTo(ReportRoutingStatus.MANUAL_REQUIRED);
		assertThat(report.getNextRoutingAt()).isNull();
	}

	@Test
	void stopsRoutingWhenReportIsCancelled() {
		Report report = report();

		report.cancel();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.CANCELLED);
		assertThat(report.getRoutingStatus()).isEqualTo(ReportRoutingStatus.CANCELLED);
	}

	private Report report() {
		return Report.create(
				1L,
				ReportType.IMAGE,
				ReportCategory.FLOOD_WEATHER,
				ReportSubcategory.ROAD_FLOODING,
				"침수 제보",
				new BigDecimal("37.5"),
				new BigDecimal("127.0"),
				null
		);
	}
}
