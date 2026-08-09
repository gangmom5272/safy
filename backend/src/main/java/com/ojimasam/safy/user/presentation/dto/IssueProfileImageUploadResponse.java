package com.ojimasam.safy.user.presentation.dto;

import java.time.Instant;
import java.util.Map;

import com.ojimasam.safy.user.application.IssuedProfileImageUpload;

import io.swagger.v3.oas.annotations.media.Schema;

// 제보 이미지 업로드 응답(IssueImageUploadResponse)과 같은 모양을 유지한다. 프론트가 같은
// 순서로 처리하므로 필드 이름이 갈라지지 않게 한다. 프로필 사진은 DB 행을 만들지 않아
// mediaAssetId 만 없다.
@Schema(description = "프로필 사진 업로드 URL")
public record IssueProfileImageUploadResponse(String uploadUrl, String uploadMethod,
		Map<String, String> requiredHeaders, long maxFileSize, Instant expiresAt) {
	public static IssueProfileImageUploadResponse from(IssuedProfileImageUpload upload) {
		return new IssueProfileImageUploadResponse(upload.uploadUrl(), "PUT",
				Map.of("Content-Type", upload.contentType()), upload.maxFileSize(), upload.expiresAt());
	}
}
