package com.ojimasam.safy.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "프로필 사진 업로드 URL 발급 요청")
public record IssueProfileImageUploadRequest(
		@Schema(description = "업로드할 이미지의 MIME 타입", example = "image/jpeg",
				allowableValues = {"image/jpeg", "image/png", "image/webp"})
		@NotBlank String contentType
) {
}
