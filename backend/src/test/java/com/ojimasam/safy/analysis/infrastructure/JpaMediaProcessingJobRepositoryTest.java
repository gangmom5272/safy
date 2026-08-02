package com.ojimasam.safy.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.analysis.domain.MediaProcessingJob;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;
import com.ojimasam.safy.common.config.JpaAuditingConfig;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaMediaProcessingJobRepositoryTest {
	private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

	@Autowired
	private JpaMediaProcessingJobRepository repository;

	@Test
	void findsOldestReadyJob() {
		repository.saveAndFlush(job(1L, NOW.minusSeconds(5)));
		repository.saveAndFlush(job(2L, NOW.plusSeconds(5)));

		MediaProcessingJob ready = repository
				.findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
						List.of(MediaProcessingJobStatus.PENDING, MediaProcessingJobStatus.RETRY_WAIT), NOW)
				.orElseThrow();

		assertThat(ready.getMediaAssetId()).isEqualTo(1L);
	}

	@Test
	void preventsDuplicateJobForSameAssetAndType() {
		repository.saveAndFlush(job(1L, NOW));

		assertThatThrownBy(() -> repository.saveAndFlush(job(1L, NOW.plusSeconds(1))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private MediaProcessingJob job(Long mediaAssetId, Instant now) {
		return MediaProcessingJob.create(mediaAssetId, MediaProcessingJobType.ANALYZE_AND_BLUR, now);
	}
}
