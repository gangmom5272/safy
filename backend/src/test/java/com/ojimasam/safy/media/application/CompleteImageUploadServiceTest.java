package com.ojimasam.safy.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.MediaStorageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;

@ExtendWith(MockitoExtension.class)
class CompleteImageUploadServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock ReportImageRepository reportImageRepository;
	@Mock MediaAssetRepository mediaAssetRepository;
	@Mock MediaObjectStorage mediaObjectStorage;
	@Mock ReportRealtimeNotifier realtimeNotifier;

	@Test
	void verifiesUploadedObjectAndConnectsItToReport() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING, "도로 침수",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 42L);
		MediaAsset asset = MediaAsset.prepareOriginal(
				1L, 42L, "safy", "reports/42/1/image.jpg", "현장.jpg", "image/jpeg", now.plusSeconds(300)
		);
		ReflectionTestUtils.setField(asset, "id", 10L);
		when(reportRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(report));
		when(mediaAssetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(asset));
		when(mediaObjectStorage.stat(asset.getBucketName(), asset.getObjectKey()))
				.thenReturn(new MediaObjectMetadata(1024L, "image/jpeg"));
		when(mediaObjectStorage.readPrefix(asset.getBucketName(), asset.getObjectKey(), 12))
				.thenReturn(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

		CompleteImageUploadService service = new CompleteImageUploadService(
				reportRepository, reportImageRepository, mediaAssetRepository, mediaObjectStorage,
				realtimeNotifier,
				Clock.fixed(now, ZoneOffset.UTC)
		);
		service.complete(new CompleteImageUploadCommand(1L, 42L, 10L));

		ArgumentCaptor<ReportImage> captor = ArgumentCaptor.forClass(ReportImage.class);
		verify(reportImageRepository).save(captor.capture());
		assertThat(captor.getValue().getReport()).isSameAs(report);
		assertThat(captor.getValue().getDisplayOrder()).isZero();
		assertThat(asset.getStorageStatus()).isEqualTo(MediaStorageStatus.AVAILABLE);
		assertThat(asset.getFileSize()).isEqualTo(1024L);
		verify(realtimeNotifier).imageReady(42L, 10L);
	}
}
