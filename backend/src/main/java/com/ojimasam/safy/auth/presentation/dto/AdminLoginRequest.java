package com.ojimasam.safy.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 로컬 로그인 요청")
public record AdminLoginRequest(
		@Schema(description = "관리자 로그인 아이디", example = "operator01") @NotBlank String loginId,
		@Schema(description = "관리자 비밀번호", example = "SafyPassword1!", format = "password") @NotBlank String password) {
	@Override public String toString() { return "AdminLoginRequest[loginId=" + loginId + ", password=***]"; }
}
