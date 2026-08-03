package com.ojimasam.safy.report.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.admin.application.event.OperatorUnavailableEvent;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

class OperatorUnavailableListenerTest {
	private final ReportOperatorAssignmentRepository assignmentRepository =
			mock(ReportOperatorAssignmentRepository.class);
	private final ReportOperatorAssignmentService assignmentService =
			mock(ReportOperatorAssignmentService.class);
	private final OperatorUnavailableListener listener =
			new OperatorUnavailableListener(assignmentRepository, assignmentService);

	@Test
	void awayReleasesUnconfirmedAssignmentWithAwayReason() {
		givenUnconfirmedAssignment(10L, 30L);

		listener.handle(new OperatorUnavailableEvent(30L, OperatorPresenceStatus.AWAY));

		verify(assignmentService).releaseStaleAssignment(
				10L, 30L, ReportOperatorAssignmentStatus.ASSIGNED,
				ReportAssignmentCancelReason.OPERATOR_AWAY);
	}

	@Test
	void offlineReleasesUnconfirmedAssignmentWithOfflineReason() {
		givenUnconfirmedAssignment(10L, 30L);

		listener.handle(new OperatorUnavailableEvent(30L, OperatorPresenceStatus.OFFLINE));

		verify(assignmentService).releaseStaleAssignment(
				10L, 30L, ReportOperatorAssignmentStatus.ASSIGNED,
				ReportAssignmentCancelReason.OPERATOR_OFFLINE);
	}

	@Test
	void onDutyReleasesNothing() {
		listener.handle(new OperatorUnavailableEvent(30L, OperatorPresenceStatus.ON_DUTY));

		verifyNoInteractions(assignmentRepository);
		verifyNoInteractions(assignmentService);
	}

	@Test
	void inProgressAssignmentsAreNotTouched() {
		// 확인 후 처리중인 건은 실제 대응 중일 수 있다. 회수는 스위퍼가 유예 후에 한다.
		when(assignmentRepository.findAllByOperator_IdAndStatus(
				30L, ReportOperatorAssignmentStatus.ASSIGNED)).thenReturn(List.of());

		listener.handle(new OperatorUnavailableEvent(30L, OperatorPresenceStatus.AWAY));

		verify(assignmentService, never()).releaseStaleAssignment(any(), any(), any(), any());
		verify(assignmentRepository, never()).findAllByOperator_IdAndStatus(
				eq(30L), eq(ReportOperatorAssignmentStatus.IN_PROGRESS));
	}

	private void givenUnconfirmedAssignment(Long reportId, Long operatorId) {
		Report report = mock(Report.class);
		when(report.getId()).thenReturn(reportId);
		ReportOperatorAssignment assignment = mock(ReportOperatorAssignment.class);
		when(assignment.getReport()).thenReturn(report);
		when(assignmentRepository.findAllByOperator_IdAndStatus(
				operatorId, ReportOperatorAssignmentStatus.ASSIGNED)).thenReturn(List.of(assignment));
	}
}
