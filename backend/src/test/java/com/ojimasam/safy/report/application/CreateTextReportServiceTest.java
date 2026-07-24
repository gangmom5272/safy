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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;

@ExtendWith(MockitoExtension.class)
class CreateTextReportServiceTest {
	@Mock
	private ReportRepository reportRepository;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private CreateTextReportService createTextReportService;

	@Test
	void savesTextReportAndReturnsCreatedResult() {
		when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
			Report report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "id", 42L);
			return report;
		});

		CreateTextReportCommand command = new CreateTextReportCommand(
				1L,
				"출구 주변이 혼잡합니다.",
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				"서울특별시 중구"
		);

		CreateReportResult result = createTextReportService.create(command);

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportRepository).save(captor.capture());
		Report savedReport = captor.getValue();

		assertThat(savedReport.getReporterId()).isEqualTo(1L);
		assertThat(savedReport.getType()).isEqualTo(ReportType.TEXT);
		assertThat(savedReport.getDescription()).isEqualTo("출구 주변이 혼잡합니다.");
		assertThat(savedReport.getLatitude()).isEqualByComparingTo("37.5665000");
		assertThat(savedReport.getLongitude()).isEqualByComparingTo("126.9780000");
		assertThat(result.reportId()).isEqualTo(42L);
		assertThat(result.status()).isEqualTo(ReportStatus.RECEIVED);
		verify(eventPublisher).publishEvent(any(ReportRoutingRequestedEvent.class));
	}
}
