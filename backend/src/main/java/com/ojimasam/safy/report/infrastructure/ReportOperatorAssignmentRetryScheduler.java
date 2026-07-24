package com.ojimasam.safy.report.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;

@Component
public class ReportOperatorAssignmentRetryScheduler {
	private final ReportOperatorAssignmentService assignmentService;

	public ReportOperatorAssignmentRetryScheduler(ReportOperatorAssignmentService assignmentService) {
		this.assignmentService = assignmentService;
	}

	@Scheduled(fixedDelayString = "${app.report-assignment.scheduler-delay:PT30S}")
	public void retryUnassignedReports() {
		for (Long reportId : assignmentService.findUnassignedReportIds(50)) {
			assignmentService.retryAutomaticAssignment(reportId);
		}
	}
}
