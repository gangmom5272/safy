package com.ojimasam.safy.report.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;

class ReportOperatorAssignmentRetrySchedulerTest {
	private final ReportOperatorAssignmentService assignmentService =
			mock(ReportOperatorAssignmentService.class);
	private final ReportOperatorAssignmentRetryScheduler scheduler =
			new ReportOperatorAssignmentRetryScheduler(assignmentService);

	@Test
	void keepsReportInOriginalCenterWhenRetryAssignsNobody() {
		when(assignmentService.findUnassignedReportIds(50)).thenReturn(List.of(10L));
		when(assignmentService.retryAutomaticAssignment(10L)).thenReturn(Optional.empty());

		scheduler.retryUnassignedReports();

		verify(assignmentService).retryAutomaticAssignment(10L);
	}

	@Test
	void retriesEveryUnassignedReport() {
		when(assignmentService.findUnassignedReportIds(50)).thenReturn(List.of(10L));
		when(assignmentService.retryAutomaticAssignment(10L))
				.thenReturn(Optional.of(mock(ReportOperatorAssignment.class)));

		scheduler.retryUnassignedReports();

		verify(assignmentService).retryAutomaticAssignment(10L);
	}
}
