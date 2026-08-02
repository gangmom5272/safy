package com.ojimasam.safy.analysis.application.dto;

public record BlurredImageResult(
		byte[] image,
		String ruleVersion,
		int width,
		int height,
		int processingTimeMs
) {
}
