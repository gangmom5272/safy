package com.ojimasam.safy.live.domain;

import java.time.Instant;
import java.util.UUID;

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
import com.ojimasam.safy.live.domain.type.StreamRecordingStatus;

/**
 * 라이브 세션의 원본 녹화 1건. 미디어 서버(Egress)가 스토리지에 직접 업로드하며,
 * 완료 통지를 받은 뒤에야 실제 파일({@code mediaAssetId})이 연결된다.
 * 제보자가 끊겼다 재접속하면 녹화가 새로 시작되므로 한 세션에 여러 건이 생길 수 있다.
 */
@Entity
@Table(
		name = "stream_recordings",
		indexes = {
				@Index(name = "idx_stream_recordings_session", columnList = "live_session_id, status"),
				@Index(name = "idx_stream_recordings_egress", columnList = "provider_egress_id")
		}
)
public class StreamRecording extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "live_session_id", nullable = false)
	private UUID liveSessionId;

	@Column(name = "report_id", nullable = false)
	private Long reportId;

	/** 녹화가 완료돼 스토리지 객체가 확정된 뒤에 채워진다. */
	@Column(name = "media_asset_id")
	private Long mediaAssetId;

	@Column(name = "provider_egress_id", length = 255)
	private String providerEgressId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StreamRecordingStatus status;

	@Column(name = "object_key", nullable = false, length = 500)
	private String objectKey;

	@Column(name = "started_at", columnDefinition = "timestamp(6) with time zone")
	private Instant startedAt;

	@Column(name = "ended_at", columnDefinition = "timestamp(6) with time zone")
	private Instant endedAt;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	protected StreamRecording() {
	}

	private StreamRecording(UUID liveSessionId, Long reportId, String providerEgressId, String objectKey,
			Instant startedAt) {
		this.liveSessionId = liveSessionId;
		this.reportId = reportId;
		this.providerEgressId = providerEgressId;
		this.objectKey = objectKey;
		this.status = StreamRecordingStatus.STARTING;
		this.startedAt = startedAt;
	}

	public static StreamRecording start(UUID liveSessionId, Long reportId, String providerEgressId, String objectKey,
			Instant startedAt) {
		if (liveSessionId == null) throw new IllegalArgumentException("라이브 세션 ID는 필수입니다.");
		if (reportId == null) throw new IllegalArgumentException("제보 ID는 필수입니다.");
		if (providerEgressId == null || providerEgressId.isBlank()) {
			throw new IllegalArgumentException("녹화 작업 ID는 필수입니다.");
		}
		if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("객체 키는 필수입니다.");
		if (startedAt == null) throw new IllegalArgumentException("시작 시각은 필수입니다.");
		return new StreamRecording(liveSessionId, reportId, providerEgressId, objectKey, startedAt);
	}

	public void markActive() {
		if (status == StreamRecordingStatus.STARTING) status = StreamRecordingStatus.ACTIVE;
	}

	/**
	 * 녹화 파일이 스토리지에 확정됐다.
	 *
	 * @return 이번 호출로 완료 처리됐으면 true (중복 웹훅이면 false)
	 */
	public boolean complete(Long mediaAssetId, Long durationMs, Instant endedAt) {
		if (isFinished()) return false;
		if (mediaAssetId == null) throw new IllegalArgumentException("미디어 자산 ID는 필수입니다.");
		this.status = StreamRecordingStatus.COMPLETED;
		this.mediaAssetId = mediaAssetId;
		this.durationMs = durationMs;
		this.endedAt = endedAt;
		return true;
	}

	/** 파일 없이 끝났다(참가자가 즉시 이탈하는 등). */
	public boolean abort(Instant endedAt) {
		if (isFinished()) return false;
		this.status = StreamRecordingStatus.ABORTED;
		this.endedAt = endedAt;
		return true;
	}

	public boolean fail(String reason, Instant endedAt) {
		if (isFinished()) return false;
		this.status = StreamRecordingStatus.FAILED;
		this.failureReason = truncate(reason);
		this.endedAt = endedAt;
		return true;
	}

	public boolean isFinished() {
		return status == StreamRecordingStatus.COMPLETED
				|| status == StreamRecordingStatus.FAILED
				|| status == StreamRecordingStatus.ABORTED;
	}

	private String truncate(String reason) {
		if (reason == null || reason.isBlank()) return null;
		String trimmed = reason.trim();
		return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
	}

	public Long getId() { return id; }
	public UUID getLiveSessionId() { return liveSessionId; }
	public Long getReportId() { return reportId; }
	public Long getMediaAssetId() { return mediaAssetId; }
	public String getProviderEgressId() { return providerEgressId; }
	public StreamRecordingStatus getStatus() { return status; }
	public String getObjectKey() { return objectKey; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getEndedAt() { return endedAt; }
	public Long getDurationMs() { return durationMs; }
	public String getFailureReason() { return failureReason; }
}
