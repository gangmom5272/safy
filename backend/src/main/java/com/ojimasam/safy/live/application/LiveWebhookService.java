package com.ojimasam.safy.live.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;

/**
 * 미디어 서버 웹훅으로 실제 연결 상태를 받아 LiveSession을 정합화한다.
 * 클라이언트가 종료를 알리지 못하고 죽는 경우(앱 크래시·네트워크 단절)를 서버가 감지하는 유일한 창구다.
 */
@Service
public class LiveWebhookService {
	private static final Logger log = LoggerFactory.getLogger(LiveWebhookService.class);
	private static final String REPORTER_IDENTITY_PREFIX = "reporter-";

	private final LiveWebhookParser parser;
	private final LiveSessionRepository repository;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final LiveRecordingService recordingService;
	private final LiveFrameIngestService frameIngestService;
	private final Clock clock;

	@Autowired
	public LiveWebhookService(LiveWebhookParser parser, LiveSessionRepository repository,
			ReportRealtimeNotifier realtimeNotifier, LiveRecordingService recordingService,
			LiveFrameIngestService frameIngestService) {
		this(parser, repository, realtimeNotifier, recordingService, frameIngestService, Clock.systemUTC());
	}

	LiveWebhookService(LiveWebhookParser parser, LiveSessionRepository repository,
			ReportRealtimeNotifier realtimeNotifier, LiveRecordingService recordingService,
			LiveFrameIngestService frameIngestService, Clock clock) {
		this.parser = parser;
		this.repository = repository;
		this.realtimeNotifier = realtimeNotifier;
		this.recordingService = recordingService;
		this.frameIngestService = frameIngestService;
		this.clock = clock;
	}

	@Transactional
	public void handle(String rawBody, String authorizationHeader) {
		LiveWebhookEvent event;
		try {
			event = parser.parse(rawBody, authorizationHeader);
		} catch (LiveMediaException exception) {
			log.warn("Rejected live webhook: {}", exception.getMessage());
			throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "웹훅 서명 검증에 실패했습니다.");
		}
		if (event.type() == LiveWebhookEvent.Type.UNSUPPORTED || event.roomName() == null) return;

		LiveSession session = repository.findByRoomName(event.roomName()).orElse(null);
		if (session == null) return;

		Instant now = clock.instant();
		switch (event.type()) {
			case PARTICIPANT_LEFT -> {
				if (isReporter(session, event) && session.markDisconnected(now)) {
					realtimeNotifier.liveDisconnected(session.getReportId());
				}
			}
			case PARTICIPANT_JOINED -> {
				if (!isReporter(session, event)) return;
				if (session.markReconnected(now)) realtimeNotifier.liveReconnected(session.getReportId());
				// 제보자가 실제로 방에 들어와 송출을 시작한 시점이 녹화를 걸 수 있는 유일한 시점이다.
				recordingService.startForSession(session, event.participantIdentity());
			}
			case ROOM_FINISHED -> {
				recordingService.stopForSession(session.getId());
				if (session.end(now)) realtimeNotifier.liveEndedToOperator(session.getReportId());
			}
			case EGRESS_STARTED, EGRESS_UPDATED -> {
				if (event.egress() != null) recordingService.markActive(event.egress().egressId());
			}
			case EGRESS_ENDED -> {
				if (event.egress() != null) {
					frameIngestService.ingest(event.egress().egressId());
					recordingService.complete(event.egress().egressId(), event.egress());
				}
			}
			case UNSUPPORTED -> { }
		}
	}

	/** 담당자(operator-)·AI(ai-) 참가자의 입퇴장은 세션 생명주기에 영향을 주지 않는다. */
	private boolean isReporter(LiveSession session, LiveWebhookEvent event) {
		return event.participantIdentity() != null
				&& event.participantIdentity().equals(REPORTER_IDENTITY_PREFIX + session.getReporterId());
	}
}
