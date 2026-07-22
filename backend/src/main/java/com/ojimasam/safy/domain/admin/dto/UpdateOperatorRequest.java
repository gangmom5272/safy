package com.ojimasam.safy.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 정보 및 소속 수정 요청")
public record UpdateOperatorRequest(
		@Schema(description = "표시 이름", example = "강남구 관제요원") @NotBlank @Size(max = 255) String displayName,
		@Schema(description = "소속 관제센터 ID", example = "1") @NotNull Long controlCenterId,
		@Schema(description = "관제센터 내 직책", example = "MANAGER") @NotBlank @Size(max = 30) String membershipRole
) {
}
