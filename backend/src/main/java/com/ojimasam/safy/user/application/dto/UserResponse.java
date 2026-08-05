package com.ojimasam.safy.user.application.dto;

import java.time.Instant;

import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 정보")
public record UserResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "안전지킴이") String displayName,
		@Schema(allowableValues = {"CITIZEN", "OPERATOR", "SUPER_ADMIN"}) String role,
		@Schema(allowableValues = {"ACTIVE", "SUSPENDED", "WITHDRAWN"}) String status,
		@Schema(example = "23", nullable = true) Long controlCenterId,
		@Schema(example = "강남구 재난안전상황실", nullable = true) String controlCenterName,
		Instant createdAt,
		Instant updatedAt) {
	public static UserResponse from(User user, AdminCenterMembership membership) {
		return new UserResponse(user.getId(), user.getDisplayName(), user.getRole().name(),
				user.getStatus().name(),
				membership == null ? null : membership.getControlCenterId(),
				membership == null ? null : membership.getControlCenter().getName(),
				user.getCreatedAt(), user.getUpdatedAt());
	}
}
