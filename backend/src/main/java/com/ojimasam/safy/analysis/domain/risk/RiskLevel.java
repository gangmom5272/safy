package com.ojimasam.safy.analysis.domain.risk;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RiskLevel {
	VERY_LOW(1),
	LOW(2),
	MEDIUM(3),
	HIGH(4),
	VERY_HIGH(5);

	private final int order;

	RiskLevel(int order) {
		this.order = order;
	}

	@JsonValue
	public String jsonValue() {
		return name().toLowerCase();
	}

	public RiskLevel raiseTo(RiskLevel minimum) {
		return order >= minimum.order ? this : minimum;
	}

	public static RiskLevel fromScore(double score) {
		if (score >= 81.0) return VERY_HIGH;
		if (score >= 61.0) return HIGH;
		if (score >= 41.0) return MEDIUM;
		if (score >= 21.0) return LOW;
		return VERY_LOW;
	}
}
