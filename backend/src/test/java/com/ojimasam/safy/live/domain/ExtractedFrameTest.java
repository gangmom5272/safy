package com.ojimasam.safy.live.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ExtractedFrameTest {
	private static final Instant CAPTURED_AT = Instant.parse("2026-08-01T01:02:03Z");

	@Test
	void createsFrameForRecordingSegment() {
		ExtractedFrame frame = ExtractedFrame.create(11L, 22L, 3, CAPTURED_AT);

		assertThat(frame.getStreamRecordingId()).isEqualTo(11L);
		assertThat(frame.getMediaAssetId()).isEqualTo(22L);
		assertThat(frame.getSequenceNumber()).isEqualTo(3);
		assertThat(frame.getCapturedAt()).isEqualTo(CAPTURED_AT);
	}

	@Test
	void rejectsInvalidSequenceNumber() {
		assertThatThrownBy(() -> ExtractedFrame.create(11L, 22L, -1, CAPTURED_AT))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
