package com.ojimasam.safy.report.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.report-routing")
public record ReportRoutingProperties(
		int maxAttempts,
		Duration retryDelay,
		int batchSize
) {
	public ReportRoutingProperties {
		if (maxAttempts < 1) maxAttempts = 5;
		if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) retryDelay = Duration.ofMinutes(1);
		if (batchSize < 1) batchSize = 50;
	}
}
