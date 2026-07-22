package com.ojimasam.safy.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class MediaAssetTest {
	@Test
	void expiredVerificationDoesNotMutateStateBeforeCleanupTransaction() {
		Instant expiresAt = Instant.parse("2026-07-21T04:00:00Z");
		MediaAsset asset = MediaAsset.prepareOriginal(
				1L, "bucket", "key", "image.jpg", "image/jpeg", expiresAt
		);

		assertThatThrownBy(() -> asset.verifyAttachable(1L, expiresAt.plusSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("만료");
		assertThat(asset.getStorageStatus()).isEqualTo(MediaStorageStatus.PENDING_UPLOAD);
	}

	@Test
	void rejectsFilenameLongerThanColumnLimit() {
		assertThatThrownBy(() -> MediaAsset.prepareOriginal(
				1L, "bucket", "key", "a".repeat(256), "image/jpeg", Instant.now().plusSeconds(60)
		)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("255자");
	}
}
