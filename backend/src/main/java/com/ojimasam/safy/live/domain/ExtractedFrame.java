package com.ojimasam.safy.live.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 라이브 녹화 구간에서 추출되어 MinIO와 media_assets에 등록된 프레임. */
@Entity
@Table(
		name = "extracted_frames",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_extracted_frames_media_asset", columnNames = "media_asset_id"),
				@UniqueConstraint(
						name = "uk_extracted_frames_recording_sequence",
						columnNames = {"stream_recording_id", "sequence_number"}
				)
		},
		indexes = @Index(name = "idx_extracted_frames_recording", columnList = "stream_recording_id, captured_at")
)
public class ExtractedFrame extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "stream_recording_id", nullable = false)
	private Long streamRecordingId;

	@Column(name = "media_asset_id", nullable = false)
	private Long mediaAssetId;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	@Column(name = "captured_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant capturedAt;

	protected ExtractedFrame() {
	}

	private ExtractedFrame(Long streamRecordingId, Long mediaAssetId, int sequenceNumber, Instant capturedAt) {
		this.streamRecordingId = streamRecordingId;
		this.mediaAssetId = mediaAssetId;
		this.sequenceNumber = sequenceNumber;
		this.capturedAt = capturedAt;
	}

	public static ExtractedFrame create(
			Long streamRecordingId, Long mediaAssetId, int sequenceNumber, Instant capturedAt) {
		if (streamRecordingId == null || streamRecordingId <= 0) {
			throw new IllegalArgumentException("녹화 ID는 필수입니다.");
		}
		if (mediaAssetId == null || mediaAssetId <= 0) {
			throw new IllegalArgumentException("미디어 자산 ID는 필수입니다.");
		}
		if (sequenceNumber < 0) throw new IllegalArgumentException("프레임 순번은 0 이상이어야 합니다.");
		if (capturedAt == null) throw new IllegalArgumentException("프레임 캡처 시각은 필수입니다.");
		return new ExtractedFrame(streamRecordingId, mediaAssetId, sequenceNumber, capturedAt);
	}

	public Long getId() { return id; }
	public Long getStreamRecordingId() { return streamRecordingId; }
	public Long getMediaAssetId() { return mediaAssetId; }
	public int getSequenceNumber() { return sequenceNumber; }
	public Instant getCapturedAt() { return capturedAt; }
}
