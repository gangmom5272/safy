package com.ojimasam.safy.report.application;

import java.math.BigDecimal;
import java.util.List;

public record CreateImageReportCommand(
		Long reporterId,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		List<Long> mediaAssetIds
) {
}
