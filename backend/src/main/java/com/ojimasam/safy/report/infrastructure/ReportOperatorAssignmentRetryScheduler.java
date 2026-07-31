package com.ojimasam.safy.report.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.application.ReportTransferService;

@Component
public class ReportOperatorAssignmentRetryScheduler {
	private final ReportOperatorAssignmentService assignmentService;
	private final ReportTransferService transferService;

	public ReportOperatorAssignmentRetryScheduler(ReportOperatorAssignmentService assignmentService,
			ReportTransferService transferService) {
		this.assignmentService = assignmentService;
		this.transferService = transferService;
	}

	@Scheduled(fixedDelayString = "${app.report-assignment.scheduler-delay:PT30S}")
	public void retryUnassignedReports() {
		for (Long reportId : assignmentService.findUnassignedReportIds(50)) {
			boolean assigned = assignmentService.retryAutomaticAssignment(reportId).isPresent();
			// 센터에 근무중 담당자가 아무도 없으면 백업 센터로 자동 이관을 시도한다.
			if (!assigned) transferService.failoverIfCenterUnavailable(reportId);
		}
	}
}
