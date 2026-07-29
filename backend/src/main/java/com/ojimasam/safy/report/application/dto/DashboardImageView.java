package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.media.domain.type.MediaStorageStatus;

public record DashboardImageView(
		int imageCount,
		MediaStorageStatus status,
		String thumbnailUrl,
		Instant thumbnailUrlExpiresAt
) {
}
