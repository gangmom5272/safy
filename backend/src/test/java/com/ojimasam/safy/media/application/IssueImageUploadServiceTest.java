package com.ojimasam.safy.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.MediaStorageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;

@ExtendWith(MockitoExtension.class)
class IssueImageUploadServiceTest {
	@Mock MediaAssetRepository repository;
	@Mock ReportRepository reportRepository;
	@Mock MediaObjectStorage storage;

	@Test
	void createsPendingAssetAndPresignedUrl() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-21T04:00:00Z"), ZoneOffset.UTC);
		IssueImageUploadService service = new IssueImageUploadService(
				repository, reportRepository, storage, "safy-original", clock);
		when(reportRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(imageReport()));
		when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
			MediaAsset asset = invocation.getArgument(0);
			ReflectionTestUtils.setField(asset, "id", 10L);
			return asset;
		});
		when(storage.createPresignedPutUrl(any(), any(), any()))
				.thenReturn("http://localhost:9000/safy-original/reports/image.jpg?signature=test");

		IssuedImageUpload result = service.issue(new IssueImageUploadCommand(1L, 42L, "현장.heic", "image/jpeg"));

		assertThat(result.mediaAssetId()).isEqualTo(10L);
		assertThat(result.uploadUrl()).contains("signature=test");
		assertThat(result.contentType()).isEqualTo("image/jpeg");
		assertThat(result.maxFileSize()).isEqualTo(10L * 1024 * 1024);
		assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-07-21T04:05:00Z"));
		ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
		verify(repository).save(captor.capture());
		MediaAsset saved = captor.getValue();
		assertThat(saved.getObjectKey()).endsWith(".jpg");
		assertThat(saved.getStorageStatus()).isEqualTo(MediaStorageStatus.PENDING_UPLOAD);
		assertThat(saved.getFileSize()).isNull();
	}

	@Test
	void rejectsHeicUntilClientConvertsIt() {
		IssueImageUploadService service = new IssueImageUploadService(
				repository, reportRepository, storage, "safy-original", Clock.systemUTC());

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> service.issue(new IssueImageUploadCommand(1L, 42L, "현장.heic", "image/heic"))
		).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("HEIC/HEIF");
	}

	@Test
	void rejectsWhenActiveUploadQuotaIsExhausted() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-21T04:00:00Z"), ZoneOffset.UTC);
		IssueImageUploadService service = new IssueImageUploadService(
				repository, reportRepository, storage, "safy-original", clock);
		when(reportRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(imageReport()));
		when(repository.countByOwnerUserIdAndStorageStatusAndUploadExpiresAtAfter(any(), any(), any()))
				.thenReturn(10L);

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> service.issue(new IssueImageUploadCommand(1L, 42L, "현장.jpg", "image/jpeg"))
		).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("최대 10개");
	}

	private Report imageReport() {
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING, "침수 현장",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 42L);
		return report;
	}
}
