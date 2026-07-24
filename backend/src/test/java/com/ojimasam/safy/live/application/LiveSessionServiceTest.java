package com.ojimasam.safy.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.LiveSessionStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

@ExtendWith(MockitoExtension.class)
class LiveSessionServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-22T03:00:00Z");

	@Mock LiveSessionRepository repository;
	@Mock LiveMediaGateway mediaGateway;
	@Mock ReportRepository reportRepository;

	private LiveSessionService service;

	@BeforeEach
	void setUp() {
		OpenViduProperties properties = new OpenViduProperties(
				"ws://openvidu:7880", "key", "secret", Duration.ofMinutes(10),
				Duration.ofMinutes(30), Duration.ofMinutes(1));
		service = new LiveSessionService(repository, mediaGateway, reportRepository,
				properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsRoomWithServerGeneratedName() {
		when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
			Report report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "id", 10L);
			return report;
		});
		when(repository.save(any(LiveSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LiveSessionView result = service.create(command());

		assertThat(result.reportId()).isEqualTo(10L);
		verify(mediaGateway).createRoom("report-" + result.streamRoomId());
	}

	@Test
	void reporterTokenActivatesSessionAndUsesCameraPublisherRole() {
		LiveSession session = session();
		when(repository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
		when(mediaGateway.issueToken(session.getRoomName(), "reporter-1", LiveParticipantRole.REPORTER,
				Duration.ofMinutes(10))).thenReturn("jwt");
		when(mediaGateway.serverUrl()).thenReturn("ws://openvidu:7880");

		LiveTokenView token = service.issueReporterToken(1L, session.getId());

		assertThat(token.participantType()).isEqualTo(LiveParticipantRole.REPORTER);
		assertThat(token.token()).isEqualTo("jwt");
		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
	}

	@Test
	void rejectsTokenRequestFromAnotherReporter() {
		LiveSession session = session();
		when(repository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

		assertThatThrownBy(() -> service.issueReporterToken(2L, session.getId()))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(LiveSessionErrorCode.ACCESS_DENIED));
		verify(mediaGateway, never()).issueToken(any(), any(), any(), any());
	}

	@Test
	void endIsIdempotentAndClosesRoomOnce() {
		LiveSession session = session();
		when(repository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

		service.end(1L, session.getId());
		service.end(1L, session.getId());

		verify(mediaGateway).closeRoom(session.getRoomName());
		assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.ENDED);
	}

	private CreateLiveSessionCommand command() {
		return new CreateLiveSessionCommand(1L, "화재 현장", new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"), "서울");
	}

	private LiveSession session() {
		return LiveSession.create(10L, 1L, NOW, Duration.ofMinutes(30));
	}
}
