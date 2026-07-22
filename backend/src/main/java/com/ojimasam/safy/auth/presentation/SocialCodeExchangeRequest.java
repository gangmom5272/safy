package com.ojimasam.safy.auth.presentation;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 일회용 코드 교환 요청")
public record SocialCodeExchangeRequest(
		@Schema(description = "OAuth2 성공 후 프론트 Redirect URI로 전달된 60초 일회용 코드",
				example = "7maaqPBDZ4d8YZtQS_BEuZ4puVsfESOzCfQvUEe9jwA") @NotBlank String code) {
	@Override public String toString() { return "SocialCodeExchangeRequest[code=***]"; }
}
