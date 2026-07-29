package com.ojimasam.safy.realtime.application.dto;

import java.time.Instant;

import com.ojimasam.safy.realtime.domain.RealtimeEvent;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;

public record RealtimeEventView(
		Long eventId,
		RealtimeEventType eventType,
		Long reportId,
		String resourceId,
		Long messageId,
		Instant createdAt
) {
	public static RealtimeEventView from(RealtimeEvent event) {
		return new RealtimeEventView(event.getId(), event.getEventType(), event.getReportId(),
				event.getResourceId(), event.getMessageId(), event.getCreatedAt());
	}
}
