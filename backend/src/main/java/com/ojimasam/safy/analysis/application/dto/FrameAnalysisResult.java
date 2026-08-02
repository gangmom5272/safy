package com.ojimasam.safy.analysis.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FrameAnalysisResult(
		FrameAnalysisStatus analysisStatus,
		Integer personCount,
		Double visualCongestionScore,
		Double localPeakCongestionScore,
		CongestionSource congestionSource,
		List<String> qualityIssues,
		List<String> failureCodes,
		Integer frameWidth,
		Integer frameHeight,
		Double brightnessScore,
		Double blurScore,
		String modelName,
		String modelVersion,
		String modelWeightId,
		String congestionRuleVersion,
		String frameQualityRuleVersion,
		Integer processingTimeMs
) {
	public FrameAnalysisResult {
		qualityIssues = qualityIssues == null ? List.of() : List.copyOf(qualityIssues);
		failureCodes = failureCodes == null ? List.of() : List.copyOf(failureCodes);
	}
}
