package com.ojimasam.safy.admin.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 생성 요청")
public record CreateOperatorRequest(
		@Schema(description = "로그인 아이디", example = "operator01", maxLength = 100)
		@NotBlank @Size(max = 100) String loginId,
		@Schema(description = "초기 비밀번호", example = "SafyPassword1!", format = "password", minLength = 10, maxLength = 100)
		@NotBlank @Size(min = 10, max = 100) String password,
		@Schema(description = "표시 이름", example = "강남구 관제요원", maxLength = 255)
		@NotBlank @Size(max = 255) String displayName,
		@Schema(description = "소속 관제센터 ID", example = "1") @NotNull Long controlCenterId,
		@Schema(description = "관제센터 내 직책", example = "MANAGER", maxLength = 30)
		@NotBlank @Size(max = 30) String membershipRole
) {
	@Override public String toString() {
		return "CreateOperatorRequest[loginId=" + loginId + ", password=***, displayName=" + displayName
				+ ", controlCenterId=" + controlCenterId + ", membershipRole=" + membershipRole + "]";
	}
}
