package com.ojimasam.safy.analysis.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ojimasam.safy.analysis.application.dto.CongestionSource;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisResult;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisStatus;
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
@Table(name = "media_analysis_results", uniqueConstraints = @UniqueConstraint(
		name = "uk_media_analysis_results_source", columnNames = "source_media_asset_id"))
public class MediaAnalysisResult extends BaseEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "source_media_asset_id", nullable = false)
	private Long sourceMediaAssetId;
	@Enumerated(EnumType.STRING) @Column(name = "analysis_status", nullable = false, length = 30)
	private FrameAnalysisStatus analysisStatus;
	@Column(name = "person_count") private Integer personCount;
	@Column(name = "visual_congestion_score") private Double visualCongestionScore;
	@Column(name = "local_peak_congestion_score") private Double localPeakCongestionScore;
	@Enumerated(EnumType.STRING) @Column(name = "congestion_source", nullable = false, length = 30)
	private CongestionSource congestionSource;
	@JdbcTypeCode(SqlTypes.JSON) @Column(name = "quality_issues") private String qualityIssues;
	@JdbcTypeCode(SqlTypes.JSON) @Column(name = "failure_codes") private String failureCodes;
	@Column(name = "frame_width") private Integer frameWidth;
	@Column(name = "frame_height") private Integer frameHeight;
	@Column(name = "brightness_score") private Double brightnessScore;
	@Column(name = "blur_score") private Double blurScore;
	@Column(name = "model_name", nullable = false, length = 50) private String modelName;
	@Column(name = "model_version", nullable = false, length = 100) private String modelVersion;
	@Column(name = "model_weight_id", nullable = false, length = 100) private String modelWeightId;
	@Column(name = "congestion_rule_version", nullable = false, length = 100) private String congestionRuleVersion;
	@Column(name = "frame_quality_rule_version", nullable = false, length = 100) private String frameQualityRuleVersion;
	@Column(name = "processing_time_ms", nullable = false) private int processingTimeMs;
	@Column(name = "observed_at", nullable = false, columnDefinition = "timestamp(6) with time zone") private Instant observedAt;
	@Column(name = "analyzed_at", nullable = false, columnDefinition = "timestamp(6) with time zone") private Instant analyzedAt;

	protected MediaAnalysisResult() { }

	public static MediaAnalysisResult create(Long sourceId, FrameAnalysisResult analysis,
			String qualityIssues, String failureCodes, Instant observedAt, Instant now) {
		if (sourceId == null || sourceId <= 0) throw new IllegalArgumentException("원본 미디어 ID는 필수입니다.");
		if (analysis == null) throw new IllegalArgumentException("AI 분석 응답은 필수입니다.");
		if (observedAt == null || now == null) throw new IllegalArgumentException("관측·분석 시각은 필수입니다.");
		MediaAnalysisResult result = new MediaAnalysisResult();
		result.sourceMediaAssetId = sourceId;
		result.analysisStatus = analysis.analysisStatus();
		result.personCount = analysis.personCount();
		result.visualCongestionScore = analysis.visualCongestionScore();
		result.localPeakCongestionScore = analysis.localPeakCongestionScore();
		result.congestionSource = analysis.congestionSource();
		result.qualityIssues = qualityIssues;
		result.failureCodes = failureCodes;
		result.frameWidth = analysis.frameWidth();
		result.frameHeight = analysis.frameHeight();
		result.brightnessScore = analysis.brightnessScore();
		result.blurScore = analysis.blurScore();
		result.modelName = analysis.modelName();
		result.modelVersion = analysis.modelVersion();
		result.modelWeightId = analysis.modelWeightId();
		result.congestionRuleVersion = analysis.congestionRuleVersion();
		result.frameQualityRuleVersion = analysis.frameQualityRuleVersion();
		result.processingTimeMs = analysis.processingTimeMs();
		result.observedAt = observedAt;
		result.analyzedAt = now;
		return result;
	}

	public Long getId() { return id; }
	public Long getSourceMediaAssetId() { return sourceMediaAssetId; }
	public FrameAnalysisStatus getAnalysisStatus() { return analysisStatus; }
	public Integer getPersonCount() { return personCount; }
	public Double getVisualCongestionScore() { return visualCongestionScore; }
	public Double getLocalPeakCongestionScore() { return localPeakCongestionScore; }
	public CongestionSource getCongestionSource() { return congestionSource; }
	public String getQualityIssues() { return qualityIssues; }
	public String getFailureCodes() { return failureCodes; }
	public Integer getFrameWidth() { return frameWidth; }
	public Integer getFrameHeight() { return frameHeight; }
	public Double getBrightnessScore() { return brightnessScore; }
	public Double getBlurScore() { return blurScore; }
	public String getFrameQualityRuleVersion() { return frameQualityRuleVersion; }
	public Instant getObservedAt() { return observedAt; }
	public Instant getAnalyzedAt() { return analyzedAt; }

	public boolean hasNoQualityIssues() {
		return qualityIssues == null || qualityIssues.isBlank() || "[]".equals(qualityIssues.trim());
	}
}
