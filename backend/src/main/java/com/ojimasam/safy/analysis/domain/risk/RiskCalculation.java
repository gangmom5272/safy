package com.ojimasam.safy.analysis.domain.risk;

public record RiskCalculation(
		Double finalRisk,
		RiskLevel riskLevel,
		Double peakVisualCongestion,
		Double peakLocalCongestion,
		Double persistence,
		Double growth,
		double riskConfidence,
		boolean confirmRequired,
		String riskRuleVersion,
		Long representativeSourceMediaAssetId
) {
	public boolean assessable() {
		return finalRisk != null && riskLevel != null;
	}
}
