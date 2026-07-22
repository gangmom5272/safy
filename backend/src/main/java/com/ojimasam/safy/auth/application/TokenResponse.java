package com.ojimasam.safy.auth.application;

import com.ojimasam.safy.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SAFY 인증 토큰 응답")
public record TokenResponse(
		@Schema(description = "JWT Access Token") String accessToken,
		@Schema(description = "토큰 타입", example = "Bearer") String tokenType,
		@Schema(description = "Access Token 유효 시간(초)", example = "900") long expiresIn,
		@Schema(description = "사용자 ID", example = "1") Long userId,
		@Schema(description = "사용자 역할", allowableValues = {"CITIZEN", "OPERATOR", "SUPER_ADMIN"}) String role,
		@Schema(description = "소셜 최초 가입 여부", example = "false") boolean newUser
) {
	public static TokenResponse of(String token, long expiresIn, User user, boolean newUser) {
		return new TokenResponse(token, "Bearer", expiresIn, user.getId(), user.getRole().name(), newUser);
	}

	@Override
	public String toString() {
		return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn
				+ ", userId=" + userId + ", role=" + role + ", newUser=" + newUser + "]";
	}
}
