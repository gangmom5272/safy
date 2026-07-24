package com.ojimasam.safy.live.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.report.domain.Report;

public record OperatorLiveSessionView(
		UUID streamRoomId,
		Long reportId,
		StreamStatus status,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		Instant startedAt,
		Instant endedAt,
		Instant expiresAt,
		Instant createdAt
) {
	public static OperatorLiveSessionView from(LiveSession session, Report report) {
		return new OperatorLiveSessionView(session.getId(), session.getReportId(),
				StreamStatus.from(session.getStatus()), report.getDescription(), report.getLatitude(),
				report.getLongitude(), report.getAddress(), session.getStartedAt(), session.getEndedAt(),
				session.getExpiresAt(), session.getCreatedAt());
	}
}
