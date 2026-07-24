package com.ojimasam.safy.report.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.report.application.ReportRoutingProcessor;
import com.ojimasam.safy.report.application.ReportRoutingTransactionService;

@Component
public class ReportRoutingRetryScheduler {
	private final ReportRoutingTransactionService transactionService;
	private final ReportRoutingProcessor processor;

	public ReportRoutingRetryScheduler(
			ReportRoutingTransactionService transactionService,
			ReportRoutingProcessor processor
	) {
		this.transactionService = transactionService;
		this.processor = processor;
	}

	@Scheduled(fixedDelayString = "${app.report-routing.scheduler-delay:PT30S}")
	public void retryPendingReports() {
		for (Long reportId : transactionService.findDueReportIds()) {
			processor.process(reportId);
		}
	}
}
