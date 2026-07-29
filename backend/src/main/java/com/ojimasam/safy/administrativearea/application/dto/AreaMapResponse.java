package com.ojimasam.safy.administrativearea.application.dto;

import com.ojimasam.safy.report.application.dto.ReportCursorResponse;

public record AreaMapResponse(
		AreaStatusResponse status,
		String boundaryGeoJson,
		ReportCursorResponse reports
) {
}
