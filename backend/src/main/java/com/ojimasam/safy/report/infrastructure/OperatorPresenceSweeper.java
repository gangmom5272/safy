package com.ojimasam.safy.report.infrastructure;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.admin.application.OperatorPresenceProperties;
import com.ojimasam.safy.admin.application.OperatorPresenceService;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;
import com.ojimasam.safy.realtime.application.SseEventBroadcaster;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

/**
 * 담당자 근무 상태를 SSE 연결 생존 여부로 갱신한다.
 * 로그아웃 없이 브라우저를 닫거나 네트워크가 끊긴 담당자를 잡기 위한 안전망이며,
 * 오프라인이 길어지면 처리중(IN_PROGRESS) 배정까지 회수한다.
 * 제보에 직접 손대는 회수 로직이 있어 report 인프라에 둔다.
 */
@Component
public class OperatorPresenceSweeper {
	private final OperatorPresenceRepository presenceRepository;
	private final OperatorPresenceService presenceService;
	private final SseEventBroadcaster broadcaster;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportOperatorAssignmentService assignmentService;
	private final OperatorPresenceProperties properties;
	private final Clock clock;

	@Autowired
	public OperatorPresenceSweeper(OperatorPresenceRepository presenceRepository,
			OperatorPresenceService presenceService,
			SseEventBroadcaster broadcaster,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportOperatorAssignmentService assignmentService,
			OperatorPresenceProperties properties) {
		this(presenceRepository, presenceService, broadcaster, assignmentRepository, assignmentService,
				properties, Clock.systemUTC());
	}

	OperatorPresenceSweeper(OperatorPresenceRepository presenceRepository,
			OperatorPresenceService presenceService,
			SseEventBroadcaster broadcaster,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportOperatorAssignmentService assignmentService,
			OperatorPresenceProperties properties,
			Clock clock) {
		this.presenceRepository = presenceRepository;
		this.presenceService = presenceService;
		this.broadcaster = broadcaster;
		this.assignmentRepository = assignmentRepository;
		this.assignmentService = assignmentService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.presence.sweep-delay:PT30S}")
	public void sweep() {
		Instant now = clock.instant();
		Instant offlineCutoff = now.minus(properties.offlineGrace());
		for (OperatorPresence presence : presenceRepository.findAllByStatusNot(OperatorPresenceStatus.OFFLINE)) {
			if (broadcaster.isConnected(presence.getOperatorId())) {
				presenceService.touch(presence.getOperatorId());
			} else if (presence.getLastSeenAt().isBefore(offlineCutoff)) {
				// 스냅샷 조회와 전환 사이에 재접속했을 수 있으므로, 전환 트랜잭션 안에서
				// 기준 시각(offlineCutoff)으로 최근 접속을 재검증한다.
				// 전환 이벤트가 ASSIGNED 배정 회수를 트리거한다.
				presenceService.markOffline(presence.getOperatorId(), offlineCutoff);
			}
		}

		// 오프라인 유예까지 지나면 확인 후 방치된 처리중 배정도 회수한다.
		Instant inProgressCutoff = now.minus(properties.inProgressReleaseGrace());
		for (OperatorPresence presence : presenceRepository.findAllByStatusAndLastSeenAtBefore(
				OperatorPresenceStatus.OFFLINE, inProgressCutoff)) {
			// 오프라인 행이어도 연결이 살아 있으면(수동 오프라인 뒤 마무리 작업 등) 회수하지 않는다.
			if (broadcaster.isConnected(presence.getOperatorId())) {
				presenceService.touch(presence.getOperatorId());
				continue;
			}
			for (ReportOperatorAssignment assignment : assignmentRepository.findAllByOperator_IdAndStatus(
					presence.getOperatorId(), ReportOperatorAssignmentStatus.IN_PROGRESS)) {
				// 오프라인 표시 이후에 착수한 건(큐 claim 등)까지 뺏지 않도록 착수 시각에도 유예를 준다.
				Instant startedAt = assignment.getStartedAt() != null
						? assignment.getStartedAt()
						: assignment.getAssignedAt();
				if (!startedAt.isBefore(inProgressCutoff)) continue;
				assignmentService.releaseStaleAssignment(
						assignment.getReport().getId(),
						presence.getOperatorId(),
						ReportOperatorAssignmentStatus.IN_PROGRESS,
						ReportAssignmentCancelReason.OPERATOR_OFFLINE
				);
			}
		}
	}
}
