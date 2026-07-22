package com.ojimasam.safy.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImageContentTypeDetectorTest {
	@Test
	void detectsSupportedImageSignatures() {
		assertThat(ImageContentTypeDetector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
				.isEqualTo("image/jpeg");
		assertThat(ImageContentTypeDetector.detect(new byte[] {
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
		})).isEqualTo("image/png");
		assertThat(ImageContentTypeDetector.detect(new byte[] {
				0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50
		})).isEqualTo("image/webp");
	}

	@Test
	void rejectsSpoofedContent() {
		assertThatThrownBy(() -> ImageContentTypeDetector.detect("not-an-image".getBytes()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("실제 이미지");
	}
}
