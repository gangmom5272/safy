package com.ojimasam.safy.live.presentation;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLiveSessionRequest(
		@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
		@NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
		@Size(max = 500) String address,
		@Size(max = 2000) String description
) {
}
