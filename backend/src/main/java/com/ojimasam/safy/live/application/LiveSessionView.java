package com.ojimasam.safy.live.application;

import java.time.Instant;
import java.util.UUID;
import com.ojimasam.safy.live.domain.LiveSession;

public record LiveSessionView(UUID streamRoomId, Long reportId, StreamStatus status,
		Instant startedAt, Instant endedAt, Instant expiresAt) {
	public static LiveSessionView from(LiveSession session) {
		return new LiveSessionView(session.getId(), session.getReportId(), StreamStatus.from(session.getStatus()),
				session.getStartedAt(), session.getEndedAt(), session.getExpiresAt());
	}
}
