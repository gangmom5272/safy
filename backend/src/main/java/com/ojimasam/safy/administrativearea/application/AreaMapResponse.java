package com.ojimasam.safy.administrativearea.application;

import com.ojimasam.safy.report.application.ReportCursorResponse;

public record AreaMapResponse(
		AreaStatusResponse status,
		String boundaryGeoJson,
		ReportCursorResponse reports
) {
}
