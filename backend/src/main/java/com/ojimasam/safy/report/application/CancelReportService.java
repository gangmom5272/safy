package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.report.application.event.ReportTerminatedEvent;

@Service
public class CancelReportService {
	private final ReportRepository reportRepository;
	private final ReportCenterRouteRepository routeRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportStatusHistoryRepository historyRepository;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public CancelReportService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			ReportRealtimeNotifier realtimeNotifier,
			ApplicationEventPublisher eventPublisher
	) {
		this(reportRepository, routeRepository, assignmentRepository, historyRepository, realtimeNotifier,
				eventPublisher, Clock.systemUTC());
	}

	CancelReportService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			ReportRealtimeNotifier realtimeNotifier,
			ApplicationEventPublisher eventPublisher,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.routeRepository = routeRepository;
		this.assignmentRepository = assignmentRepository;
		this.historyRepository = historyRepository;
		this.realtimeNotifier = realtimeNotifier;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public void cancel(Long reporterId, Long reportId) {
		// claim·자동 배정과 같은 행 잠금으로 직렬화한다. Report 에 @Version 이 없어
		// 잠금 없이는 취소(CANCELLED)와 배정(ASSIGNED) 커밋이 서로를 덮어쓸 수 있다.
		Report report = reportRepository.findByIdForUpdate(reportId)
				.filter(found -> found.getReporterId().equals(reporterId))
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		ReportStatus previous = report.getStatus();
		Instant now = clock.instant();
		try {
			report.cancel();
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
		// 배정 담당자에게 회수 알림을 보내려면 배정을 취소하기 전에 담당자 ID를 확보해야 한다.
		Long assignedOperatorId = assignmentRepository
				.findByReport_IdAndStatusIn(reportId, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES)
				.map(assignment -> {
					Long operatorId = assignment.getOperator().getId();
					assignment.cancel(now, ReportAssignmentCancelReason.REPORTER_CANCELLED);
					return operatorId;
				})
				.orElse(null);
		routeRepository.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.ifPresent(route -> route.close(now));
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), null, "REPORTER_CANCELLED", now
		));
		if (assignedOperatorId != null) {
			realtimeNotifier.reportCancelledToOperator(reportId, assignedOperatorId);
		}
		// 진행 중인 라이브 세션이 있으면 종료 이벤트로 닫는다(담당자 종료와 동일 경로).
		eventPublisher.publishEvent(new ReportTerminatedEvent(reportId, report.getReporterId(), report.getStatus()));
	}
}
