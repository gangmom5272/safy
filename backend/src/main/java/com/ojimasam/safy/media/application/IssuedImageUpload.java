package com.ojimasam.safy.media.application;

import java.time.Instant;
import java.util.Map;

public record IssuedImageUpload(Long mediaAssetId, String uploadUrl, Map<String, String> formFields,
		long maxFileSize, Instant expiresAt) {
}
