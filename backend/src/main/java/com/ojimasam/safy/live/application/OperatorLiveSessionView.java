package com.ojimasam.safy.live.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;

public record OperatorLiveSessionView(
		UUID streamRoomId,
		Long reportId,
		StreamStatus status,
		ReportCategory category,
		ReportSubcategory subcategory,
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
				StreamStatus.from(session.getStatus()), report.getCategory(), report.getSubcategory(),
				report.getDescription(), report.getLatitude(),
				report.getLongitude(), report.getAddress(), session.getStartedAt(), session.getEndedAt(),
				session.getExpiresAt(), session.getCreatedAt());
	}
}
