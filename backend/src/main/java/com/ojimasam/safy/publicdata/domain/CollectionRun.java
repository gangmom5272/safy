package com.ojimasam.safy.publicdata.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;
import com.ojimasam.safy.publicdata.domain.type.CollectionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * FastAPI 서버가 공공데이터 원본 API를 호출한 실행 이력.
 * 우리가 FastAPI를 호출한 기록이 아니라 FastAPI 응답에 동봉되어 오는 메타데이터이므로,
 * 같은 실행을 두 번 적재하지 않도록 externalRunId 로 식별한다.
 */
@Entity
@Table(name = "data_collection_runs")
@EntityListeners(AuditingEntityListener.class)
public class CollectionRun {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_run_id", nullable = false, unique = true, length = 100)
	private String externalRunId;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 50)
	private CollectionJobType jobType;

	@Column(nullable = false, length = 50)
	private String source;

	@Column(name = "started_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant startedAt;

	@Column(name = "finished_at", columnDefinition = "timestamp(6) with time zone")
	private Instant finishedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CollectionStatus status;

	@Column(name = "success_count")
	private Integer successCount;

	@Column(name = "failure_count")
	private Integer failureCount;

	@Column(name = "error_code", length = 100)
	private String errorCode;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "error_details")
	private String errorDetails;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant createdAt;

	protected CollectionRun() {
	}

	private CollectionRun(
			String externalRunId,
			CollectionJobType jobType,
			String source,
			Instant startedAt,
			Instant finishedAt,
			CollectionStatus status,
			Integer successCount,
			Integer failureCount,
			String errorCode,
			String errorMessage,
			String errorDetails
	) {
		this.externalRunId = externalRunId;
		this.jobType = jobType;
		this.source = source;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.status = status;
		this.successCount = successCount;
		this.failureCount = failureCount;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.errorDetails = errorDetails;
	}

	public static CollectionRun create(
			String externalRunId,
			CollectionJobType jobType,
			String source,
			Instant startedAt,
			Instant finishedAt,
			CollectionStatus status,
			Integer successCount,
			Integer failureCount,
			String errorCode,
			String errorMessage,
			String errorDetails
	) {
		return new CollectionRun(
				externalRunId,
				jobType,
				source,
				startedAt,
				finishedAt,
				status,
				successCount,
				failureCount,
				errorCode,
				errorMessage,
				errorDetails
		);
	}

	public Long getId() {
		return id;
	}

	public String getExternalRunId() {
		return externalRunId;
	}

	public CollectionJobType getJobType() {
		return jobType;
	}

	public String getSource() {
		return source;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public CollectionStatus getStatus() {
		return status;
	}

	public Integer getSuccessCount() {
		return successCount;
	}

	public Integer getFailureCount() {
		return failureCount;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getErrorDetails() {
		return errorDetails;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
