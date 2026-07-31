package com.ojimasam.safy.report.infrastructure;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ojimasam.safy.admin.application.event.OperatorWentOfflineEvent;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

/**
 * 담당자가 오프라인으로 전환되면 아직 확인하지 않은(ASSIGNED) 배정을 즉시 회수해
 * 다른 담당자에게 재배정한다. 확인 후 처리중(IN_PROGRESS)인 배정은 실제 대응 중일 수
 * 있으므로 즉시 회수하지 않고, 근무 상태 스위퍼가 유예 후 회수한다.
 */
@Component
public class OperatorWentOfflineListener {
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportOperatorAssignmentService assignmentService;

	public OperatorWentOfflineListener(ReportOperatorAssignmentRepository assignmentRepository,
			ReportOperatorAssignmentService assignmentService) {
		this.assignmentRepository = assignmentRepository;
		this.assignmentService = assignmentService;
	}

	@Async("reportRoutingExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(OperatorWentOfflineEvent event) {
		for (ReportOperatorAssignment assignment : assignmentRepository.findAllByOperator_IdAndStatus(
				event.operatorId(), ReportOperatorAssignmentStatus.ASSIGNED)) {
			assignmentService.releaseStaleAssignment(
					assignment.getReport().getId(),
					event.operatorId(),
					ReportOperatorAssignmentStatus.ASSIGNED,
					ReportAssignmentCancelReason.OPERATOR_OFFLINE
			);
		}
	}
}
