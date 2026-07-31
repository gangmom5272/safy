package com.ojimasam.safy.administrativearea.application.dto;

import com.ojimasam.safy.report.application.dto.PublicReportCursorResponse;

public record AreaMapResponse(
		AreaStatusResponse status,
		String boundaryGeoJson,
		PublicReportCursorResponse reports
) {
}
