package com.ojimasam.safy.controlcenter.application;

import com.ojimasam.safy.controlcenter.domain.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.ControlCenterType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateControlCenterRequest(
		@NotBlank @Size(max = 100) String name,
		@NotNull ControlCenterType centerType,
		@NotNull ControlCenterStatus status,
		@Min(1) Integer capacityLimit,
		@Size(max = 255) String address,
		@Size(max = 30) String phoneNumber
) {
}
