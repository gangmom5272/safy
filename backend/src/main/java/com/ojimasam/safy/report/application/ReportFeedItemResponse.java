package com.ojimasam.safy.report.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.domain.ReportType;

public record ReportFeedItemResponse(
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		ReportStatus status,
		Long areaId,
		String areaCode,
		String areaName,
		UUID streamRoomId,
		LiveSessionStatus streamStatus,
		Instant reportedAt
) {
	public static ReportFeedItemResponse from(Report report, LiveSession liveSession) {
		AdministrativeArea area = report.getAdministrativeArea();
		return new ReportFeedItemResponse(
				report.getId(),
				report.getType(),
				report.getCategory(),
				report.getSubcategory(),
				report.getDescription(),
				report.getLatitude(),
				report.getLongitude(),
				report.getAddress(),
				report.getStatus(),
				area == null ? null : area.getId(),
				area == null ? null : area.getCode(),
				area == null ? null : area.getName(),
				liveSession == null ? null : liveSession.getId(),
				liveSession == null ? null : liveSession.getStatus(),
				report.getReportedAt()
		);
	}
}
