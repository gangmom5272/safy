package com.ojimasam.safy.report.infrastructure;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ojimasam.safy.admin.application.event.OperatorUnavailableEvent;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

/**
 * 담당자가 자리비움·오프라인으로 전환되면 아직 확인하지 않은(ASSIGNED) 배정을 즉시
 * 회수해 다른 담당자에게 재배정한다. 자리를 비운 사람이 확인도 안 한 제보를 들고 있으면
 * 5분 타임아웃이 돌 때까지 아무도 보지 않는다.
 *
 * <p>확인 후 처리중(IN_PROGRESS)인 배정은 실제 대응 중일 수 있으므로 즉시 회수하지 않고,
 * 근무 상태 스위퍼가 연결까지 끊긴 채 유예가 지난 뒤에 회수한다.
 */
@Component
public class OperatorUnavailableListener {
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportOperatorAssignmentService assignmentService;

	public OperatorUnavailableListener(ReportOperatorAssignmentRepository assignmentRepository,
			ReportOperatorAssignmentService assignmentService) {
		this.assignmentRepository = assignmentRepository;
		this.assignmentService = assignmentService;
	}

	@Async("reportRoutingExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(OperatorUnavailableEvent event) {
		ReportAssignmentCancelReason reason = cancelReason(event.status());
		if (reason == null) return;

		for (ReportOperatorAssignment assignment : assignmentRepository.findAllByOperator_IdAndStatus(
				event.operatorId(), ReportOperatorAssignmentStatus.ASSIGNED)) {
			assignmentService.releaseStaleAssignment(
					assignment.getReport().getId(),
					event.operatorId(),
					ReportOperatorAssignmentStatus.ASSIGNED,
					reason
			);
		}
	}

	private ReportAssignmentCancelReason cancelReason(OperatorPresenceStatus status) {
		return switch (status) {
			case OFFLINE -> ReportAssignmentCancelReason.OPERATOR_OFFLINE;
			case AWAY -> ReportAssignmentCancelReason.OPERATOR_AWAY;
			// 근무중 전환은 회수 대상이 아니다. 발행되지 않지만 방어적으로 무시한다.
			case ON_DUTY -> null;
		};
	}
}
