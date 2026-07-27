package com.ojimasam.safy.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.LiveSessionStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.application.OperatorReportAccessService;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;

@ExtendWith(MockitoExtension.class)
class LiveSessionServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-22T03:00:00Z");

	@Mock LiveSessionRepository repository;
	@Mock LiveMediaGateway mediaGateway;
	@Mock ReportRepository reportRepository;
	@Mock OperatorReportAccessService operatorAccessService;
	@Mock ReportOperatorAssignmentService assignmentService;
	@Mock ReportRealtimeNotifier realtimeNotifier;
	@Mock LiveRecordingService recordingService;

	private LiveSessionService service;

	@BeforeEach
	void setUp() {
		OpenViduProperties properties = new OpenViduProperties(
				"ws://openvidu:7880", "key", "secret", Duration.ofMinutes(10),
				Duration.ofMinutes(30), Duration.ofMinutes(1), Duration.ofSeconds(60));
		service = new LiveSessionService(repository, mediaGateway, reportRepository,
				operatorAccessService, assignmentService, realtimeNotifier, recordingService, properties,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsRoomForExistingLiveReport() {
		Report report = report();
		when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(repository.save(any(LiveSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LiveSessionView result = service.create(command());

		assertThat(result.reportId()).isEqualTo(10L);
		verify(mediaGateway).createRoom("report-" + result.streamRoomId());
		verify(realtimeNotifier).liveReady(10L, result.streamRoomId().toString());
	}

	@Test
	void returnsExistingCreatedSessionWithoutCreatingAnotherRoom() {
		Report report = report();
		LiveSession session = session();
		when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(repository.findByReportId(10L)).thenReturn(Optional.of(session));

		LiveSessionView result = service.create(command());

		assertThat(result.streamRoomId()).isEqualTo(session.getId());
		verify(mediaGateway, never()).createRoom(any());
	}

	@Test
	void retriesFailedRoomCreationWithSameSession() {
		Report report = report();
		AtomicReference<LiveSession> saved = new AtomicReference<>();
		when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
		when(repository.findByReportId(10L))
				.thenAnswer(invocation -> Optional.ofNullable(saved.get()));
		when(repository.save(any(LiveSession.class))).thenAnswer(invocation -> {
			LiveSession session = invocation.getArgument(0);
			saved.set(session);
			return session;
		});
		doThrow(new LiveMediaException("temporary failure"))
				.doNothing()
				.when(mediaGateway).createRoom(any());

		assertThatThrownBy(() -> service.create(command()))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE));
		UUID failedSessionId = saved.get().getId();
		assertThat(saved.get().getStatus()).isEqualTo(LiveSessionStatus.FAILED);

		LiveSessionView retried = service.create(command());

		assertThat(retried.streamRoomId()).isEqualTo(failedSessionId);
		assertThat(saved.get().getStatus()).isEqualTo(LiveSessionStatus.CREATED);
		verify(mediaGateway, times(2)).createRoom("report-" + failedSessionId);
		verify(realtimeNotifier).liveReady(10L, failedSessionId.toString());
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
		verify(realtimeNotifier).liveActive(10L, session.getId().toString());
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
	void assignedOperatorReceivesViewerToken() {
		LiveSession session = session();
		when(repository.findById(session.getId())).thenReturn(Optional.of(session));
		when(mediaGateway.issueToken(session.getRoomName(), "operator-30", LiveParticipantRole.VIEWER,
				Duration.ofMinutes(10))).thenReturn("viewer-jwt");
		when(mediaGateway.serverUrl()).thenReturn("ws://openvidu:7880");

		LiveTokenView token = service.issueViewerToken(30L, session.getId());

		assertThat(token.token()).isEqualTo("viewer-jwt");
		verify(operatorAccessService).requireAccess(30L, 10L);
	}

	@Test
	void operatorWithoutAssignmentCannotReceiveViewerToken() {
		LiveSession session = session();
		when(repository.findById(session.getId())).thenReturn(Optional.of(session));
		doThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
				.when(operatorAccessService).requireAccess(30L, 10L);

		assertThatThrownBy(() -> service.issueViewerToken(30L, session.getId()))
				.isInstanceOf(BusinessException.class);
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
		return new CreateLiveSessionCommand(1L, 10L);
	}

	private LiveSession session() {
		return LiveSession.create(10L, 1L, NOW, Duration.ofMinutes(30));
	}

	private Report report() {
		Report report = Report.create(1L, ReportType.LIVE,
				ReportCategory.ACCIDENT_RISK, ReportSubcategory.FIRE_SMOKE, "화재 현장",
				new BigDecimal("37.5665000"), new BigDecimal("126.9780000"), "서울");
		ReflectionTestUtils.setField(report, "id", 10L);
		return report;
	}
}
