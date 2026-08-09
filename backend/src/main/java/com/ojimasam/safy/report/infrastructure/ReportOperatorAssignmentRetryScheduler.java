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
			// 관할 센터 담당자가 없으면 그 센터의 공용 큐에 유지한다. 다른 자치구의
			// BACKUP 센터로 자동 이관하면 제보 지역과 담당자 지역이 달라진다.
			assignmentService.retryAutomaticAssignment(reportId);
		}
	}
}
