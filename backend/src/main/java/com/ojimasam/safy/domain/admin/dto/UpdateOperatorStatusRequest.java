package com.ojimasam.safy.domain.admin.dto;

import com.ojimasam.safy.domain.user.entity.UserStatus;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 상태 변경 요청")
public record UpdateOperatorStatusRequest(
		@Schema(description = "변경할 상태", example = "SUSPENDED", allowableValues = {"ACTIVE", "SUSPENDED"})
		@NotNull UserStatus status) {
}
