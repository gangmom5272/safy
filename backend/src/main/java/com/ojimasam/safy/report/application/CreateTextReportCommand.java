package com.ojimasam.safy.report.application;

import java.math.BigDecimal;

public record CreateTextReportCommand(
		Long reporterId,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address
) {
}
