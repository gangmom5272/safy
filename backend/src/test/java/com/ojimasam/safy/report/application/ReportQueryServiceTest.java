package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock ReportImageRepository reportImageRepository;
	@Mock MediaObjectStorage mediaObjectStorage;

	@Test
	void getsOwnedReportWithTemporaryPreviewUrl() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = Report.createImage(1L, "침수 제보", new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 42L);
		MediaAsset asset = MediaAsset.prepareOriginal(
				1L, "safy", "reports/1/image.png", "현장.png", "image/png", now.plusSeconds(300)
		);
		ReflectionTestUtils.setField(asset, "id", 10L);
		asset.markAvailable(1024L);
		ReportImage image = ReportImage.create(report, asset, 0);
		ReflectionTestUtils.setField(image, "id", 20L);
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of(image));
		when(mediaObjectStorage.createPresignedGetUrl("safy", "reports/1/image.png", java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/preview");
		ReportQueryService service = service(now);

		ReportDetailView result = service.getMine(1L, 42L);

		assertThat(result.reportId()).isEqualTo(42L);
		assertThat(result.images()).hasSize(1);
		assertThat(result.images().getFirst().previewUrl()).isEqualTo("http://localhost:9000/preview");
		assertThat(result.images().getFirst().previewUrlExpiresAt()).isEqualTo(now.plusSeconds(600));
	}

	@Test
	void hidesOtherUsersReportAsNotFound() {
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service(Instant.parse("2026-07-21T04:00:00Z")).getMine(1L, 42L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void getsMyReportsInPage() {
		Report report = Report.createText(1L, "텍스트 제보", new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 1L);
		when(reportRepository.findAllByReporterId(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new PageImpl<>(List.of(report)));
		when(reportImageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(List.of(1L)))
				.thenReturn(List.of());

		var result = service(Instant.parse("2026-07-21T04:00:00Z")).getMyList(1L, 0, 20);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().imageCount()).isZero();
	}

	private ReportQueryService service(Instant now) {
		return new ReportQueryService(reportRepository, reportImageRepository, mediaObjectStorage,
				Clock.fixed(now, ZoneOffset.UTC));
	}
}
