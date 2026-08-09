package com.ojimasam.safy.report.application.dto;

import java.math.BigDecimal;

import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;

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
