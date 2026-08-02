package com.ojimasam.safy.live.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ojimasam.safy.analysis.domain.MediaProcessingJob;
import com.ojimasam.safy.analysis.domain.MediaProcessingJobRepository;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;
import com.ojimasam.safy.live.domain.ExtractedFrame;
import com.ojimasam.safy.live.domain.ExtractedFrameRepository;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.StreamRecording;
import com.ojimasam.safy.live.domain.StreamRecordingRepository;
import com.ojimasam.safy.live.domain.type.StreamRecordingStatus;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.application.StoredMediaObject;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.type.MediaVariantType;

@Service
public class LiveFrameIngestService {
	private static final Logger log = LoggerFactory.getLogger(LiveFrameIngestService.class);
	private static final Set<StreamRecordingStatus> IN_FLIGHT =
			Set.of(StreamRecordingStatus.STARTING, StreamRecordingStatus.ACTIVE);
	private static final String JPEG = "image/jpeg";

	private final StreamRecordingRepository recordings;
	private final LiveSessionRepository sessions;
	private final MediaAssetRepository assets;
	private final ExtractedFrameRepository frames;
	private final MediaProcessingJobRepository jobs;
	private final MediaObjectStorage storage;
	private final LiveRecordingProperties properties;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public LiveFrameIngestService(StreamRecordingRepository recordings, LiveSessionRepository sessions,
			MediaAssetRepository assets, ExtractedFrameRepository frames, MediaProcessingJobRepository jobs,
			MediaObjectStorage storage, LiveRecordingProperties properties, TransactionTemplate transactions) {
		this.recordings = recordings; this.sessions = sessions; this.assets = assets; this.frames = frames;
		this.jobs = jobs; this.storage = storage; this.properties = properties;
		this.transactions = transactions; this.clock = Clock.systemUTC();
	}

	@Scheduled(fixedDelayString = "${live.recording.frame-poll-delay:PT2S}")
	public void pollActiveRecordings() {
		if (!properties.enabled()) return;
		for (StreamRecording recording : recordings.findTop100ByStatusInOrderByStartedAtAsc(IN_FLIGHT)) {
			ingest(recording.getProviderEgressId());
		}
	}

	public void ingest(String providerEgressId) {
		StreamRecording recording = recordings.findByProviderEgressId(providerEgressId).orElse(null);
		if (recording == null || recording.getFramePrefix() == null) return;
		List<StoredMediaObject> objects;
		try {
			objects = storage.list(properties.bucket(), recording.getFramePrefix(), recording.getLastIngestedFrameKey());
		} catch (RuntimeException exception) {
			log.warn("라이브 프레임 목록 조회에 실패했습니다. egressId={}", providerEgressId, exception);
			return;
		}
		for (StoredMediaObject object : objects) {
			if (object.size() <= 0 || !isJpeg(object.objectKey())) continue;
			transactions.executeWithoutResult(status -> register(providerEgressId, object));
		}
	}

	private void register(String providerEgressId, StoredMediaObject object) {
		StreamRecording recording = recordings.findByProviderEgressIdForUpdate(providerEgressId).orElse(null);
		if (recording == null || !object.objectKey().startsWith(recording.getFramePrefix())) return;
		String cursor = recording.getLastIngestedFrameKey();
		if (cursor != null && object.objectKey().compareTo(cursor) <= 0) return;

		MediaAsset existing = assets.findByBucketNameAndObjectKey(properties.bucket(), object.objectKey()).orElse(null);
		if (existing == null) {
			LiveSession session = sessions.findById(recording.getLiveSessionId()).orElseThrow();
			MediaAsset asset = assets.save(MediaAsset.recordServerObject(session.getReporterId(), recording.getReportId(),
					properties.bucket(), object.objectKey(), null, JPEG, object.size(),
					MediaVariantType.ORIGINAL, null, capturedAt(object)));
			int sequence = frames.findTopByStreamRecordingIdOrderBySequenceNumberDesc(recording.getId())
					.map(frame -> frame.getSequenceNumber() + 1).orElse(0);
			frames.save(ExtractedFrame.create(recording.getId(), asset.getId(), sequence, capturedAt(object)));
			jobs.save(MediaProcessingJob.create(asset.getId(), MediaProcessingJobType.ANALYZE_AND_BLUR, clock.instant()));
		}
		recording.advanceFrameCursor(object.objectKey());
	}

	private Instant capturedAt(StoredMediaObject object) {
		return object.lastModified() == null ? clock.instant() : object.lastModified();
	}

	private boolean isJpeg(String key) {
		String lower = key.toLowerCase();
		return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
	}
}
