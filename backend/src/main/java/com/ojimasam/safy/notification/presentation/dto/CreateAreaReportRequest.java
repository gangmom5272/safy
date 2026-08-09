package com.ojimasam.safy.notification.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAreaReportRequest(
		@NotNull Long placeId,
		@NotBlank @Size(max = 120) String title,
		@NotBlank @Size(max = 500) String content,
		@Min(5) @Max(120) int expiresInMinutes
) {
}
