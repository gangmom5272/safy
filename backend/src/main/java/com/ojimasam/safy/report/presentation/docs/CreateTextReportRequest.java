package com.ojimasam.safy.report.presentation.docs;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateTextReportRequest", description = "텍스트 제보 등록 요청")
public record CreateTextReportRequest(
		@Schema(description = "제보 유형", allowableValues = "TEXT", example = "TEXT", requiredMode = Schema.RequiredMode.REQUIRED)
		String reportType,
		@Schema(description = "제보 내용", example = "도로에 큰 포트홀이 있습니다.", maxLength = 2000,
				requiredMode = Schema.RequiredMode.REQUIRED)
		String description,
		@Schema(description = "위도", example = "37.5665", minimum = "-90", maximum = "90",
				requiredMode = Schema.RequiredMode.REQUIRED)
		BigDecimal latitude,
		@Schema(description = "경도", example = "126.978", minimum = "-180", maximum = "180",
				requiredMode = Schema.RequiredMode.REQUIRED)
		BigDecimal longitude,
		@Schema(description = "주소", example = "서울특별시 중구 세종대로 110", maxLength = 500)
		String address
) {
}
