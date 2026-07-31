package com.ojimasam.safy.report.infrastructure;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.report.application.ReportAssignmentProperties;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

/**
 * 배정된 뒤 유예 시간 안에 한 번도 확인되지 않은(ASSIGNED 그대로) 배정을 회수해
 * 재배정 대상으로 되돌린다. 근무중이지만 알림을 놓친 담당자를 잡는 안전망이다.
 * 확인 후 처리중(IN_PROGRESS)인 배정은 실제 대응 중일 수 있으므로 대상에서 제외한다.
 */
@Component
public class ReportAssignmentTimeoutScheduler {
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportOperatorAssignmentService assignmentService;
	private final ReportAssignmentProperties properties;
	private final Clock clock;

	@Autowired
	public ReportAssignmentTimeoutScheduler(ReportOperatorAssignmentRepository assignmentRepository,
			ReportOperatorAssignmentService assignmentService,
			ReportAssignmentProperties properties) {
		this(assignmentRepository, assignmentService, properties, Clock.systemUTC());
	}

	ReportAssignmentTimeoutScheduler(ReportOperatorAssignmentRepository assignmentRepository,
			ReportOperatorAssignmentService assignmentService,
			ReportAssignmentProperties properties,
			Clock clock) {
		this.assignmentRepository = assignmentRepository;
		this.assignmentService = assignmentService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.report-assignment.timeout-sweep-delay:PT30S}")
	public void releaseTimedOutAssignments() {
		Instant cutoff = clock.instant().minus(properties.assignedTimeout());
		for (ReportOperatorAssignment assignment : assignmentRepository
				.findTop50ByStatusAndAssignedAtBeforeOrderByAssignedAtAsc(
						ReportOperatorAssignmentStatus.ASSIGNED, cutoff)) {
			assignmentService.releaseStaleAssignment(
					assignment.getReport().getId(),
					assignment.getOperator().getId(),
					ReportOperatorAssignmentStatus.ASSIGNED,
					ReportAssignmentCancelReason.ASSIGNMENT_TIMEOUT
			);
		}
	}
}
