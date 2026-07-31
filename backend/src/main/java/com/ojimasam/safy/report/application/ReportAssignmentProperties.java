package com.ojimasam.safy.report.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.report-assignment")
public record ReportAssignmentProperties(
		Duration assignedTimeout
) {
	public ReportAssignmentProperties {
		if (assignedTimeout == null || assignedTimeout.isNegative() || assignedTimeout.isZero()) {
			assignedTimeout = Duration.ofMinutes(5);
		}
	}
}
