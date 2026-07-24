package com.ojimasam.safy.report.application;

import java.math.BigDecimal;

public record ReportRoutingTarget(
		Long reportId,
		BigDecimal latitude,
		BigDecimal longitude
) {
}
