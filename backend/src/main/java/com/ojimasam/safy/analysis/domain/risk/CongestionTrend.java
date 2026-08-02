package com.ojimasam.safy.analysis.domain.risk;

public record CongestionTrend(
		Double persistence,
		Double growth,
		Double peakVisualCongestion,
		Double peakLocalCongestion
) {
	public static CongestionTrend unknown() {
		return new CongestionTrend(null, null, null, null);
	}
}
