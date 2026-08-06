package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

@ExtendWith(MockitoExtension.class)
class ReportPublicVisibilityServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock OperatorReportAccessService operatorAccessService;

	@Test
	void withholdsPublishedReport() {
		Report report = confirmedReport();
		when(reportRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(report));

		service().withhold(1L, 42L);

		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.WITHHELD);
	}

	/** 아직 공개되지 않은 제보도 미리 내려둘 수 있다. 이후 승인이 이를 되살리지 않는다. */
	@Test
	void withholdsReportThatWasNeverPublished() {
		Report report = report();
		when(reportRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(report));

		service().withhold(1L, 42L);

		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.WITHHELD);
	}

	@Test
	void propagatesAccessDenialWithoutTouchingReport() {
		doThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
				.when(operatorAccessService).requireAccess(1L, 42L);

		assertThatThrownBy(() -> service().withhold(1L, 42L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(CommonErrorCode.ACCESS_DENIED);
		verifyNoInteractions(reportRepository);
	}

	@Test
	void failsWhenReportDoesNotExist() {
		when(reportRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().withhold(1L, 42L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	private ReportPublicVisibilityService service() {
		return new ReportPublicVisibilityService(reportRepository, operatorAccessService);
	}

	private Report report() {
		return Report.create(1L, ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION, ReportSubcategory.EXIT_CONGESTION, "출구 혼잡",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
	}

	private Report confirmedReport() {
		Report report = report();
		report.assign();
		report.beginHandling();
		report.confirm(Instant.now().plusSeconds(60));
		return report;
	}
}
