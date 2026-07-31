package com.ojimasam.safy.report.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.admin.application.OperatorPresenceProperties;
import com.ojimasam.safy.admin.application.OperatorPresenceService;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;
import com.ojimasam.safy.realtime.application.SseEventBroadcaster;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

class OperatorPresenceSweeperTest {
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
	private static final Duration OFFLINE_GRACE = Duration.ofSeconds(90);
	private static final Duration IN_PROGRESS_GRACE = Duration.ofMinutes(3);

	private final OperatorPresenceRepository presenceRepository = mock(OperatorPresenceRepository.class);
	private final OperatorPresenceService presenceService = mock(OperatorPresenceService.class);
	private final SseEventBroadcaster broadcaster = mock(SseEventBroadcaster.class);
	private final ReportOperatorAssignmentRepository assignmentRepository =
			mock(ReportOperatorAssignmentRepository.class);
	private final ReportOperatorAssignmentService assignmentService =
			mock(ReportOperatorAssignmentService.class);
	private final OperatorPresenceSweeper sweeper = new OperatorPresenceSweeper(
			presenceRepository,
			presenceService,
			broadcaster,
			assignmentRepository,
			assignmentService,
			new OperatorPresenceProperties(OFFLINE_GRACE, IN_PROGRESS_GRACE),
			Clock.fixed(NOW, ZoneOffset.UTC)
	);

	@Test
	void touchesConnectedOperatorInsteadOfMarkingOffline() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW.minusSeconds(600));
		when(presenceRepository.findAllByStatusNot(OperatorPresenceStatus.OFFLINE))
				.thenReturn(List.of(presence));
		when(broadcaster.isConnected(30L)).thenReturn(true);

		sweeper.sweep();

		verify(presenceService).touch(30L);
		verify(presenceService, never()).markOffline(any(), any());
	}

	@Test
	void marksDisconnectedOperatorOfflineAfterGraceWithCutoffRevalidation() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW.minusSeconds(600));
		when(presenceRepository.findAllByStatusNot(OperatorPresenceStatus.OFFLINE))
				.thenReturn(List.of(presence));
		when(broadcaster.isConnected(30L)).thenReturn(false);

		sweeper.sweep();

		// 전환 트랜잭션이 같은 기준 시각으로 재검증할 수 있게 컷오프를 넘긴다.
		verify(presenceService).markOffline(30L, NOW.minus(OFFLINE_GRACE));
	}

	@Test
	void leavesDisconnectedOperatorAloneWithinGrace() {
		OperatorPresence presence = OperatorPresence.connect(30L, NOW.minusSeconds(30));
		when(presenceRepository.findAllByStatusNot(OperatorPresenceStatus.OFFLINE))
				.thenReturn(List.of(presence));
		when(broadcaster.isConnected(30L)).thenReturn(false);

		sweeper.sweep();

		verify(presenceService, never()).touch(any());
		verify(presenceService, never()).markOffline(any(), any());
	}

	@Test
	void releasesInProgressAssignmentOfLongOfflineOperator() {
		OperatorPresence offline = offlinePresence(30L, NOW.minusSeconds(600));
		when(presenceRepository.findAllByStatusAndLastSeenAtBefore(
				eq(OperatorPresenceStatus.OFFLINE), eq(NOW.minus(IN_PROGRESS_GRACE))))
				.thenReturn(List.of(offline));
		when(broadcaster.isConnected(30L)).thenReturn(false);
		ReportOperatorAssignment staleAssignment = inProgressAssignment(10L, NOW.minusSeconds(600));
		when(assignmentRepository.findAllByOperator_IdAndStatus(30L, ReportOperatorAssignmentStatus.IN_PROGRESS))
				.thenReturn(List.of(staleAssignment));

		sweeper.sweep();

		verify(assignmentService).releaseStaleAssignment(
				10L, 30L, ReportOperatorAssignmentStatus.IN_PROGRESS,
				ReportAssignmentCancelReason.OPERATOR_OFFLINE);
	}

	@Test
	void keepsInProgressAssignmentStartedAfterCutoff() {
		// 오프라인 표시 상태로 큐에서 claim 해 방금 착수한 건은 뺏지 않는다.
		OperatorPresence offline = offlinePresence(30L, NOW.minusSeconds(600));
		when(presenceRepository.findAllByStatusAndLastSeenAtBefore(any(), any()))
				.thenReturn(List.of(offline));
		when(broadcaster.isConnected(30L)).thenReturn(false);
		ReportOperatorAssignment freshAssignment = inProgressAssignment(10L, NOW.minusSeconds(10));
		when(assignmentRepository.findAllByOperator_IdAndStatus(30L, ReportOperatorAssignmentStatus.IN_PROGRESS))
				.thenReturn(List.of(freshAssignment));

		sweeper.sweep();

		verify(assignmentService, never()).releaseStaleAssignment(any(), any(), any(), any());
	}

	@Test
	void skipsInProgressReleaseWhileConnectionIsAlive() {
		// 수동 오프라인 뒤 화면을 켜 둔 채 마무리 작업 중인 담당자의 배정은 회수하지 않는다.
		OperatorPresence offline = offlinePresence(30L, NOW.minusSeconds(600));
		when(presenceRepository.findAllByStatusAndLastSeenAtBefore(any(), any()))
				.thenReturn(List.of(offline));
		when(broadcaster.isConnected(30L)).thenReturn(true);

		sweeper.sweep();

		verify(presenceService).touch(30L);
		verify(assignmentService, never()).releaseStaleAssignment(any(), any(), any(), any());
	}

	private OperatorPresence offlinePresence(Long operatorId, Instant lastSeenAt) {
		OperatorPresence presence = OperatorPresence.connect(operatorId, lastSeenAt);
		presence.change(OperatorPresenceStatus.OFFLINE, lastSeenAt);
		return presence;
	}

	private ReportOperatorAssignment inProgressAssignment(Long reportId, Instant startedAt) {
		Report report = mock(Report.class);
		when(report.getId()).thenReturn(reportId);
		ReportOperatorAssignment assignment = mock(ReportOperatorAssignment.class);
		when(assignment.getReport()).thenReturn(report);
		when(assignment.getStartedAt()).thenReturn(startedAt);
		return assignment;
	}
}
