package com.ojimasam.safy.report.application;

import java.time.Instant;

import com.ojimasam.safy.media.domain.MediaStorageStatus;

public record DashboardImageView(
		int imageCount,
		MediaStorageStatus status,
		String thumbnailUrl,
		Instant thumbnailUrlExpiresAt
) {
}
