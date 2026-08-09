package com.ojimasam.safy.analysis.domain;

import java.time.Instant;

import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;
import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
		name = "media_processing_jobs",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_media_processing_jobs_asset_type",
				columnNames = {"media_asset_id", "job_type"}
		),
		indexes = @Index(
				name = "idx_media_processing_jobs_ready",
				columnList = "status, next_attempt_at"
		)
)
public class MediaProcessingJob extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "media_asset_id", nullable = false)
	private Long mediaAssetId;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 30)
	private MediaProcessingJobType jobType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MediaProcessingJobStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", columnDefinition = "timestamp(6) with time zone")
	private Instant nextAttemptAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@Column(name = "started_at", columnDefinition = "timestamp(6) with time zone")
	private Instant startedAt;

	@Column(name = "completed_at", columnDefinition = "timestamp(6) with time zone")
	private Instant completedAt;

	protected MediaProcessingJob() {
	}

	private MediaProcessingJob(Long mediaAssetId, MediaProcessingJobType jobType, Instant now) {
		this.mediaAssetId = mediaAssetId;
		this.jobType = jobType;
		this.status = MediaProcessingJobStatus.PENDING;
		this.nextAttemptAt = now;
	}

	public static MediaProcessingJob create(
			Long mediaAssetId, MediaProcessingJobType jobType, Instant now) {
		if (mediaAssetId == null || mediaAssetId <= 0) {
			throw new IllegalArgumentException("미디어 자산 ID는 필수입니다.");
		}
		if (jobType == null) throw new IllegalArgumentException("미디어 처리 작업 유형은 필수입니다.");
		if (now == null) throw new IllegalArgumentException("작업 생성 시각은 필수입니다.");
		return new MediaProcessingJob(mediaAssetId, jobType, now);
	}

	public void start(Instant now) {
		if (now == null) throw new IllegalArgumentException("작업 시작 시각은 필수입니다.");
		if (status != MediaProcessingJobStatus.PENDING && status != MediaProcessingJobStatus.RETRY_WAIT) {
			throw new IllegalStateException("대기 중인 작업만 시작할 수 있습니다.");
		}
		if (nextAttemptAt != null && nextAttemptAt.isAfter(now)) {
			throw new IllegalStateException("아직 재시도 시각이 되지 않았습니다.");
		}
		status = MediaProcessingJobStatus.PROCESSING;
		attemptCount++;
		startedAt = now;
		nextAttemptAt = null;
	}

	public void scheduleRetry(String error, Instant nextAttemptAt) {
		requireProcessing();
		if (error == null || error.isBlank()) throw new IllegalArgumentException("실패 사유는 필수입니다.");
		if (nextAttemptAt == null) throw new IllegalArgumentException("재시도 시각은 필수입니다.");
		status = MediaProcessingJobStatus.RETRY_WAIT;
		lastError = error;
		this.nextAttemptAt = nextAttemptAt;
	}

	public void complete(Instant now) {
		requireProcessing();
		if (now == null) throw new IllegalArgumentException("작업 완료 시각은 필수입니다.");
		status = MediaProcessingJobStatus.COMPLETED;
		lastError = null;
		completedAt = now;
	}

	public void fail(String error, Instant now) {
		requireProcessing();
		if (error == null || error.isBlank()) throw new IllegalArgumentException("실패 사유는 필수입니다.");
		if (now == null) throw new IllegalArgumentException("작업 종료 시각은 필수입니다.");
		status = MediaProcessingJobStatus.FAILED;
		lastError = error;
		completedAt = now;
	}

	public void recoverInterrupted(Instant nextAttemptAt) {
		requireProcessing();
		if (nextAttemptAt == null) throw new IllegalArgumentException("복구 시각은 필수입니다.");
		status = MediaProcessingJobStatus.RETRY_WAIT;
		lastError = "백엔드 재시작 또는 작업 시간 초과로 재시도합니다.";
		this.nextAttemptAt = nextAttemptAt;
	}

	private void requireProcessing() {
		if (status != MediaProcessingJobStatus.PROCESSING) {
			throw new IllegalStateException("처리 중인 작업만 변경할 수 있습니다.");
		}
	}

	public Long getId() { return id; }
	public Long getMediaAssetId() { return mediaAssetId; }
	public MediaProcessingJobType getJobType() { return jobType; }
	public MediaProcessingJobStatus getStatus() { return status; }
	public int getAttemptCount() { return attemptCount; }
	public Instant getNextAttemptAt() { return nextAttemptAt; }
	public String getLastError() { return lastError; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getCompletedAt() { return completedAt; }
}
