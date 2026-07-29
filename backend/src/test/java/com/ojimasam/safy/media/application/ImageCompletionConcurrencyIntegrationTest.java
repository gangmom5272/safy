package com.ojimasam.safy.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;
import com.ojimasam.safy.media.infrastructure.JpaMediaAssetRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.infrastructure.JpaReportImageRepository;
import com.ojimasam.safy.report.infrastructure.JpaReportRepository;
import com.ojimasam.safy.media.application.dto.CompleteImageUploadCommand;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImageCompletionConcurrencyIntegrationTest {
	@Autowired CompleteImageUploadService service;
	@Autowired JpaReportRepository reportRepository;
	@Autowired ReportRepository reports;
	@Autowired JpaReportImageRepository reportImageRepository;
	@Autowired JpaMediaAssetRepository mediaAssetRepository;
	@Autowired MediaAssetRepository mediaAssets;
	@MockitoBean MediaObjectStorage mediaObjectStorage;

	@BeforeEach
	void cleanUp() {
		reportImageRepository.deleteAll();
		mediaAssetRepository.deleteAll();
		reportRepository.deleteAll();
	}

	@Test
	void concurrentCompletionForSameAssetAttachesImageOnce() throws Exception {
		Report report = reports.save(Report.create(
				1L, ReportType.IMAGE, ReportCategory.ACCIDENT_RISK, ReportSubcategory.FIRE_SMOKE,
				"화재 현장 이미지", new BigDecimal("37.4979000"), new BigDecimal("127.0276000"), "서울 강남구"));
		MediaAsset asset = mediaAssets.save(MediaAsset.prepareOriginal(
				1L, report.getId(), "safy", "reports/" + report.getId() + "/image.jpg",
				"현장.jpg", "image/jpeg", Instant.now().plusSeconds(300)));
		when(mediaObjectStorage.stat(asset.getBucketName(), asset.getObjectKey()))
				.thenReturn(new MediaObjectMetadata(1024L, "image/jpeg"));
		when(mediaObjectStorage.readPrefix(asset.getBucketName(), asset.getObjectKey(), 12))
				.thenReturn(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Boolean>> futures = List.of(
					executor.submit(() -> completeAfterSignal(report.getId(), asset.getId(), ready, start)),
					executor.submit(() -> completeAfterSignal(report.getId(), asset.getId(), ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Boolean> results = List.of(
					futures.get(0).get(10, TimeUnit.SECONDS),
					futures.get(1).get(10, TimeUnit.SECONDS));

			assertThat(results).containsExactlyInAnyOrder(true, false);
			assertThat(reportImageRepository.countByReportId(report.getId())).isEqualTo(1);
			assertThat(mediaAssetRepository.findById(asset.getId()).orElseThrow().getStorageStatus())
					.isEqualTo(MediaStorageStatus.AVAILABLE);
			verify(mediaObjectStorage).stat(asset.getBucketName(), asset.getObjectKey());
		} finally {
			executor.shutdownNow();
		}
	}

	private boolean completeAfterSignal(Long reportId, Long mediaAssetId,
			CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			service.complete(new CompleteImageUploadCommand(1L, reportId, mediaAssetId));
			return true;
		} catch (BusinessException exception) {
			return false;
		}
	}
}
