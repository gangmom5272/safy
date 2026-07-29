package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.media.domain.type.MediaStorageStatus;

public record ReportImageView(
		Long reportImageId,
		Long mediaAssetId,
		int displayOrder,
		String originalFilename,
		String contentType,
		Long fileSize,
		MediaStorageStatus storageStatus,
		String previewUrl,
		Instant previewUrlExpiresAt
) {
}
