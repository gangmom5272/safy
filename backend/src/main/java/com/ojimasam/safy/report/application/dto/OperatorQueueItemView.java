package com.ojimasam.safy.report.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

/**
 * 센터 공용 큐의 한 행. 담당자가 집어갈지 판단할 최소 정보만 담는다.
 * 접수 경과 시간은 reportedAt 으로 화면에서 계산한다.
 */
public record OperatorQueueItemView(
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		Instant reportedAt,
		DashboardImageView image,
		DashboardLiveView live
) {
}
