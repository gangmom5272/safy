package com.ojimasam.safy.report.application;

import java.math.BigDecimal;

import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportSubcategory;

public record CreateReportCommand(
		Long reporterId,
		ReportType type,
		ReportCategory category,
		ReportSubcategory subcategory,
		String description,
		BigDecimal latitude,
		BigDecimal longitude
) {
}
