package com.ojimasam.safy.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;

@ExtendWith(MockitoExtension.class)
class ExpiredLiveSessionCleanupServiceTest {
	@Mock LiveSessionRepository repository;
	@Mock LiveMediaGateway mediaGateway;

	@Test
	void closesExpiredRoomAndEndsSession() {
		Instant now = Instant.parse("2026-07-22T03:31:00Z");
		LiveSession session = LiveSession.create(10L, 1L,
				now.minus(Duration.ofMinutes(31)), Duration.ofMinutes(30));
		when(repository.findTop100ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(any(), any()))
				.thenReturn(List.of(session));
		ExpiredLiveSessionCleanupService service = new ExpiredLiveSessionCleanupService(
				repository, mediaGateway, Clock.fixed(now, ZoneOffset.UTC));

		service.cleanup();

		verify(mediaGateway).closeRoom(session.getRoomName());
		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.ENDED);
	}
}
