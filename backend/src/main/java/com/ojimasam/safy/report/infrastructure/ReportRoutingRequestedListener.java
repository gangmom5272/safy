package com.ojimasam.safy.report.infrastructure;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ojimasam.safy.report.application.ReportRoutingProcessor;
import com.ojimasam.safy.report.application.event.ReportRoutingRequestedEvent;

@Component
public class ReportRoutingRequestedListener {
	private final ReportRoutingProcessor processor;

	public ReportRoutingRequestedListener(ReportRoutingProcessor processor) {
		this.processor = processor;
	}

	@Async("reportRoutingExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(ReportRoutingRequestedEvent event) {
		processor.process(event.reportId());
	}
}
