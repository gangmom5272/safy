package com.ojimasam.safy.report.presentation.docs;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateImageReportRequest", description = "이미지 제보 등록 요청")
public record CreateImageReportRequest(
		@Schema(description = "제보 유형", allowableValues = "IMAGE", example = "IMAGE", requiredMode = Schema.RequiredMode.REQUIRED)
		String reportType,
		@Schema(description = "제보 내용", example = "도로 침수 현장입니다.", maxLength = 2000,
				requiredMode = Schema.RequiredMode.REQUIRED)
		String description,
		@Schema(description = "위도", example = "37.5665", minimum = "-90", maximum = "90",
				requiredMode = Schema.RequiredMode.REQUIRED)
		BigDecimal latitude,
		@Schema(description = "경도", example = "126.978", minimum = "-180", maximum = "180",
				requiredMode = Schema.RequiredMode.REQUIRED)
		BigDecimal longitude,
		@Schema(description = "주소", example = "서울특별시 중구 세종대로 110", maxLength = 500)
		String address,
		@ArraySchema(
				schema = @Schema(description = "Presigned URL 발급 응답의 미디어 ID", example = "10"),
				minItems = 1, maxItems = 5
		)
		List<Long> mediaAssetIds
) {
}
