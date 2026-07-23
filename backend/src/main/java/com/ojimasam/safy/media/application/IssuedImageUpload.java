package com.ojimasam.safy.media.application;

import java.time.Instant;

public record IssuedImageUpload(Long mediaAssetId, String uploadUrl, String contentType,
		long maxFileSize, Instant expiresAt) {
}
