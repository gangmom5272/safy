package com.ojimasam.safy.report.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.user.domain.User;

@ExtendWith(MockitoExtension.class)
class ReportRealtimeNotifierTest {
	@Mock ReportOperatorAssignmentRepository assignmentRepository;
	@Mock RealtimeEventService eventService;

	@Test
	void notifiesAssignedOperatorWhenRiskAssessmentChanges() {
		ReportOperatorAssignment assignment = org.mockito.Mockito.mock(ReportOperatorAssignment.class);
		User operator = org.mockito.Mockito.mock(User.class);
		when(operator.getId()).thenReturn(30L);
		when(assignment.getOperator()).thenReturn(operator);
		when(assignmentRepository.findByReport_IdAndStatusIn(
				42L, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES))
				.thenReturn(Optional.of(assignment));

		new ReportRealtimeNotifier(assignmentRepository, eventService).riskAssessmentUpdated(42L);

		verify(eventService).record(30L, RealtimeEventType.RISK_ASSESSMENT_UPDATED, 42L, null, null);
	}
}
