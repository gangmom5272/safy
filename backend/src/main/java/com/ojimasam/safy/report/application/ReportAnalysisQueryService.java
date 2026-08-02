package com.ojimasam.safy.report.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.analysis.application.dto.FrameAnalysisStatus;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResultRepository;
import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.report.application.dto.ReportAnalysisResponse;

@Service
@Transactional(readOnly = true)
public class ReportAnalysisQueryService {
	private final RiskAssessmentRepository riskAssessments;
	private final MediaAnalysisResultRepository analysisResults;

	public ReportAnalysisQueryService(RiskAssessmentRepository riskAssessments,
			MediaAnalysisResultRepository analysisResults) {
		this.riskAssessments = riskAssessments;
		this.analysisResults = analysisResults;
	}

	public ReportAnalysisResponse get(Long reportId) {
		RiskAssessment assessment = riskAssessments.findByReportId(reportId).orElse(null);
		if (assessment == null) return ReportAnalysisResponse.pending(reportId);
		Integer crowdCount = analysisResults.findAllByReportId(reportId).stream()
				.filter(result -> result.getAnalysisStatus() == FrameAnalysisStatus.COMPLETED)
				.map(result -> result.getPersonCount())
				.filter(java.util.Objects::nonNull)
				.max(Integer::compareTo)
				.orElse(null);
		return new ReportAnalysisResponse(
				reportId,
				assessment.getAssessmentStatus().name(),
				null,
				assessment.getRiskLevel() == null ? null : assessment.getRiskLevel().jsonValue(),
				assessment.getFinalRiskScore(),
				crowdCount,
				assessment.getRiskConfidence(),
				assessment.isConfirmRequired(),
				assessment.getPeakVisualCongestionScore(),
				assessment.getPeakLocalCongestionScore(),
				assessment.getPersistenceScore(),
				assessment.getGrowthScore(),
				assessment.getTotalFrameCount(),
				assessment.getCompletedFrameCount(),
				assessment.getInvalidFrameCount(),
				assessment.getFailedFrameCount(),
				assessment.getRiskRuleVersion(),
				assessment.getAssessedAt());
	}
}
