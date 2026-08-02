package com.ojimasam.safy.media.application;

import java.time.Instant;

public record StoredMediaObject(
		String objectKey,
		long size,
		Instant lastModified
) {
}
