package com.ojimasam.safy.report.application;

import org.springframework.stereotype.Service;

import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;

@Service
public class ReportRealtimeNotifier {
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final RealtimeEventService eventService;

	public ReportRealtimeNotifier(ReportOperatorAssignmentRepository assignmentRepository,
			RealtimeEventService eventService) {
		this.assignmentRepository = assignmentRepository;
		this.eventService = eventService;
	}

	public void reportAssigned(ReportOperatorAssignment assignment) {
		eventService.record(assignment.getOperator().getId(), RealtimeEventType.REPORT_ASSIGNED,
				assignment.getReport().getId(), null, null);
	}

	public void reportReceived(Long reportId, Long reporterId) {
		eventService.record(reporterId, RealtimeEventType.REPORT_RECEIVED, reportId, null, null);
	}

	public void reportInProgress(Long reportId, Long reporterId) {
		eventService.recordOncePerReport(reporterId, RealtimeEventType.REPORT_IN_PROGRESS, reportId);
	}

	public void imageReady(Long reportId, Long mediaAssetId) {
		notifyAssignedOperator(RealtimeEventType.IMAGE_READY, reportId, mediaAssetId.toString());
	}

	public void liveReady(Long reportId, String streamRoomId) {
		notifyAssignedOperator(RealtimeEventType.LIVE_READY, reportId, streamRoomId);
	}

	public void liveActive(Long reportId, String streamRoomId) {
		notifyAssignedOperator(RealtimeEventType.LIVE_ACTIVE, reportId, streamRoomId);
	}

	/** 제보자 연결이 끊겨 재접속 대기중 → 배정 담당자에게 알림(타일에 '연결 끊김' 표시). */
	public void liveDisconnected(Long reportId) {
		notifyAssignedOperator(RealtimeEventType.LIVE_DISCONNECTED, reportId, null);
	}

	/** 제보자가 유예 시간 안에 재접속 → 배정 담당자에게 알림. */
	public void liveReconnected(Long reportId) {
		notifyAssignedOperator(RealtimeEventType.LIVE_RECONNECTED, reportId, null);
	}

	/** 제보자가 송출을 종료 → 배정 담당자에게 알림(관제 타일 갱신). */
	public void liveEndedToOperator(Long reportId) {
		notifyAssignedOperator(RealtimeEventType.LIVE_ENDED, reportId, null);
	}

	public void riskAssessmentUpdated(Long reportId) {
		notifyAssignedOperator(RealtimeEventType.RISK_ASSESSMENT_UPDATED, reportId, null);
	}

	/** 담당자 종료로 라이브가 끊김 → 제보자에게 알림(송출 중단 유도). */
	public void liveEndedToReporter(Long reportId, Long reporterId) {
		eventService.record(reporterId, RealtimeEventType.LIVE_ENDED, reportId, null, null);
	}

	/** 담당자가 제보를 종료(CLOSED) → 제보자에게 알림. */
	public void reportClosed(Long reportId, Long reporterId) {
		eventService.record(reporterId, RealtimeEventType.REPORT_CLOSED, reportId, null, null);
	}

	/** 담당자가 제보를 반려(REJECTED) → 제보자에게 알림. */
	public void reportRejected(Long reportId, Long reporterId) {
		eventService.record(reporterId, RealtimeEventType.REPORT_REJECTED, reportId, null, null);
	}

	/**
	 * 담당자가 재배정을 요청 → 제보자에게 알림.
	 * 제보와 라이브 송출은 유지되므로 연결이 끊기지 않는다는 점을 화면에서 안내한다.
	 */
	public void reportReassigned(Long reportId, Long reporterId) {
		eventService.record(reporterId, RealtimeEventType.REPORT_REASSIGNED, reportId, null, null);
	}

	/** 제보자가 제보를 취소(CANCELLED) → 배정돼 있던 담당자에게 알림(관제 타일 회수). */
	public void reportCancelledToOperator(Long reportId, Long operatorId) {
		eventService.record(operatorId, RealtimeEventType.REPORT_CANCELLED, reportId, null, null);
	}

	private void notifyAssignedOperator(RealtimeEventType eventType, Long reportId, String resourceId) {
		assignmentRepository.findByReport_IdAndStatusIn(
				reportId, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES
		).ifPresent(assignment -> eventService.record(
				assignment.getOperator().getId(), eventType, reportId, resourceId, null
		));
	}
}
