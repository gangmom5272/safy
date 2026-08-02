package com.ojimasam.safy.analysis.domain;

import java.time.Instant;

import com.ojimasam.safy.analysis.domain.risk.RiskCalculation;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.analysis.domain.type.RiskAssessmentStatus;
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

@Entity
@Table(name = "risk_assessments",
		uniqueConstraints = @UniqueConstraint(name = "uk_risk_assessments_report", columnNames = "report_id"),
		indexes = @Index(name = "idx_risk_assessments_level", columnList = "risk_level, assessed_at"))
public class RiskAssessment extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "report_id", nullable = false)
	private Long reportId;

	@Enumerated(EnumType.STRING)
	@Column(name = "assessment_status", nullable = false, length = 30)
	private RiskAssessmentStatus assessmentStatus;

	@Column(name = "final_risk_score")
	private Double finalRiskScore;

	@Enumerated(EnumType.STRING)
	@Column(name = "risk_level", length = 30)
	private RiskLevel riskLevel;

	@Column(name = "peak_visual_congestion_score")
	private Double peakVisualCongestionScore;

	@Column(name = "peak_local_congestion_score")
	private Double peakLocalCongestionScore;

	@Column(name = "persistence_score")
	private Double persistenceScore;

	@Column(name = "growth_score")
	private Double growthScore;

	@Column(name = "risk_confidence", nullable = false)
	private double riskConfidence;

	@Column(name = "confirm_required", nullable = false)
	private boolean confirmRequired;

	@Column(name = "total_frame_count", nullable = false)
	private int totalFrameCount;

	@Column(name = "completed_frame_count", nullable = false)
	private int completedFrameCount;

	@Column(name = "invalid_frame_count", nullable = false)
	private int invalidFrameCount;

	@Column(name = "failed_frame_count", nullable = false)
	private int failedFrameCount;

	@Column(name = "quality_clean_frame_count", nullable = false)
	private int qualityCleanFrameCount;

	@Column(name = "congestion_source_mixed", nullable = false)
	private boolean congestionSourceMixed;

	@Column(name = "representative_blur_asset_id")
	private Long representativeBlurAssetId;

	@Column(name = "risk_rule_version", nullable = false, length = 100)
	private String riskRuleVersion;

	@Column(name = "assessed_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant assessedAt;

	protected RiskAssessment() {
	}

	private RiskAssessment(Long reportId) {
		if (reportId == null || reportId <= 0) throw new IllegalArgumentException("제보 ID는 필수입니다.");
		this.reportId = reportId;
	}

	public static RiskAssessment create(Long reportId, RiskCalculation calculation, RiskAssessmentCounts counts,
			Long representativeBlurAssetId, Instant now) {
		RiskAssessment assessment = new RiskAssessment(reportId);
		assessment.refresh(calculation, counts, representativeBlurAssetId, now);
		return assessment;
	}

	public void refresh(RiskCalculation calculation, RiskAssessmentCounts counts,
			Long representativeBlurAssetId, Instant now) {
		if (calculation == null || counts == null || now == null) {
			throw new IllegalArgumentException("위험도 계산 결과·집계·시각은 필수입니다.");
		}
		this.assessmentStatus = calculation.assessable()
				? RiskAssessmentStatus.COMPLETED : RiskAssessmentStatus.NOT_ASSESSABLE;
		this.finalRiskScore = calculation.finalRisk();
		this.riskLevel = calculation.riskLevel();
		this.peakVisualCongestionScore = calculation.peakVisualCongestion();
		this.peakLocalCongestionScore = calculation.peakLocalCongestion();
		this.persistenceScore = calculation.persistence();
		this.growthScore = calculation.growth();
		this.riskConfidence = calculation.riskConfidence();
		this.confirmRequired = calculation.confirmRequired();
		this.totalFrameCount = counts.total();
		this.completedFrameCount = counts.completed();
		this.invalidFrameCount = counts.invalid();
		this.failedFrameCount = counts.failed();
		this.qualityCleanFrameCount = counts.qualityClean();
		this.congestionSourceMixed = counts.congestionSourceMixed();
		this.representativeBlurAssetId = representativeBlurAssetId;
		this.riskRuleVersion = calculation.riskRuleVersion();
		this.assessedAt = now;
	}

	public Long getId() { return id; }
	public Long getReportId() { return reportId; }
	public RiskAssessmentStatus getAssessmentStatus() { return assessmentStatus; }
	public Double getFinalRiskScore() { return finalRiskScore; }
	public RiskLevel getRiskLevel() { return riskLevel; }
	public Double getPeakVisualCongestionScore() { return peakVisualCongestionScore; }
	public Double getPeakLocalCongestionScore() { return peakLocalCongestionScore; }
	public Double getPersistenceScore() { return persistenceScore; }
	public Double getGrowthScore() { return growthScore; }
	public double getRiskConfidence() { return riskConfidence; }
	public boolean isConfirmRequired() { return confirmRequired; }
	public int getTotalFrameCount() { return totalFrameCount; }
	public int getCompletedFrameCount() { return completedFrameCount; }
	public int getInvalidFrameCount() { return invalidFrameCount; }
	public int getFailedFrameCount() { return failedFrameCount; }
	public int getQualityCleanFrameCount() { return qualityCleanFrameCount; }
	public boolean isCongestionSourceMixed() { return congestionSourceMixed; }
	public Long getRepresentativeBlurAssetId() { return representativeBlurAssetId; }
	public String getRiskRuleVersion() { return riskRuleVersion; }
	public Instant getAssessedAt() { return assessedAt; }
}
