package com.ojimasam.safy.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;
import com.ojimasam.safy.analysis.infrastructure.JpaMediaProcessingJobRepository;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.StreamRecording;
import com.ojimasam.safy.live.infrastructure.JpaExtractedFrameRepository;
import com.ojimasam.safy.live.infrastructure.JpaLiveSessionRepository;
import com.ojimasam.safy.live.infrastructure.JpaStreamRecordingRepository;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.application.StoredMediaObject;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.type.MediaVariantType;
import com.ojimasam.safy.media.infrastructure.JpaMediaAssetRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.infrastructure.JpaReportRepository;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LiveFrameIngestServiceIntegrationTest {
	@Autowired LiveFrameIngestService service;
	@Autowired JpaReportRepository reports;
	@Autowired JpaLiveSessionRepository sessions;
	@Autowired JpaStreamRecordingRepository recordings;
	@Autowired JpaMediaAssetRepository assets;
	@Autowired JpaExtractedFrameRepository frames;
	@Autowired JpaMediaProcessingJobRepository jobs;
	@MockitoBean MediaObjectStorage storage;

	@Test
	void registersNewFrameOnceAndEnqueuesAiProcessing() {
		Report report = reports.saveAndFlush(Report.create(1L, ReportType.LIVE,
				ReportCategory.ACCIDENT_RISK, ReportSubcategory.FIRE_SMOKE, "라이브 현장",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null));
		LiveSession session = sessions.saveAndFlush(LiveSession.create(
				report.getId(), 1L, Instant.now(), Duration.ofMinutes(30)));
		String prefix = "frames/%d/%s/123/".formatted(report.getId(), session.getId());
		StreamRecording recording = recordings.saveAndFlush(StreamRecording.start(session.getId(), report.getId(),
				"egress-1", "recordings/%d/%s/123.mp4".formatted(report.getId(), session.getId()),
				prefix, Instant.now()));
		String frameKey = prefix + "1690000000000.jpg";
		StoredMediaObject object = new StoredMediaObject(frameKey, 2048L, Instant.parse("2026-08-01T01:00:05Z"));
		when(storage.list(anyString(), eq(prefix), nullable(String.class))).thenReturn(List.of(object));

		service.ingest("egress-1");
		service.ingest("egress-1");

		assertThat(assetsUnder(prefix)).singleElement().satisfies(asset -> {
			assertThat(asset.getObjectKey()).isEqualTo(frameKey);
			assertThat(asset.getVariantType()).isEqualTo(MediaVariantType.ORIGINAL);
		});
		assertThat(frames.findTopByStreamRecordingIdOrderBySequenceNumberDesc(recording.getId()))
				.hasValueSatisfying(frame -> {
					assertThat(frame.getSequenceNumber()).isZero();
					assertThat(frame.getCapturedAt()).isEqualTo(object.lastModified());
				});
		Long assetId = assetsUnder(prefix).getFirst().getId();
		assertThat(jobs.findByMediaAssetIdAndJobType(assetId, MediaProcessingJobType.ANALYZE_AND_BLUR)).isPresent();
		assertThat(recordings.findById(recording.getId()).orElseThrow().getLastIngestedFrameKey())
				.isEqualTo(frameKey);
	}

	/**
	 * 블러 파생본은 원본 프레임과 같은 prefix 아래(blurred/)에 저장되고 키 정렬상 타임스탬프
	 * 프레임보다 뒤에 온다. 커서가 파생본까지 전진하면 이후 프레임이 영구히 무시된다.
	 */
	@Test
	void skipsBlurredDerivativesSoLaterFramesAreStillIngested() {
		Report report = reports.saveAndFlush(Report.create(1L, ReportType.LIVE,
				ReportCategory.ACCIDENT_RISK, ReportSubcategory.FIRE_SMOKE, "라이브 현장",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null));
		LiveSession session = sessions.saveAndFlush(LiveSession.create(
				report.getId(), 1L, Instant.now(), Duration.ofMinutes(30)));
		String prefix = "frames/%d/%s/456/".formatted(report.getId(), session.getId());
		StreamRecording recording = recordings.saveAndFlush(StreamRecording.start(session.getId(), report.getId(),
				"egress-2", "recordings/%d/%s/456.mp4".formatted(report.getId(), session.getId()),
				prefix, Instant.now()));
		String firstKey = prefix + "1690000000000.jpg";
		String secondKey = prefix + "1690000005000.jpg";
		String blurredKey = prefix + "blurred/1690000000000.jpg";
		when(storage.list(anyString(), eq(prefix), nullable(String.class)))
				.thenReturn(List.of(new StoredMediaObject(firstKey, 2048L, Instant.parse("2026-08-01T01:00:05Z"))))
				// 워커가 첫 프레임을 블러한 뒤의 목록. MinIO 는 키 순서로 주므로 blurred/ 가 마지막이다.
				.thenReturn(List.of(
						new StoredMediaObject(secondKey, 2048L, Instant.parse("2026-08-01T01:00:10Z")),
						new StoredMediaObject(blurredKey, 1024L, Instant.parse("2026-08-01T01:00:11Z"))));

		service.ingest("egress-2");
		service.ingest("egress-2");

		assertThat(assetsUnder(prefix)).extracting("objectKey")
				.containsExactlyInAnyOrder(firstKey, secondKey);
		assertThat(recordings.findById(recording.getId()).orElseThrow().getLastIngestedFrameKey())
				.isEqualTo(secondKey);
	}

	/** 테스트끼리 DB 를 공유하므로 해당 녹화 구간의 자산만 본다. */
	private List<MediaAsset> assetsUnder(String prefix) {
		return assets.findAll().stream()
				.filter(asset -> asset.getObjectKey().startsWith(prefix))
				.toList();
	}
}
