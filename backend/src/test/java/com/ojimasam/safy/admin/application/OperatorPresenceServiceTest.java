package com.ojimasam.safy.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import com.ojimasam.safy.admin.application.dto.OperatorPresenceResponse;
import com.ojimasam.safy.admin.application.event.OperatorUnavailableEvent;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;

class OperatorPresenceServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	private final OperatorPresenceRepository repository = mock(OperatorPresenceRepository.class);
	private final OperatorPresenceRegistrar registrar = mock(OperatorPresenceRegistrar.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final OperatorPresenceService service =
			new OperatorPresenceService(repository, registrar, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void firstConnectionCreatesOnDutyPresence() {
		OperatorPresence created = OperatorPresence.connect(30L, NOW);
		when(repository.findByOperatorId(30L))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(created));

		service.markConnected(30L);

		verify(registrar).create(30L, NOW);
		assertThat(created.getStatus()).isEqualTo(OperatorPresenceStatus.ON_DUTY);
	}

	@Test
	void concurrentFirstConnectionReusesRowCreatedByTheOtherRequest() {
		OperatorPresence createdByOther = OperatorPresence.connect(30L, NOW.minusSeconds(1));
		when(repository.findByOperatorId(30L))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(createdByOther));
		doThrow(new DataIntegrityViolationException("duplicate"))
				.when(registrar).create(30L, NOW);

		service.markConnected(30L);

		assertThat(createdByOther.getStatus()).isEqualTo(OperatorPresenceStatus.ON_DUTY);
		assertThat(createdByOther.getLastSeenAt()).isEqualTo(NOW);
	}

	@Test
	void reconnectionRestoresOfflineOperatorToOnDuty() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW.minusSeconds(600));
		presence.change(OperatorPresenceStatus.OFFLINE, NOW.minusSeconds(300));
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));

		service.markConnected(30L);

		assertThat(presence.getStatus()).isEqualTo(OperatorPresenceStatus.ON_DUTY);
		assertThat(presence.getLastSeenAt()).isEqualTo(NOW);
	}

	@Test
	void reconnectionKeepsManualAwayStatus() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW.minusSeconds(600));
		presence.change(OperatorPresenceStatus.AWAY, NOW.minusSeconds(300));
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));

		service.markConnected(30L);

		assertThat(presence.getStatus()).isEqualTo(OperatorPresenceStatus.AWAY);
	}

	@Test
	void goingOfflinePublishesEventOnce() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW.minusSeconds(300));
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));

		service.markOffline(30L, NOW.minusSeconds(90));
		service.markOffline(30L, NOW.minusSeconds(90));

		assertThat(presence.getStatus()).isEqualTo(OperatorPresenceStatus.OFFLINE);
		verify(eventPublisher).publishEvent(
				new OperatorUnavailableEvent(30L, OperatorPresenceStatus.OFFLINE));
	}

	@Test
	void markOfflineSkipsOperatorSeenAfterCutoff() {
		// 스위퍼가 스냅샷으로 판단한 뒤 재접속(markConnected 커밋)한 경우 — 내리면 안 된다.
		OperatorPresence presence = OperatorPresence.connect(30L, NOW.minusSeconds(10));
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));

		service.markOffline(30L, NOW.minusSeconds(90));

		assertThat(presence.getStatus()).isEqualTo(OperatorPresenceStatus.ON_DUTY);
		verify(eventPublisher, never()).publishEvent(any(OperatorUnavailableEvent.class));
	}

	@Test
	void manualChangeToOfflinePublishesEvent() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW);
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));

		OperatorPresenceResponse response = service.changeStatus(30L, OperatorPresenceStatus.OFFLINE);

		assertThat(response.status()).isEqualTo(OperatorPresenceStatus.OFFLINE);
		verify(eventPublisher).publishEvent(
				new OperatorUnavailableEvent(30L, OperatorPresenceStatus.OFFLINE));
	}

	@Test
	void manualChangeToAwayPublishesEventSoUnconfirmedAssignmentsAreReleased() {
		// 자리를 비운 사람이 확인도 안 한 제보를 들고 있으면 5분 타임아웃까지 아무도 보지 않는다.
		OperatorPresence presence = OperatorPresence.connect(30L, NOW);
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));

		OperatorPresenceResponse response = service.changeStatus(30L, OperatorPresenceStatus.AWAY);

		assertThat(response.status()).isEqualTo(OperatorPresenceStatus.AWAY);
		verify(eventPublisher).publishEvent(
				new OperatorUnavailableEvent(30L, OperatorPresenceStatus.AWAY));
	}

	@Test
	void returningToOnDutyDoesNotPublishEvent() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW);
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));
		service.changeStatus(30L, OperatorPresenceStatus.AWAY);
		clearInvocations(eventPublisher);

		service.changeStatus(30L, OperatorPresenceStatus.ON_DUTY);

		verify(eventPublisher, never()).publishEvent(any(OperatorUnavailableEvent.class));
	}

	@Test
	void repeatingSameUnavailableStatusPublishesOnce() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW);
		when(repository.findByOperatorId(30L)).thenReturn(Optional.of(presence));

		service.changeStatus(30L, OperatorPresenceStatus.AWAY);
		service.changeStatus(30L, OperatorPresenceStatus.AWAY);

		verify(eventPublisher).publishEvent(
				new OperatorUnavailableEvent(30L, OperatorPresenceStatus.AWAY));
	}

	@Test
	void unknownOperatorIsReportedAsOffline() {
		when(repository.findByOperatorId(99L)).thenReturn(Optional.empty());

		assertThat(service.getMine(99L).status()).isEqualTo(OperatorPresenceStatus.OFFLINE);
	}
}
