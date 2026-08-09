package com.ojimasam.safy.report.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;

public record ReportWebRtcResponse(
		Long reportId,
		UUID streamRoomId,
		String roomName,
		LiveSessionStatus status,
		Instant startedAt,
		Instant endedAt,
		Instant expiresAt,
		boolean joinable
) {
	public static ReportWebRtcResponse from(Long reportId, LiveSession session, Instant now) {
		if (session == null) {
			return new ReportWebRtcResponse(reportId, null, null, null, null, null, null, false);
		}
		boolean joinable = (session.getStatus() == LiveSessionStatus.CREATED
				|| session.getStatus() == LiveSessionStatus.ACTIVE)
				&& now.isBefore(session.getExpiresAt());
		return new ReportWebRtcResponse(
				reportId,
				session.getId(),
				session.getRoomName(),
				session.getStatus(),
				session.getStartedAt(),
				session.getEndedAt(),
				session.getExpiresAt(),
				joinable
		);
	}
}
