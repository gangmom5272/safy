package com.ojimasam.safy.report.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OperatorReportTransferRequest(
		@NotNull Long targetControlCenterId,
		@NotBlank @Size(max = 500) String reason
) {
}
