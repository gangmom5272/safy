package com.ojimasam.safy.user.application.dto;

import java.time.Instant;

import com.ojimasam.safy.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 정보")
public record UserResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "안전지킴이") String displayName,
		@Schema(allowableValues = {"CITIZEN", "OPERATOR", "SUPER_ADMIN"}) String role,
		@Schema(allowableValues = {"ACTIVE", "SUSPENDED", "WITHDRAWN"}) String status,
		Instant createdAt,
		Instant updatedAt) {
	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getDisplayName(), user.getRole().name(),
				user.getStatus().name(), user.getCreatedAt(), user.getUpdatedAt());
	}
}
