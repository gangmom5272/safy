package com.ojimasam.safy.live.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.LiveSessionStatus;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;

/**
 * 재접속 유예 시간이 지난 DISCONNECTED 세션을 종료해 좀비 세션을 정리한다.
 * 유예 안에 제보자가 돌아오면 세션은 ACTIVE로 복귀하므로 여기 걸리지 않는다.
 */
@Service
public class DisconnectedLiveSessionSweeper {
	private static final Logger log = LoggerFactory.getLogger(DisconnectedLiveSessionSweeper.class);

	private final LiveSessionRepository repository;
	private final LiveMediaGateway mediaGateway;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final LiveRecordingService recordingService;
	private final OpenViduProperties properties;
	private final Clock clock;

	@Autowired
	public DisconnectedLiveSessionSweeper(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRealtimeNotifier realtimeNotifier, LiveRecordingService recordingService,
			OpenViduProperties properties) {
		this(repository, mediaGateway, realtimeNotifier, recordingService, properties, Clock.systemUTC());
	}

	DisconnectedLiveSessionSweeper(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRealtimeNotifier realtimeNotifier, LiveRecordingService recordingService,
			OpenViduProperties properties, Clock clock) {
		this.repository = repository;
		this.mediaGateway = mediaGateway;
		this.realtimeNotifier = realtimeNotifier;
		this.recordingService = recordingService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${openvidu.disconnect-sweep-delay:PT20S}")
	@Transactional
	public void sweep() {
		Instant now = clock.instant();
		Instant threshold = now.minus(properties.disconnectGrace());
		for (LiveSession session : repository.findTop100ByStatusAndDisconnectedAtBeforeOrderByDisconnectedAtAsc(
				LiveSessionStatus.DISCONNECTED, threshold)) {
			recordingService.stopForSession(session.getId());
			try {
				mediaGateway.closeRoom(session.getRoomName());
				if (session.end(now)) realtimeNotifier.liveEndedToOperator(session.getReportId());
			} catch (LiveMediaException exception) {
				session.markFailed(exception.getMessage(), now);
				log.warn("Failed to close disconnected live session {}", session.getId(), exception);
			}
		}
	}
}
