package com.ojimasam.safy.user.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "표시 이름 수정 요청")
public record UpdateDisplayNameRequest(
		@Schema(description = "새 표시 이름", example = "안전지킴이", maxLength = 255)
		@NotBlank @Size(max = 255) String displayName
) {
}
