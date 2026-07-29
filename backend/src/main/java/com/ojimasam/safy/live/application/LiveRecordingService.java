package com.ojimasam.safy.live.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.StreamRecording;
import com.ojimasam.safy.live.domain.StreamRecordingRepository;
import com.ojimasam.safy.live.domain.type.StreamRecordingStatus;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.type.MediaVariantType;
import com.ojimasam.safy.live.application.dto.LiveEgressResult;
import com.ojimasam.safy.live.application.dto.LiveRecordingRequest;

/**
 * 라이브 원본 녹화의 수명주기를 관리한다.
 * 실제 인코딩·업로드는 미디어 서버(Egress)가 수행하고, 여기서는 시작 지시와 결과 반영만 한다.
 */
@Service
public class LiveRecordingService {
	private static final Logger log = LoggerFactory.getLogger(LiveRecordingService.class);
	private static final Set<StreamRecordingStatus> IN_FLIGHT = Set.of(
			StreamRecordingStatus.STARTING, StreamRecordingStatus.ACTIVE);
	private static final String RECORDING_CONTENT_TYPE = "video/mp4";

	private final StreamRecordingRepository recordingRepository;
	private final LiveSessionRepository sessionRepository;
	private final MediaAssetRepository mediaAssetRepository;
	private final LiveMediaGateway mediaGateway;
	private final LiveRecordingProperties properties;
	private final Clock clock;

	@Autowired
	public LiveRecordingService(StreamRecordingRepository recordingRepository,
			LiveSessionRepository sessionRepository,
			MediaAssetRepository mediaAssetRepository, LiveMediaGateway mediaGateway,
			LiveRecordingProperties properties) {
		this(recordingRepository, sessionRepository, mediaAssetRepository, mediaGateway, properties, Clock.systemUTC());
	}

	LiveRecordingService(StreamRecordingRepository recordingRepository, LiveSessionRepository sessionRepository,
			MediaAssetRepository mediaAssetRepository, LiveMediaGateway mediaGateway,
			LiveRecordingProperties properties, Clock clock) {
		this.recordingRepository = recordingRepository;
		this.sessionRepository = sessionRepository;
		this.mediaAssetRepository = mediaAssetRepository;
		this.mediaGateway = mediaGateway;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 제보자가 실제로 방에 들어와 송출을 시작한 시점에 호출한다. 이미 진행 중인 녹화가 있으면 아무것도 하지 않는다.
	 * 녹화 실패가 라이브 시청 자체를 막아서는 안 되므로 예외를 삼킨다.
	 */
	@Transactional
	public void startForSession(LiveSession session, String participantIdentity) {
		if (!properties.enabled()) return;
		if (recordingRepository.existsByLiveSessionIdAndStatusIn(session.getId(), IN_FLIGHT)) return;

		Instant now = clock.instant();
		String objectKey = recordingObjectKey(session, now);
		try {
			String egressId = mediaGateway.startRecording(new LiveRecordingRequest(
					session.getRoomName(), participantIdentity, objectKey, framePrefix(session)));
			recordingRepository.save(StreamRecording.start(
					session.getId(), session.getReportId(), egressId, objectKey, now));
		} catch (LiveMediaException exception) {
			log.warn("Failed to start recording for live session {}", session.getId(), exception);
		}
	}

	/** 세션이 끝날 때 진행 중인 녹화를 중단시킨다. 완료 처리는 미디어 서버 통지로 이뤄진다. */
	@Transactional
	public void stopForSession(UUID liveSessionId) {
		if (!properties.enabled()) return;
		List<StreamRecording> recordings =
				recordingRepository.findAllByLiveSessionIdAndStatusIn(liveSessionId, IN_FLIGHT);
		for (StreamRecording recording : recordings) {
			try {
				mediaGateway.stopRecording(recording.getProviderEgressId());
			} catch (LiveMediaException exception) {
				log.warn("Failed to stop recording {}", recording.getProviderEgressId(), exception);
			}
		}
	}

	/** 미디어 서버가 녹화 진행을 알렸다. */
	@Transactional
	public void markActive(String providerEgressId) {
		recordingRepository.findByProviderEgressId(providerEgressId).ifPresent(StreamRecording::markActive);
	}

	/**
	 * 녹화가 끝났다. 업로드된 파일을 미디어 자산으로 등록하고 녹화 건을 종료 처리한다.
	 * 중복 통지가 와도 안전하도록 멱등하게 동작한다.
	 */
	@Transactional
	public void complete(String providerEgressId, LiveEgressResult result) {
		StreamRecording recording = recordingRepository.findByProviderEgressId(providerEgressId).orElse(null);
		if (recording == null || recording.isFinished()) return;

		Instant now = clock.instant();
		if (result.failed()) {
			recording.fail(result.error(), now);
			return;
		}
		LiveEgressResult.LiveEgressFile file = result.primaryFile();
		if (file == null || file.sizeBytes() <= 0) {
			recording.abort(now);
			return;
		}
		Long reporterId = sessionRepository.findById(recording.getLiveSessionId())
				.map(LiveSession::getReporterId)
				.orElse(null);
		if (reporterId == null) {
			recording.fail("녹화 대상 세션을 찾을 수 없습니다.", now);
			return;
		}
		MediaAsset asset = mediaAssetRepository.save(MediaAsset.recordServerObject(
				reporterId, recording.getReportId(), properties.bucket(), file.objectKey(),
				null, RECORDING_CONTENT_TYPE, file.sizeBytes(), MediaVariantType.ORIGINAL, null, now));
		recording.complete(asset.getId(), file.durationMs(), now);
	}

	private String recordingObjectKey(LiveSession session, Instant now) {
		return "recordings/%d/%s/%d.mp4".formatted(session.getReportId(), session.getId(), now.toEpochMilli());
	}

	private String framePrefix(LiveSession session) {
		return "frames/%d/%s/".formatted(session.getReportId(), session.getId());
	}
}
