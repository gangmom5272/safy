package com.ojimasam.safy.controlcenter.application;

import java.time.Instant;

import com.ojimasam.safy.controlcenter.domain.CoverageRole;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AssignControlCenterAreaRequest(
		@NotNull Long administrativeAreaId,
		@NotNull CoverageRole coverageRole,
		@Min(1) int priority,
		Instant effectiveFrom,
		Instant effectiveTo
) {
}
