package com.ojimasam.safy.report.infrastructure;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.report.application.ReportAssignmentProperties;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.user.domain.User;

class ReportAssignmentTimeoutSchedulerTest {
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	private final ReportOperatorAssignmentRepository assignmentRepository =
			mock(ReportOperatorAssignmentRepository.class);
	private final ReportOperatorAssignmentService assignmentService =
			mock(ReportOperatorAssignmentService.class);
	private final ReportAssignmentTimeoutScheduler scheduler = new ReportAssignmentTimeoutScheduler(
			assignmentRepository,
			assignmentService,
			new ReportAssignmentProperties(Duration.ofMinutes(5)),
			Clock.fixed(NOW, ZoneOffset.UTC)
	);

	@Test
	void releasesAssignmentsUnviewedPastTimeout() {
		Report report = mock(Report.class);
		when(report.getId()).thenReturn(10L);
		User operator = mock(User.class);
		when(operator.getId()).thenReturn(30L);
		ReportOperatorAssignment stale = mock(ReportOperatorAssignment.class);
		when(stale.getReport()).thenReturn(report);
		when(stale.getOperator()).thenReturn(operator);
		when(assignmentRepository.findTop50ByStatusAndAssignedAtBeforeOrderByAssignedAtAsc(
				eq(ReportOperatorAssignmentStatus.ASSIGNED), eq(NOW.minus(Duration.ofMinutes(5)))))
				.thenReturn(List.of(stale));

		scheduler.releaseTimedOutAssignments();

		verify(assignmentService).releaseStaleAssignment(
				10L, 30L, ReportOperatorAssignmentStatus.ASSIGNED,
				ReportAssignmentCancelReason.ASSIGNMENT_TIMEOUT);
	}

	@Test
	void doesNothingWhenNoAssignmentIsStale() {
		when(assignmentRepository.findTop50ByStatusAndAssignedAtBeforeOrderByAssignedAtAsc(
				eq(ReportOperatorAssignmentStatus.ASSIGNED), eq(NOW.minus(Duration.ofMinutes(5)))))
				.thenReturn(List.of());

		scheduler.releaseTimedOutAssignments();

		verifyNoInteractions(assignmentService);
	}
}
