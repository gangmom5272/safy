package com.ojimasam.safy.media.presentation.dto;

import java.time.Instant;
import java.util.Map;

import com.ojimasam.safy.media.application.IssuedImageUpload;

public record IssueImageUploadResponse(Long mediaAssetId, String uploadUrl, String uploadMethod,
		Map<String, String> requiredHeaders, long maxFileSize, Instant expiresAt) {
	public static IssueImageUploadResponse from(IssuedImageUpload upload) {
		return new IssueImageUploadResponse(upload.mediaAssetId(), upload.uploadUrl(), "PUT",
				Map.of("Content-Type", upload.contentType()), upload.maxFileSize(), upload.expiresAt());
	}
}
