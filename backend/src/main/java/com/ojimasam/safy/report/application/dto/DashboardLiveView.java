package com.ojimasam.safy.report.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.ojimasam.safy.live.application.StreamStatus;

public record DashboardLiveView(
		UUID streamRoomId,
		StreamStatus status,
		Instant startedAt,
		Instant expiresAt
) {
}
