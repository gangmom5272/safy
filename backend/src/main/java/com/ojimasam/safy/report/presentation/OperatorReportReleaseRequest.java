package com.ojimasam.safy.report.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OperatorReportReleaseRequest(
		@Schema(description = "반려/재배정 사유", example = "관할 구역 오배정")
		@NotBlank(message = "사유는 필수입니다.")
		@Size(max = 500, message = "사유는 500자 이하여야 합니다.")
		String reason
) {
}
