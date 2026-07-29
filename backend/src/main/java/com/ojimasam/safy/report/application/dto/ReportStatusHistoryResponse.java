package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistory;

public record ReportStatusHistoryResponse(
		Long id,
		ReportStatus fromStatus,
		ReportStatus toStatus,
		String reason,
		Instant changedAt
) {
	public static ReportStatusHistoryResponse from(ReportStatusHistory history) {
		return new ReportStatusHistoryResponse(
				history.getId(),
				history.getFromStatus(),
				history.getToStatus(),
				history.getReason(),
				history.getChangedAt()
		);
	}
}
