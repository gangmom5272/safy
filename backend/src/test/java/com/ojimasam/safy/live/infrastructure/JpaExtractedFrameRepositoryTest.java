package com.ojimasam.safy.live.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.live.domain.ExtractedFrame;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaExtractedFrameRepositoryTest {
	@Autowired
	private JpaExtractedFrameRepository repository;

	@Test
	void findsLastSequenceWithinRecordingSegment() {
		repository.saveAndFlush(ExtractedFrame.create(10L, 100L, 0, Instant.parse("2026-08-01T01:00:00Z")));
		repository.saveAndFlush(ExtractedFrame.create(10L, 101L, 1, Instant.parse("2026-08-01T01:00:05Z")));
		repository.saveAndFlush(ExtractedFrame.create(11L, 102L, 0, Instant.parse("2026-08-01T01:00:10Z")));

		ExtractedFrame last = repository
				.findTopByStreamRecordingIdOrderBySequenceNumberDesc(10L)
				.orElseThrow();

		assertThat(last.getMediaAssetId()).isEqualTo(101L);
		assertThat(last.getSequenceNumber()).isEqualTo(1);
		assertThat(repository.existsByMediaAssetId(102L)).isTrue();
	}
}
