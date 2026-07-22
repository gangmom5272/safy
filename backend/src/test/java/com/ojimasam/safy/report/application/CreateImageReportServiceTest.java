package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.media.application.MediaObjectMetadata;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.MediaStorageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportType;

@ExtendWith(MockitoExtension.class)
class CreateImageReportServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock ReportImageRepository reportImageRepository;
	@Mock MediaAssetRepository mediaAssetRepository;
	@Mock MediaObjectStorage mediaObjectStorage;

	@Test
	void verifiesObjectsAndCreatesImageReport() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		MediaAsset asset = MediaAsset.prepareOriginal(
				1L, "safy-original", "reports/1/image.jpg", "현장.jpg", "image/jpeg", now.plusSeconds(300)
		);
		ReflectionTestUtils.setField(asset, "id", 10L);
		when(mediaAssetRepository.findAllByIdForUpdate(List.of(10L))).thenReturn(List.of(asset));
		when(mediaObjectStorage.stat("safy-original", "reports/1/image.jpg"))
				.thenReturn(new MediaObjectMetadata(1024L, "image/jpeg"));
		when(mediaObjectStorage.readPrefix("safy-original", "reports/1/image.jpg", 12))
				.thenReturn(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
		when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
			Report report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "id", 42L);
			return report;
		});
		CreateImageReportService service = new CreateImageReportService(
				reportRepository, reportImageRepository, mediaAssetRepository, mediaObjectStorage,
				Clock.fixed(now, ZoneOffset.UTC)
		);

		CreateReportResult result = service.create(new CreateImageReportCommand(
				1L, "도로가 침수됐습니다.", new BigDecimal("37.5"), new BigDecimal("127.0"), null, List.of(10L)
		));

		assertThat(result.reportId()).isEqualTo(42L);
		assertThat(result.type()).isEqualTo(ReportType.IMAGE);
		assertThat(asset.getStorageStatus()).isEqualTo(MediaStorageStatus.AVAILABLE);
		assertThat(asset.getFileSize()).isEqualTo(1024L);
		verify(reportImageRepository).save(any());
	}
}
