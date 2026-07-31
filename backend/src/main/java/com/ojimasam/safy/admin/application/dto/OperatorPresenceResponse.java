package com.ojimasam.safy.admin.application.dto;

import java.time.Instant;

import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;

public record OperatorPresenceResponse(
		OperatorPresenceStatus status,
		Instant lastSeenAt
) {
	public static OperatorPresenceResponse from(OperatorPresence presence) {
		return new OperatorPresenceResponse(presence.getStatus(), presence.getLastSeenAt());
	}

	/** 접속 이력이 없는 담당자는 오프라인으로 본다. */
	public static OperatorPresenceResponse offline() {
		return new OperatorPresenceResponse(OperatorPresenceStatus.OFFLINE, null);
	}
}
