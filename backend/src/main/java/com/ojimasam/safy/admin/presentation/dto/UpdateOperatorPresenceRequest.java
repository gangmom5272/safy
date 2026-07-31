package com.ojimasam.safy.admin.presentation.dto;

import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateOperatorPresenceRequest(
		@NotNull OperatorPresenceStatus status
) {
}
