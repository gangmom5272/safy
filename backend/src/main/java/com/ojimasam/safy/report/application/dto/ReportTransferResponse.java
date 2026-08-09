package com.ojimasam.safy.report.application.dto;

import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteType;

public record ReportTransferResponse(
		Long reportId,
		Long controlCenterId,
		String controlCenterName,
		ReportCenterRouteType routeType,
		/** 이관 직후 대상 센터에서 자동 배정까지 성공했는지. 실패해도 대상 센터 공용 큐에 남는다. */
		boolean assigned
) {
	public static ReportTransferResponse from(ReportCenterRoute route, boolean assigned) {
		return new ReportTransferResponse(
				route.getReport().getId(),
				route.getControlCenter().getId(),
				route.getControlCenter().getName(),
				route.getRouteType(),
				assigned
		);
	}
}
