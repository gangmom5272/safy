package com.ojimasam.safy.notification.application.dto;

import java.time.Instant;

import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.type.NotificationType;

public record NotificationView(
		Long id,
		NotificationType type,
		String title,
		String content,
		Instant createdAt,
		boolean isRead,
		boolean isImportant,
		String targetType,
		Long targetId,
		Long reportId,
		Long areaReportRequestId
) {
	public static NotificationView from(Notification notification) {
		Long reportId = notification.getReportId();
		Long requestId = notification.getAreaReportRequestId();
		String targetType = reportId != null ? "REPORT" : requestId != null ? "AREA_REPORT_REQUEST" : "NONE";
		Long targetId = reportId != null ? reportId : requestId;
		return new NotificationView(
				notification.getId(), notification.getType(), notification.getTitle(), notification.getContent(),
				notification.getCreatedAt(), notification.getReadAt() != null, requestId != null,
				targetType, targetId, reportId, requestId
		);
	}
}
