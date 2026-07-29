package com.ojimasam.safy.report.application;

import java.math.BigDecimal;

public record ReportRoutingTarget(
		Long reportId,
		BigDecimal latitude,
		BigDecimal longitude,
		/** 이미 주소가 있으면 재시도에서 다시 조회하지 않는다. */
		boolean hasAddress
) {
}
