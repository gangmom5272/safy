package com.ojimasam.safy.report.application;

import java.time.Instant;

import com.ojimasam.safy.report.domain.ReportMessage;
import com.ojimasam.safy.user.domain.UserRole;

public record ReportMessageResponse(
		Long messageId,
		Long reportId,
		Long senderUserId,
		UserRole senderRole,
		String content,
		Instant sentAt
) {
	public static ReportMessageResponse from(ReportMessage message) {
		return new ReportMessageResponse(message.getId(), message.getReport().getId(), message.getSenderUserId(),
				message.getSenderRole(), message.getContent(), message.getSentAt());
	}
}
