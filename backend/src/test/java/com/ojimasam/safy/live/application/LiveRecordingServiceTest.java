package com.ojimasam.safy.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.StreamRecording;
import com.ojimasam.safy.live.domain.StreamRecordingRepository;
import com.ojimasam.safy.live.domain.type.StreamRecordingStatus;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;
import com.ojimasam.safy.media.domain.type.MediaVariantType;
import com.ojimasam.safy.live.application.dto.LiveEgressResult;
import com.ojimasam.safy.live.application.dto.LiveRecordingRequest;

@ExtendWith(MockitoExtension.class)
class LiveRecordingServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");
	private static final Long REPORT_ID = 10L;
	private static final Long REPORTER_ID = 7L;
	private static final String EGRESS_ID = "EG_abc";

	@Mock StreamRecordingRepository recordingRepository;
	@Mock LiveSessionRepository sessionRepository;
	@Mock MediaAssetRepository mediaAssetRepository;
	@Mock LiveMediaGateway mediaGateway;

	private LiveRecordingService service;

	@BeforeEach
	void setUp() {
		service = newService(true);
	}

	private LiveRecordingService newService(boolean enabled) {
		return newService(enabled, 1);
	}

	private LiveRecordingService newService(boolean enabled, int maxAttempts) {
		LiveRecordingProperties properties = new LiveRecordingProperties(
				enabled, "safy", "http://minio:9000", "key", "secret", null, Duration.ofSeconds(5), 1280, 720,
				maxAttempts, Duration.ofMillis(1));
		return new LiveRecordingService(recordingRepository, sessionRepository, mediaAssetRepository,
				mediaGateway, properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void startsRecordingWithBothFileAndFrameOutputs() {
		LiveSession session = session();
		when(recordingRepository.existsByLiveSessionIdAndStatusIn(any(), any())).thenReturn(false);
		when(mediaGateway.startRecording(any())).thenReturn(EGRESS_ID);

		service.startForSession(session, "reporter-" + REPORTER_ID);

		ArgumentCaptor<LiveRecordingRequest> request = ArgumentCaptor.forClass(LiveRecordingRequest.class);
		verify(mediaGateway).startRecording(request.capture());
		assertThat(request.getValue().roomName()).isEqualTo(session.getRoomName());
		assertThat(request.getValue().recordingObjectKey())
				.isEqualTo("recordings/%d/%s/%d.mp4".formatted(REPORT_ID, session.getId(), NOW.toEpochMilli()));
		assertThat(request.getValue().framePrefix())
				.isEqualTo("frames/%d/%s/%d/".formatted(REPORT_ID, session.getId(), NOW.toEpochMilli()));
		verify(recordingRepository).save(any(StreamRecording.class));
	}

	@Test
	void doesNotStartSecondRecordingWhileOneIsInFlight() {
		when(recordingRepository.existsByLiveSessionIdAndStatusIn(any(), any())).thenReturn(true);

		service.startForSession(session(), "reporter-" + REPORTER_ID);

		verify(mediaGateway, never()).startRecording(any());
	}

	@Test
	void skipsRecordingWhenDisabled() {
		newService(false).startForSession(session(), "reporter-" + REPORTER_ID);

		verify(mediaGateway, never()).startRecording(any());
	}

	@Test
	void liveFailureDoesNotPropagate() {
		when(recordingRepository.existsByLiveSessionIdAndStatusIn(any(), any())).thenReturn(false);
		when(mediaGateway.startRecording(any())).thenThrow(new LiveMediaException("egress down"));

		service.startForSession(session(), "reporter-" + REPORTER_ID);

		verify(recordingRepository, never()).save(any());
	}

	@Test
	void retriesAndSucceedsAfterTransientFailure() {
		LiveRecordingService retryingService = newService(true, 3);
		when(recordingRepository.existsByLiveSessionIdAndStatusIn(any(), any())).thenReturn(false);
		when(mediaGateway.startRecording(any()))
				.thenThrow(new LiveMediaException("egress cpu full"))
				.thenReturn(EGRESS_ID);

		retryingService.startForSession(session(), "reporter-" + REPORTER_ID);

		verify(mediaGateway, times(2)).startRecording(any());
		verify(recordingRepository).save(any(StreamRecording.class));
	}

	@Test
	void givesUpAfterExhaustingRetries() {
		LiveRecordingService retryingService = newService(true, 3);
		when(recordingRepository.existsByLiveSessionIdAndStatusIn(any(), any())).thenReturn(false);
		when(mediaGateway.startRecording(any())).thenThrow(new LiveMediaException("egress down"));

		retryingService.startForSession(session(), "reporter-" + REPORTER_ID);

		verify(mediaGateway, times(3)).startRecording(any());
		verify(recordingRepository, never()).save(any());
	}

	@Test
	void completeRegistersUploadedFileAsMediaAsset() {
		LiveSession session = session();
		StreamRecording recording = recording();
		when(recordingRepository.findByProviderEgressId(EGRESS_ID)).thenReturn(Optional.of(recording));
		when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
		when(mediaAssetRepository.save(any())).thenAnswer(invocation -> {
			MediaAsset saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 55L);
			return saved;
		});
		ReflectionTestUtils.setField(recording, "liveSessionId", session.getId());

		service.complete(EGRESS_ID, new LiveEgressResult(EGRESS_ID, false, null, List.of(
				new LiveEgressResult.LiveEgressFile("recordings/10/a.mp4", 2048L, 30_000L))));

		ArgumentCaptor<MediaAsset> asset = ArgumentCaptor.forClass(MediaAsset.class);
		verify(mediaAssetRepository).save(asset.capture());
		assertThat(asset.getValue().getContentType()).isEqualTo("video/mp4");
		assertThat(asset.getValue().getVariantType()).isEqualTo(MediaVariantType.ORIGINAL);
		assertThat(asset.getValue().getStorageStatus()).isEqualTo(MediaStorageStatus.AVAILABLE);
		assertThat(asset.getValue().getOwnerUserId()).isEqualTo(REPORTER_ID);
		assertThat(recording.getStatus()).isEqualTo(StreamRecordingStatus.COMPLETED);
		assertThat(recording.getMediaAssetId()).isEqualTo(55L);
		assertThat(recording.getDurationMs()).isEqualTo(30_000L);
	}

	@Test
	void completeWithoutFileAbortsInsteadOfCreatingAsset() {
		StreamRecording recording = recording();
		when(recordingRepository.findByProviderEgressId(EGRESS_ID)).thenReturn(Optional.of(recording));

		service.complete(EGRESS_ID, new LiveEgressResult(EGRESS_ID, false, null, List.of()));

		assertThat(recording.getStatus()).isEqualTo(StreamRecordingStatus.ABORTED);
		verify(mediaAssetRepository, never()).save(any());
	}

	@Test
	void completeMarksFailureWhenEgressFailed() {
		StreamRecording recording = recording();
		when(recordingRepository.findByProviderEgressId(EGRESS_ID)).thenReturn(Optional.of(recording));

		service.complete(EGRESS_ID, new LiveEgressResult(EGRESS_ID, true, "disk full", List.of()));

		assertThat(recording.getStatus()).isEqualTo(StreamRecordingStatus.FAILED);
		assertThat(recording.getFailureReason()).isEqualTo("disk full");
		verify(mediaAssetRepository, never()).save(any());
	}

	@Test
	void duplicateCompleteIsIgnored() {
		StreamRecording recording = recording();
		recording.abort(NOW);
		when(recordingRepository.findByProviderEgressId(EGRESS_ID)).thenReturn(Optional.of(recording));

		service.complete(EGRESS_ID, new LiveEgressResult(EGRESS_ID, false, null, List.of(
				new LiveEgressResult.LiveEgressFile("recordings/10/a.mp4", 2048L, 30_000L))));

		verify(mediaAssetRepository, never()).save(any());
		assertThat(recording.getStatus()).isEqualTo(StreamRecordingStatus.ABORTED);
	}

	private LiveSession session() {
		LiveSession session = LiveSession.create(REPORT_ID, REPORTER_ID, NOW.minusSeconds(60), Duration.ofMinutes(30));
		session.activate(NOW.minusSeconds(60));
		return session;
	}

	private StreamRecording recording() {
		return StreamRecording.start(java.util.UUID.randomUUID(), REPORT_ID, EGRESS_ID,
				"recordings/10/a.mp4", "frames/10/a/", NOW.minusSeconds(30));
	}
}
