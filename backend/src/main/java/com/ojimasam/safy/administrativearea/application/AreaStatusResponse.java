package com.ojimasam.safy.administrativearea.application;

import java.time.Instant;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;

public record AreaStatusResponse(
		Long areaId,
		String areaCode,
		String areaName,
		String riskLevel,
		long activeReportCount,
		long liveReportCount,
		Instant updatedAt
) {
	public static AreaStatusResponse of(
			AdministrativeArea area,
			String riskLevel,
			long activeReportCount,
			long liveReportCount,
			Instant updatedAt
	) {
		return new AreaStatusResponse(
				area.getId(),
				area.getCode(),
				area.getName(),
				riskLevel,
				activeReportCount,
				liveReportCount,
				updatedAt
		);
	}
}
