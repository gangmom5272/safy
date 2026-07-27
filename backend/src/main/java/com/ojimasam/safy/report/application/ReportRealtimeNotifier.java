package com.ojimasam.safy.report.application;

import org.springframework.stereotype.Service;

import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.RealtimeEventType;
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

	public void imageReady(Long reportId, Long mediaAssetId) {
		notifyAssignedOperator(RealtimeEventType.IMAGE_READY, reportId, mediaAssetId.toString());
	}

	public void liveReady(Long reportId, String streamRoomId) {
		notifyAssignedOperator(RealtimeEventType.LIVE_READY, reportId, streamRoomId);
	}

	public void liveActive(Long reportId, String streamRoomId) {
		notifyAssignedOperator(RealtimeEventType.LIVE_ACTIVE, reportId, streamRoomId);
	}

	private void notifyAssignedOperator(RealtimeEventType eventType, Long reportId, String resourceId) {
		assignmentRepository.findByReport_IdAndStatusIn(
				reportId, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES
		).ifPresent(assignment -> eventService.record(
				assignment.getOperator().getId(), eventType, reportId, resourceId, null
		));
	}
}
