package com.ojimasam.safy.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;

@ExtendWith(MockitoExtension.class)
class LiveWebhookServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");
	private static final Long REPORT_ID = 10L;
	private static final Long REPORTER_ID = 7L;

	@Mock LiveWebhookParser parser;
	@Mock LiveSessionRepository repository;
	@Mock ReportRealtimeNotifier realtimeNotifier;
	@Mock LiveRecordingService recordingService;

	private LiveWebhookService service;

	@BeforeEach
	void setUp() {
		service = new LiveWebhookService(parser, repository, realtimeNotifier, recordingService,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void reporterLeaveStartsGraceInsteadOfEndingSession() {
		LiveSession session = activeSession();
		stub(session, LiveWebhookEvent.Type.PARTICIPANT_LEFT, "reporter-" + REPORTER_ID);

		service.handle("body", "signature");

		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.DISCONNECTED);
		assertThat(session.getDisconnectedAt()).isEqualTo(NOW);
		verify(realtimeNotifier).liveDisconnected(REPORT_ID);
	}

	@Test
	void operatorLeaveDoesNotAffectSession() {
		LiveSession session = activeSession();
		stub(session, LiveWebhookEvent.Type.PARTICIPANT_LEFT, "operator-99");

		service.handle("body", "signature");

		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
		verify(realtimeNotifier, never()).liveDisconnected(any());
	}

	@Test
	void reporterRejoinRestoresActiveSession() {
		LiveSession session = activeSession();
		session.markDisconnected(NOW.minusSeconds(10));
		stub(session, LiveWebhookEvent.Type.PARTICIPANT_JOINED, "reporter-" + REPORTER_ID);

		service.handle("body", "signature");

		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
		assertThat(session.getDisconnectedAt()).isNull();
		verify(realtimeNotifier).liveReconnected(REPORT_ID);
	}

	@Test
	void roomFinishedEndsSession() {
		LiveSession session = activeSession();
		stub(session, LiveWebhookEvent.Type.ROOM_FINISHED, null);

		service.handle("body", "signature");

		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.ENDED);
		verify(realtimeNotifier).liveEndedToOperator(REPORT_ID);
	}

	@Test
	void rejectsUnverifiedWebhook() {
		when(parser.parse("body", "bad")).thenThrow(new LiveMediaException("bad signature"));

		assertThatThrownBy(() -> service.handle("body", "bad"))
				.isInstanceOf(BusinessException.class);
	}

	private void stub(LiveSession session, LiveWebhookEvent.Type type, String identity) {
		when(parser.parse("body", "signature"))
				.thenReturn(new LiveWebhookEvent(type, session.getRoomName(), identity));
		when(repository.findByRoomName(session.getRoomName())).thenReturn(Optional.of(session));
	}

	private LiveSession activeSession() {
		LiveSession session = LiveSession.create(REPORT_ID, REPORTER_ID, NOW.minusSeconds(60), Duration.ofMinutes(30));
		session.activate(NOW.minusSeconds(60));
		return session;
	}
}
