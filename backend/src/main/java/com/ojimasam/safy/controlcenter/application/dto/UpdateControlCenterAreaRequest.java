package com.ojimasam.safy.controlcenter.application.dto;

import java.time.Instant;

import com.ojimasam.safy.controlcenter.domain.type.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.type.CoverageStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateControlCenterAreaRequest(
		@NotNull CoverageRole coverageRole,
		@Min(1) int priority,
		@NotNull CoverageStatus status,
		@NotNull Instant effectiveFrom,
		Instant effectiveTo
) {
}
