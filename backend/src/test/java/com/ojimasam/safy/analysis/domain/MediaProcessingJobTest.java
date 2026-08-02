package com.ojimasam.safy.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;

class MediaProcessingJobTest {
	private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

	@Test
	void processesJobSuccessfully() {
		MediaProcessingJob job = job();

		job.start(NOW);
		job.complete(NOW.plusSeconds(1));

		assertThat(job.getStatus()).isEqualTo(MediaProcessingJobStatus.COMPLETED);
		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getStartedAt()).isEqualTo(NOW);
		assertThat(job.getCompletedAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	void retriesOnlyAfterScheduledTime() {
		MediaProcessingJob job = job();
		job.start(NOW);
		job.scheduleRetry("AI connection failed", NOW.plusSeconds(10));

		assertThatThrownBy(() -> job.start(NOW.plusSeconds(9)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("재시도 시각");

		job.start(NOW.plusSeconds(10));

		assertThat(job.getStatus()).isEqualTo(MediaProcessingJobStatus.PROCESSING);
		assertThat(job.getAttemptCount()).isEqualTo(2);
	}

	@Test
	void rejectsTerminalTransitionUnlessProcessing() {
		MediaProcessingJob job = job();

		assertThatThrownBy(() -> job.fail("invalid frame", NOW))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void recoversInterruptedProcessingJob() {
		MediaProcessingJob job = job();
		job.start(NOW);

		job.recoverInterrupted(NOW.plusSeconds(60));

		assertThat(job.getStatus()).isEqualTo(MediaProcessingJobStatus.RETRY_WAIT);
		assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
	}

	private MediaProcessingJob job() {
		return MediaProcessingJob.create(10L, MediaProcessingJobType.ANALYZE_AND_BLUR, NOW);
	}
}
