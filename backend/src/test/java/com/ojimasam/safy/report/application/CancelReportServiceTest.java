package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportStatus;

@ExtendWith(MockitoExtension.class)
class CancelReportServiceTest {
	@Mock ReportRepository reportRepository;

	@Test
	void cancelsOwnedReceivedReport() {
		Report report = report();
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));

		new CancelReportService(reportRepository).cancel(1L, 42L);

		assertThat(report.getStatus()).isEqualTo(ReportStatus.CANCELLED);
	}

	@Test
	void hidesReportNotOwnedByUser() {
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> new CancelReportService(reportRepository).cancel(1L, 42L))
				.isInstanceOf(BusinessException.class);
	}

	private Report report() {
		Report report = Report.createText(1L, "기존 내용", new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 42L);
		return report;
	}
}
