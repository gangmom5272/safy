package com.ojimasam.safy.admin.application;

import java.time.Instant;
import java.time.LocalDateTime;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.auth.domain.AdminCredential;
import com.ojimasam.safy.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 정보")
public record OperatorResponse(
		@Schema(example = "2") Long id,
		@Schema(example = "operator01") String loginId,
		@Schema(example = "강남구 관제요원") String displayName,
		@Schema(allowableValues = {"OPERATOR", "SUPER_ADMIN"}) String role,
		@Schema(allowableValues = {"ACTIVE", "SUSPENDED"}) String status,
		@Schema(example = "1") Long controlCenterId,
		@Schema(example = "MANAGER") String membershipRole,
		LocalDateTime lastLoginAt,
		Instant createdAt
) {
	public static OperatorResponse of(User user, AdminCredential credential, AdminCenterMembership membership) {
		return new OperatorResponse(user.getId(), credential.getLoginId(), user.getDisplayName(),
				user.getRole().name(), user.getStatus().name(),
				membership == null ? null : membership.getControlCenterId(),
				membership == null ? null : membership.getMembershipRole(),
				credential.getLastLoginAt(), user.getCreatedAt());
	}
}
