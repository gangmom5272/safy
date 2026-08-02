package com.ojimasam.safy.analysis.domain.risk;

import java.util.Comparator;
import java.util.List;

public final class RiskAssessor {
	public static final String RISK_RULE_VERSION = "safy-risk-v0.1";
	private static final double WEIGHT_VISUAL = 0.4;
	private static final double WEIGHT_LOCAL_PEAK = 0.6;
	private static final double WEIGHT_PERSISTENCE = 0.15;
	private static final double WEIGHT_GROWTH = 0.15;
	private static final double EXCEPTION_LOCAL_PEAK_HIGH = 80.0;
	private static final double EXCEPTION_LOCAL_PEAK_VERY_HIGH = 90.0;
	private static final double EXCEPTION_PERSISTENCE_VERY_HIGH = 70.0;
	private static final int FULL_CONFIDENCE_FRAME_COUNT = 12;
	private static final double CONFIRM_REQUIRED_CONFIDENCE = 0.5;

	private RiskAssessor() {
	}

	public static RiskCalculation assess(List<FrameObservation> completedFrames, int totalFrameCount,
			int qualityCleanFrameCount, boolean congestionSourceMixed) {
		CongestionTrend trend = CongestionTrendCalculator.calculate(completedFrames);
		Long representative = representative(completedFrames);
		if (trend.peakLocalCongestion() == null || trend.peakVisualCongestion() == null) {
			return new RiskCalculation(null, null, null, null, null, null, 0.0, true,
					RISK_RULE_VERSION, representative);
		}

		Double persistence = congestionSourceMixed ? null : trend.persistence();
		Double growth = congestionSourceMixed ? null : trend.growth();
		double bonus = (persistence == null ? 0.0 : WEIGHT_PERSISTENCE * persistence)
				+ (growth == null ? 0.0 : WEIGHT_GROWTH * growth);
		double finalRisk = roundToOneDecimal(Math.max(0.0, Math.min(100.0,
				WEIGHT_VISUAL * trend.peakVisualCongestion()
						+ WEIGHT_LOCAL_PEAK * trend.peakLocalCongestion() + bonus)));
		RiskLevel level = applyExceptionRules(
				RiskLevel.fromScore(finalRisk), trend.peakLocalCongestion(), persistence);
		double confidence = calculateConfidence(completedFrames.size(), totalFrameCount, qualityCleanFrameCount);
		boolean confirmRequired = confidence < CONFIRM_REQUIRED_CONFIDENCE
				|| persistence == null || growth == null || congestionSourceMixed;
		return new RiskCalculation(finalRisk, level, trend.peakVisualCongestion(),
				trend.peakLocalCongestion(), persistence, growth, confidence, confirmRequired,
				RISK_RULE_VERSION, representative);
	}

	private static RiskLevel applyExceptionRules(RiskLevel level, double localPeak, Double persistence) {
		if (localPeak >= EXCEPTION_LOCAL_PEAK_VERY_HIGH
				&& persistence != null && persistence >= EXCEPTION_PERSISTENCE_VERY_HIGH) {
			return level.raiseTo(RiskLevel.VERY_HIGH);
		}
		if (localPeak >= EXCEPTION_LOCAL_PEAK_HIGH) return level.raiseTo(RiskLevel.HIGH);
		return level;
	}

	private static double calculateConfidence(int analyzed, int total, int qualityClean) {
		if (analyzed == 0 || total <= 0) return 0.0;
		double sample = Math.min((double) analyzed / FULL_CONFIDENCE_FRAME_COUNT, 1.0);
		double inclusion = (double) analyzed / total;
		double quality = (double) qualityClean / analyzed;
		return Math.max(0.0, Math.min(1.0, sample * inclusion * quality));
	}

	private static Long representative(List<FrameObservation> frames) {
		if (frames == null) return null;
		return frames.stream()
				.max(Comparator.comparingDouble(FrameObservation::localPeakCongestionScore)
						.thenComparingDouble(FrameObservation::visualCongestionScore))
				.map(FrameObservation::sourceMediaAssetId)
				.orElse(null);
	}

	private static double roundToOneDecimal(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
