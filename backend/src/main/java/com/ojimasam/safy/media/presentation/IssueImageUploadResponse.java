package com.ojimasam.safy.media.presentation;

import java.time.Instant;
import java.util.Map;

import com.ojimasam.safy.media.application.IssuedImageUpload;

public record IssueImageUploadResponse(Long mediaAssetId, String uploadUrl, String uploadMethod,
		Map<String, String> formFields, long maxFileSize, Instant expiresAt) {
	public static IssueImageUploadResponse from(IssuedImageUpload upload) {
		return new IssueImageUploadResponse(upload.mediaAssetId(), upload.uploadUrl(), "POST",
				upload.formFields(), upload.maxFileSize(), upload.expiresAt());
	}
}
