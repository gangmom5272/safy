package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;

@ExtendWith(MockitoExtension.class)
class CreateReportServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock ApplicationEventPublisher eventPublisher;
	@InjectMocks CreateReportService service;

	@Test
	void createsLiveReportAndRequestsRouting() {
		when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
			Report report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "id", 42L);
			return report;
		});

		CreateReportResult result = service.create(new CreateReportCommand(
				1L, ReportType.LIVE, ReportCategory.CROWD_CONGESTION, ReportSubcategory.EXIT_CONGESTION,
				"출구 주변이 혼잡합니다.",
				new BigDecimal("37.5665000"), new BigDecimal("126.9780000"), "서울특별시 중구"
		));

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(ReportType.LIVE);
		assertThat(captor.getValue().getCategory()).isEqualTo(ReportCategory.CROWD_CONGESTION);
		assertThat(captor.getValue().getSubcategory()).isEqualTo(ReportSubcategory.EXIT_CONGESTION);
		assertThat(captor.getValue().getDescription()).isEqualTo("출구 주변이 혼잡합니다.");
		assertThat(result.reportId()).isEqualTo(42L);
		assertThat(result.status()).isEqualTo(ReportStatus.RECEIVED);
		verify(eventPublisher).publishEvent(any(ReportRoutingRequestedEvent.class));
	}
}
