package com.ojimasam.safy.analysis.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ojimasam.safy.analysis.application.dto.BlurredImageResult;
import com.ojimasam.safy.analysis.domain.type.MediaBlurStatus;
import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "media_blur_results", uniqueConstraints = {
		@UniqueConstraint(name = "uk_media_blur_results_source", columnNames = "source_media_asset_id"),
		@UniqueConstraint(name = "uk_media_blur_results_blurred", columnNames = "blurred_media_asset_id")
})
public class MediaBlurResult extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "source_media_asset_id", nullable = false)
	private Long sourceMediaAssetId;

	@Column(name = "blurred_media_asset_id")
	private Long blurredMediaAssetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MediaBlurStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "failure_codes", nullable = false)
	private String failureCodes;

	@Column(name = "blur_rule_version", length = 100)
	private String blurRuleVersion;

	@Column(name = "image_width")
	private Integer imageWidth;

	@Column(name = "image_height")
	private Integer imageHeight;

	@Column(name = "processing_time_ms")
	private Integer processingTimeMs;

	@Column(name = "processed_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant processedAt;

	protected MediaBlurResult() {
	}

	private MediaBlurResult(Long sourceMediaAssetId) {
		if (sourceMediaAssetId == null || sourceMediaAssetId <= 0) {
			throw new IllegalArgumentException("원본 미디어 ID는 필수입니다.");
		}
		this.sourceMediaAssetId = sourceMediaAssetId;
	}

	public static MediaBlurResult completed(Long sourceMediaAssetId, Long blurredMediaAssetId,
			BlurredImageResult blur, Instant now) {
		MediaBlurResult result = new MediaBlurResult(sourceMediaAssetId);
		result.complete(blurredMediaAssetId, blur, now);
		return result;
	}

	public static MediaBlurResult failed(Long sourceMediaAssetId, String failureCodes, Instant now) {
		MediaBlurResult result = new MediaBlurResult(sourceMediaAssetId);
		result.recordFailure(failureCodes, now);
		return result;
	}

	public void complete(Long blurredMediaAssetId, BlurredImageResult blur, Instant now) {
		if (blurredMediaAssetId == null || blurredMediaAssetId <= 0) {
			throw new IllegalArgumentException("블러 미디어 ID는 필수입니다.");
		}
		if (blur == null || now == null) throw new IllegalArgumentException("블러 결과와 처리 시각은 필수입니다.");
		this.blurredMediaAssetId = blurredMediaAssetId;
		this.status = MediaBlurStatus.COMPLETED;
		this.failureCodes = "[]";
		this.blurRuleVersion = blur.ruleVersion();
		this.imageWidth = blur.width();
		this.imageHeight = blur.height();
		this.processingTimeMs = blur.processingTimeMs();
		this.processedAt = now;
	}

	public void recordFailure(String failureCodes, Instant now) {
		if (failureCodes == null || failureCodes.isBlank()) throw new IllegalArgumentException("실패 코드는 필수입니다.");
		if (now == null) throw new IllegalArgumentException("처리 시각은 필수입니다.");
		this.blurredMediaAssetId = null;
		this.status = MediaBlurStatus.FAILED;
		this.failureCodes = failureCodes;
		this.blurRuleVersion = null;
		this.imageWidth = null;
		this.imageHeight = null;
		this.processingTimeMs = null;
		this.processedAt = now;
	}

	public Long getId() { return id; }
	public Long getSourceMediaAssetId() { return sourceMediaAssetId; }
	public Long getBlurredMediaAssetId() { return blurredMediaAssetId; }
	public MediaBlurStatus getStatus() { return status; }
	public String getFailureCodes() { return failureCodes; }
	public String getBlurRuleVersion() { return blurRuleVersion; }
	public Integer getImageWidth() { return imageWidth; }
	public Integer getImageHeight() { return imageHeight; }
	public Integer getProcessingTimeMs() { return processingTimeMs; }
	public Instant getProcessedAt() { return processedAt; }
}
