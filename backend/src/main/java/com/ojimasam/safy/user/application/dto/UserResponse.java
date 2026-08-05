package com.ojimasam.safy.user.application.dto;

import java.time.Instant;

import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.auth.domain.SocialAccount;
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
		// 소셜 로그인으로 받은 값이라 회원이 앱에서 바꿀 수 없다. 로그인마다 갱신된다.
		// 소셜 계정이 없는 관리자 계정은 둘 다 null 이다.
		@Schema(example = "citizen@example.com", nullable = true) String email,
		@Schema(example = "https://k.kakaocdn.net/dn/profile.jpg", nullable = true) String profileImageUrl,
		Instant createdAt,
		Instant updatedAt) {
	public static UserResponse from(User user, AdminCenterMembership membership, SocialAccount socialAccount) {
		return new UserResponse(user.getId(), user.getDisplayName(), user.getRole().name(),
				user.getStatus().name(),
				membership == null ? null : membership.getControlCenterId(),
				membership == null ? null : membership.getControlCenter().getName(),
				socialAccount == null ? null : socialAccount.getEmail(),
				secureUrl(socialAccount == null ? null : socialAccount.getProviderProfileImageUrl()),
				user.getCreatedAt(), user.getUpdatedAt());
	}

	// 카카오는 프로필 이미지를 http URL 로 주는 경우가 있다. 그대로 내려주면 https 로 서비스되는
	// 프론트에서 mixed content 로 차단되어 이미지가 비어 보인다. 같은 경로가 https 로도
	// 서비스되므로 스킴만 바꿔 내려준다. 이미 저장된 행도 응답 시점에 함께 교정된다.
	private static String secureUrl(String url) {
		if (url == null || !url.startsWith("http://")) return url;
		return "https://" + url.substring("http://".length());
	}
}
