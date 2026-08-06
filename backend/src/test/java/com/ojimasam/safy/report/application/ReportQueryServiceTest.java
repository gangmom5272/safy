package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.ojimasam.safy.analysis.domain.MediaBlurResultRepository;
import com.ojimasam.safy.analysis.domain.MediaBlurResult;
import com.ojimasam.safy.analysis.application.dto.BlurredImageResult;
import com.ojimasam.safy.live.domain.StreamRecordingRepository;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.live.domain.StreamRecording;
import com.ojimasam.safy.live.domain.type.StreamRecordingStatus;
import com.ojimasam.safy.media.domain.type.MediaVariantType;
import com.ojimasam.safy.report.application.dto.ReportDetailView;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock ReportImageRepository reportImageRepository;
	@Mock MediaObjectStorage mediaObjectStorage;
	@Mock ReportCenterRouteRepository routeRepository;
	@Mock ReportStatusHistoryRepository historyRepository;
	@Mock OperatorReportAccessService operatorAccessService;
	@Mock ReportOperatorAssignmentRepository assignmentRepository;
	@Mock MediaBlurResultRepository blurResultRepository;
	@Mock com.ojimasam.safy.media.domain.MediaAssetRepository mediaAssetRepository;
	@Mock StreamRecordingRepository streamRecordingRepository;

	@Test
	void getsOwnedReportWithBlurredPreviewUrl() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING, "침수 제보",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 42L);
		MediaAsset asset = MediaAsset.prepareOriginal(
				1L, 42L, "safy", "reports/1/image.png", "현장.png", "image/png", now.plusSeconds(300)
		);
		ReflectionTestUtils.setField(asset, "id", 10L);
		asset.markAvailable(1024L);
		MediaAsset blurred = MediaAsset.recordServerObject(1L, 42L, "safy",
				"reports/42/blurred/10.jpg", null, "image/jpeg", 900L,
				com.ojimasam.safy.media.domain.type.MediaVariantType.BLURRED, asset, now);
		ReflectionTestUtils.setField(blurred, "id", 11L);
		MediaBlurResult blurResult = MediaBlurResult.completed(10L, 11L,
				new BlurredImageResult(new byte[] {1}, "privacy-blur-v1", 100, 100, 1), now);
		ReportImage image = ReportImage.create(report, asset, 0);
		ReflectionTestUtils.setField(image, "id", 20L);
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of(image));
		when(blurResultRepository.findAllBySourceMediaAssetIdIn(List.of(10L))).thenReturn(List.of(blurResult));
		when(mediaAssetRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(blurred));
		when(mediaObjectStorage.createPresignedGetUrl("safy", "reports/42/blurred/10.jpg", java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/preview");
		ReportQueryService service = service(now);

		ReportDetailView result = service.getMine(1L, 42L);

		assertThat(result.reportId()).isEqualTo(42L);
		assertThat(result.images()).hasSize(1);
		assertThat(result.images().getFirst().previewUrl()).isEqualTo("http://localhost:9000/preview");
		assertThat(result.images().getFirst().previewUrlExpiresAt()).isEqualTo(now.plusSeconds(600));
		assertThat(result.images().getFirst().mediaAssetId()).isEqualTo(11L);
	}

	@Test
	void doesNotExposeOriginalPreviewWhenBlurIsNotReady() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = imageReport(42L);
		MediaAsset original = originalImage(1L, 42L, 10L, now);
		ReportImage image = ReportImage.create(report, original, 0);
		ReflectionTestUtils.setField(image, "id", 20L);
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of(image));
		when(blurResultRepository.findAllBySourceMediaAssetIdIn(List.of(10L))).thenReturn(List.of());

		ReportDetailView result = service(now).getMine(1L, 42L);

		assertThat(result.images().getFirst().previewUrl()).isNull();
		verify(mediaObjectStorage, never()).createPresignedGetUrl(
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void showsRepresentativeFrameAsDetailMediaForLiveReport() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = liveReportWithRepresentativeFrame(42L, 10L, 11L, now);
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of());
		when(mediaObjectStorage.createPresignedGetUrl("safy", blurredFrameKey(42L), java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/frame-preview");

		ReportDetailView result = service(now).getMine(1L, 42L);

		assertThat(result.images()).hasSize(1);
		assertThat(result.images().getFirst().previewUrl()).isEqualTo("http://localhost:9000/frame-preview");
		assertThat(result.images().getFirst().previewUrlExpiresAt()).isEqualTo(now.plusSeconds(600));
		assertThat(result.images().getFirst().mediaAssetId()).isEqualTo(11L);
		assertThat(result.images().getFirst().reportImageId()).isNull();
		assertThat(result.images().getFirst().displayOrder()).isZero();
	}

	@Test
	void exposesOriginalPreviewToOperator() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = imageReport(42L);
		MediaAsset original = originalImage(1L, 42L, 10L, now);
		ReportImage image = ReportImage.create(report, original, 0);
		ReflectionTestUtils.setField(image, "id", 20L);
		when(reportRepository.findOneById(42L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of(image));
		when(mediaObjectStorage.createPresignedGetUrl(
				"safy", "reports/1/image.png", java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/original");

		ReportDetailView result = service(now).getForOperator(30L, 42L);

		verify(operatorAccessService).requireAccess(30L, 42L);
		assertThat(result.images().getFirst().mediaAssetId()).isEqualTo(10L);
		assertThat(result.images().getFirst().previewUrl()).isEqualTo("http://localhost:9000/original");
		verify(blurResultRepository, never()).findAllBySourceMediaAssetIdIn(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void exposesOriginalFrameToOperatorForLiveReport() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = liveReportWithRepresentativeFrame(42L, 10L, 11L, now);
		when(reportRepository.findOneById(42L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of());
		when(mediaObjectStorage.createPresignedGetUrl("safy", frameKey(42L), java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/original-frame");

		ReportDetailView result = service(now).getForOperator(30L, 42L);

		assertThat(result.images()).hasSize(1);
		assertThat(result.images().getFirst().mediaAssetId()).isEqualTo(10L);
		assertThat(result.images().getFirst().previewUrl()).isEqualTo("http://localhost:9000/original-frame");
		assertThat(result.images().getFirst().reportImageId()).isNull();
	}

	@Test
	void exposesLatestCompletedRecordingUrlToOperator() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = liveReportWithRepresentativeFrame(42L, 10L, 11L, now);
		when(reportRepository.findOneById(42L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of());
		when(mediaObjectStorage.createPresignedGetUrl("safy", frameKey(42L), java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/original-frame");
		MediaAsset video = MediaAsset.recordServerObject(1L, 42L, "safy", "recordings/42/session/token.mp4",
				null, "video/mp4", 2048L, MediaVariantType.ORIGINAL, null, now);
		ReflectionTestUtils.setField(video, "id", 90L);
		StreamRecording recording = completedRecording(42L, 90L, now);
		when(streamRecordingRepository.findTopByReportIdAndStatusOrderByEndedAtDesc(42L, StreamRecordingStatus.COMPLETED))
				.thenReturn(Optional.of(recording));
		when(mediaAssetRepository.findAllById(List.of(90L))).thenReturn(List.of(video));
		when(mediaObjectStorage.createPresignedGetUrl(
				"safy", "recordings/42/session/token.mp4", java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/recording");

		ReportDetailView result = service(now).getForOperator(30L, 42L);

		assertThat(result.recordingStatus()).isEqualTo(StreamRecordingStatus.COMPLETED);
		assertThat(result.originalVideoUrl()).isEqualTo("http://localhost:9000/recording");
		assertThat(result.originalVideoUrlExpiresAt()).isEqualTo(now.plusSeconds(600));
		assertThat(result.videoMimeType()).isEqualTo("video/mp4");
	}

	@Test
	void fallsBackToLatestAttemptStatusWhenNoCompletedRecordingExists() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = liveReportWithRepresentativeFrame(42L, 10L, 11L, now);
		when(reportRepository.findOneById(42L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of());
		when(mediaObjectStorage.createPresignedGetUrl("safy", frameKey(42L), java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/original-frame");
		when(streamRecordingRepository.findTopByReportIdAndStatusOrderByEndedAtDesc(42L, StreamRecordingStatus.COMPLETED))
				.thenReturn(Optional.empty());
		when(streamRecordingRepository.findTopByReportIdOrderByStartedAtDesc(42L))
				.thenReturn(Optional.of(failedRecording(42L, now)));

		ReportDetailView result = service(now).getForOperator(30L, 42L);

		assertThat(result.recordingStatus()).isEqualTo(StreamRecordingStatus.FAILED);
		assertThat(result.originalVideoUrl()).isNull();
	}

	@Test
	void doesNotExposeRecordingFieldsToCitizen() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = liveReportWithRepresentativeFrame(42L, 10L, 11L, now);
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of());
		when(mediaObjectStorage.createPresignedGetUrl(
				"safy", blurredFrameKey(42L), java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/frame-preview");

		ReportDetailView result = service(now).getMine(1L, 42L);

		assertThat(result.recordingStatus()).isNull();
		assertThat(result.originalVideoUrl()).isNull();
		verify(streamRecordingRepository, never()).findTopByReportIdAndStatusOrderByEndedAtDesc(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private StreamRecording completedRecording(Long reportId, Long mediaAssetId, Instant now) {
		StreamRecording recording = StreamRecording.start(
				java.util.UUID.randomUUID(), reportId, "egress-1", "recordings/" + reportId + "/session/token.mp4",
				"frames/" + reportId + "/session/token/", now);
		recording.complete(mediaAssetId, 5000L, now.plusSeconds(30));
		return recording;
	}

	private StreamRecording failedRecording(Long reportId, Instant now) {
		StreamRecording recording = StreamRecording.start(
				java.util.UUID.randomUUID(), reportId, "egress-2", "recordings/" + reportId + "/session/token2.mp4",
				"frames/" + reportId + "/session/token2/", now);
		recording.fail("업로드 실패", now.plusSeconds(10));
		return recording;
	}

	@Test
	void includesAssignedControlCenterInOwnedReportDetail() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING, "침수 제보",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 42L);
		ControlCenter center = org.mockito.Mockito.mock(ControlCenter.class);
		ReportCenterRoute route = org.mockito.Mockito.mock(ReportCenterRoute.class);
		when(center.getName()).thenReturn("중구 재난안전상황실");
		when(route.getControlCenter()).thenReturn(center);
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of());
		when(routeRepository.findByReport_IdAndStatus(42L, ReportCenterRouteStatus.ACTIVE))
				.thenReturn(Optional.of(route));

		ReportDetailView result = service(now).getMine(1L, 42L);

		assertThat(result.assignedControlCenter()).isNotNull();
		assertThat(result.assignedControlCenter().name()).isEqualTo("중구 재난안전상황실");
	}

	@Test
	void hidesOtherUsersReportAsNotFound() {
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service(Instant.parse("2026-07-21T04:00:00Z")).getMine(1L, 42L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void getsMyReportsInPage() {
		Report report = Report.create(1L, ReportType.LIVE,
				ReportCategory.CROWD_CONGESTION, ReportSubcategory.CROWD_INCREASING, "라이브 제보",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 1L);
		when(reportRepository.findAllByReporterId(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new PageImpl<>(List.of(report)));
		when(reportImageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(List.of(1L)))
				.thenReturn(List.of());

		var result = service(Instant.parse("2026-07-21T04:00:00Z")).getMyList(1L, 0, 20);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().imageCount()).isZero();
	}

	@Test
	void showsRepresentativeThumbnailForLiveReportWithoutReportImageRow() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = liveReportWithRepresentativeFrame(7L, 10L, 11L, now);
		when(reportRepository.findAllByReporterId(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new PageImpl<>(List.of(report)));
		when(reportImageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(List.of(7L)))
				.thenReturn(List.of());
		when(mediaObjectStorage.createPresignedGetUrl("safy", blurredFrameKey(7L), java.time.Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/frame-preview");

		var result = service(now).getMyList(1L, 0, 20);

		var representative = result.getContent().getFirst().representativeImage();
		assertThat(representative).isNotNull();
		assertThat(representative.previewUrl()).isEqualTo("http://localhost:9000/frame-preview");
		assertThat(representative.previewUrlExpiresAt()).isEqualTo(now.plusSeconds(600));
		assertThat(representative.mediaAssetId()).isEqualTo(11L);
		assertThat(representative.reportImageId()).isNull();
		assertThat(representative.displayOrder()).isZero();
		assertThat(result.getContent().getFirst().imageCount()).isZero();
	}

	@Test
	void exposesOnlyCitizenVisibleStatusProgress() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = imageReport(42L);
		List<ReportStatusHistory> histories = List.of(
				history(1L, ReportStatus.ASSIGNED, now),
				history(2L, ReportStatus.IN_PROGRESS, now.plusSeconds(60)),
				history(3L, ReportStatus.RECEIVED, now.plusSeconds(120)),
				history(4L, ReportStatus.ASSIGNED, now.plusSeconds(180)),
				history(5L, ReportStatus.IN_PROGRESS, now.plusSeconds(240)),
				history(6L, ReportStatus.CLOSED, now.plusSeconds(300)));
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(historyRepository.findAllByReport_IdOrderByChangedAtAscIdAsc(42L)).thenReturn(histories);

		var result = service(now).getMyStatusHistory(1L, 42L);

		assertThat(result).extracting(item -> item.toStatus())
				.containsExactly(ReportStatus.RECEIVED, ReportStatus.IN_PROGRESS, ReportStatus.CLOSED);
		assertThat(result).extracting(item -> item.reason())
				.containsExactly("제보가 접수되었습니다.", "제보 내용을 확인하고 있습니다.", "제보 처리가 완료되었습니다.");
	}

	@Test
	void keepsCitizenStatusInProgressDuringInternalReassignment() {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		Report report = imageReport(42L);
		report.assign();
		report.beginHandling();
		report.releaseForReassignment();
		when(reportRepository.findByIdAndReporterId(42L, 1L)).thenReturn(Optional.of(report));
		when(reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(42L)).thenReturn(List.of());
		when(historyRepository.existsByReport_IdAndToStatus(42L, ReportStatus.IN_PROGRESS)).thenReturn(true);

		ReportDetailView result = service(now).getMine(1L, 42L);

		assertThat(result.status()).isEqualTo(ReportStatus.IN_PROGRESS);
		assertThat(result.cancellable()).isFalse();
	}

	private ReportQueryService service(Instant now) {
		return new ReportQueryService(reportRepository, reportImageRepository, mediaObjectStorage,
				routeRepository, historyRepository, operatorAccessService, assignmentRepository,
				blurResultRepository, mediaAssetRepository, streamRecordingRepository,
				Clock.fixed(now, ZoneOffset.UTC));
	}

	private Report imageReport(Long reportId) {
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING, "침수 제보",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", reportId);
		return report;
	}

	/** Egress 프레임 원본과 그 블러본만 있고 report_image 행은 없는 LIVE 제보. */
	private Report liveReportWithRepresentativeFrame(Long reportId, Long frameAssetId, Long blurredAssetId,
			Instant now) {
		Report report = Report.create(1L, ReportType.LIVE,
				ReportCategory.CROWD_CONGESTION, ReportSubcategory.CROWD_INCREASING, "라이브 제보",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", reportId);
		MediaAsset frame = MediaAsset.recordServerObject(1L, reportId, "safy", frameKey(reportId), null,
				"image/jpeg", 1200L, com.ojimasam.safy.media.domain.type.MediaVariantType.ORIGINAL, null, now);
		ReflectionTestUtils.setField(frame, "id", frameAssetId);
		MediaAsset blurred = MediaAsset.recordServerObject(1L, reportId, "safy", blurredFrameKey(reportId), null,
				"image/jpeg", 900L, com.ojimasam.safy.media.domain.type.MediaVariantType.BLURRED, frame, now);
		ReflectionTestUtils.setField(blurred, "id", blurredAssetId);
		report.applyRepresentativeBlurAsset(blurred);
		return report;
	}

	private String frameKey(Long reportId) {
		return "frames/%d/live/token/000012.jpg".formatted(reportId);
	}

	private String blurredFrameKey(Long reportId) {
		return "frames/%d/live/token/blurred/000012.jpg".formatted(reportId);
	}

	private MediaAsset originalImage(Long ownerId, Long reportId, Long assetId, Instant now) {
		MediaAsset asset = MediaAsset.prepareOriginal(
				ownerId, reportId, "safy", "reports/1/image.png", "현장.png", "image/png", now.plusSeconds(300));
		ReflectionTestUtils.setField(asset, "id", assetId);
		asset.markAvailable(1024L);
		return asset;
	}

	private ReportStatusHistory history(Long id, ReportStatus status, Instant changedAt) {
		ReportStatusHistory history = org.mockito.Mockito.mock(ReportStatusHistory.class);
		org.mockito.Mockito.lenient().when(history.getId()).thenReturn(id);
		when(history.getToStatus()).thenReturn(status);
		org.mockito.Mockito.lenient().when(history.getChangedAt()).thenReturn(changedAt);
		return history;
	}
}
