package com.ojimasam.safy.admin.presentation;

import com.ojimasam.safy.user.domain.UserStatus;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 상태 변경 요청")
public record UpdateOperatorStatusRequest(
		@Schema(description = "변경할 상태", example = "SUSPENDED", allowableValues = {"ACTIVE", "SUSPENDED"})
		@NotNull UserStatus status) {
}
