package com.ojimasam.safy.live.application;

import java.time.Clock;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;

@Service
public class ExpiredLiveSessionCleanupService {
	private static final Logger log = LoggerFactory.getLogger(ExpiredLiveSessionCleanupService.class);
	private final LiveSessionRepository repository;
	private final LiveMediaGateway mediaGateway;
	private final Clock clock;

	@Autowired
	public ExpiredLiveSessionCleanupService(LiveSessionRepository repository, LiveMediaGateway mediaGateway) {
		this(repository, mediaGateway, Clock.systemUTC());
	}

	ExpiredLiveSessionCleanupService(LiveSessionRepository repository, LiveMediaGateway mediaGateway, Clock clock) {
		this.repository = repository;
		this.mediaGateway = mediaGateway;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${openvidu.cleanup-delay:PT1M}")
	@Transactional
	public void cleanup() {
		var statuses = EnumSet.of(LiveSessionStatus.CREATED, LiveSessionStatus.ACTIVE, LiveSessionStatus.FAILED);
		for (LiveSession session : repository.findTop100ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
				statuses, clock.instant())) {
			try {
				mediaGateway.closeRoom(session.getRoomName());
				session.end(clock.instant());
			} catch (LiveMediaException exception) {
				session.markFailed(exception.getMessage(), clock.instant());
				log.warn("Failed to close expired live session {}", session.getId(), exception);
			}
		}
	}
}
