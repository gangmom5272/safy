package com.ojimasam.safy.report.presentation.dto;

import java.math.BigDecimal;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
		@NotNull(message = "제보 유형은 필수입니다.")
		@Schema(allowableValues = {"IMAGE", "LIVE"})
		ReportType reportType,

		@NotNull(message = "제보 카테고리는 필수입니다.")
		ReportCategory category,

		@NotNull(message = "제보 세부 상황은 필수입니다.")
		ReportSubcategory subcategory,

		@NotBlank(message = "제보 내용은 필수입니다.")
		@Size(max = 2000, message = "제보 내용은 2000자 이하여야 합니다.")
		String description,

		@NotNull(message = "위도는 필수입니다.")
		@DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
		@DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
		BigDecimal latitude,

		@NotNull(message = "경도는 필수입니다.")
		@DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
		@DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
		BigDecimal longitude
) {
}
