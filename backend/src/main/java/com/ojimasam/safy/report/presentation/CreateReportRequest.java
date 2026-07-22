package com.ojimasam.safy.report.presentation;

import java.math.BigDecimal;
import java.util.List;

import com.ojimasam.safy.report.domain.ReportType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
		@NotNull(message = "제보 유형은 필수입니다.")
		ReportType reportType,

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
		BigDecimal longitude,

		@Size(max = 500, message = "주소는 500자 이하여야 합니다.")
		String address,

		@Size(max = 5, message = "이미지는 최대 5장까지 첨부할 수 있습니다.")
		List<@NotNull Long> mediaAssetIds
) {
}
