package com.ojimasam.safy.live.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LiveSessionTest {
	private final Instant now = Instant.parse("2026-07-22T03:00:00Z");

	@Test
	void createsServerControlledRoomNameAndActivatesOnce() {
		LiveSession session = session();

		assertThat(session.getRoomName()).isEqualTo("report-" + session.getId());
		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.CREATED);

		session.activate(now.plusSeconds(1));
		session.activate(now.plusSeconds(2));

		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
		assertThat(session.getStartedAt()).isEqualTo(now.plusSeconds(1));
	}

	@Test
	void rejectsJoinAfterExpiry() {
		LiveSession session = session();

		assertThatThrownBy(() -> session.activate(now.plus(Duration.ofMinutes(30))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("만료");
	}

	@Test
	void endingIsIdempotent() {
		LiveSession session = session();

		assertThat(session.end(now.plusSeconds(1))).isTrue();
		assertThat(session.end(now.plusSeconds(2))).isFalse();
		assertThat(session.getEndedAt()).isEqualTo(now.plusSeconds(1));
	}

	@Test
	void onlyOwnerCanControlSession() {
		assertThatThrownBy(() -> session().requireOwnedBy(2L))
				.isInstanceOf(LiveSessionAccessDeniedException.class);
	}

	@Test
	void retriesCreationFailureWithSameRoom() {
		LiveSession session = session();
		String roomName = session.getRoomName();
		session.markFailed("temporary failure", now.plusSeconds(1));

		session.retryCreation(now.plusSeconds(2), Duration.ofMinutes(30));

		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.CREATED);
		assertThat(session.getRoomName()).isEqualTo(roomName);
		assertThat(session.getExpiresAt()).isEqualTo(now.plusSeconds(2).plus(Duration.ofMinutes(30)));
	}

	@Test
	void doesNotRetryFailureAfterStreamingStarted() {
		LiveSession session = session();
		session.activate(now.plusSeconds(1));
		session.markFailed("close failure", now.plusSeconds(2));

		assertThatThrownBy(() -> session.retryCreation(now.plusSeconds(3), Duration.ofMinutes(30)))
				.isInstanceOf(IllegalStateException.class);
	}

	private LiveSession session() {
		return LiveSession.create(10L, 1L, now, Duration.ofMinutes(30));
	}
}
