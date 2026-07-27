package com.ojimasam.safy.live.infrastructure;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ojimasam.safy.live.application.LiveSessionService;
import com.ojimasam.safy.report.application.ReportTerminatedEvent;

/**
 * 제보가 담당자에 의해 종결(CLOSED/REJECTED)되면, 진행 중인 라이브 세션을 종료하고
 * 제보자에게 통지한다. 제보 종료 트랜잭션이 커밋된 뒤 비동기로 처리한다.
 */
@Component
public class LiveSessionTerminationListener {
	private final LiveSessionService liveSessionService;

	public LiveSessionTerminationListener(LiveSessionService liveSessionService) {
		this.liveSessionService = liveSessionService;
	}

	@Async("reportRoutingExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(ReportTerminatedEvent event) {
		liveSessionService.endByReport(event.reportId());
	}
}
