package com.ojimasam.safy.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 비밀번호 변경 요청")
public record ChangePasswordRequest(
		@Schema(description = "현재 비밀번호", format = "password") @NotBlank String currentPassword,
		@Schema(description = "새 비밀번호(10~100자)", example = "NewSafyPassword1!", format = "password",
				minLength = 10, maxLength = 100) @NotBlank @Size(min = 10, max = 100) String newPassword
) {
	@Override public String toString() { return "ChangePasswordRequest[currentPassword=***, newPassword=***]"; }
}
