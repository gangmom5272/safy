package com.ojimasam.safy.admin.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.presence")
public record OperatorPresenceProperties(
		Duration offlineGrace,
		Duration inProgressReleaseGrace
) {
	public OperatorPresenceProperties {
		if (offlineGrace == null || offlineGrace.isNegative() || offlineGrace.isZero()) {
			offlineGrace = Duration.ofSeconds(90);
		}
		if (inProgressReleaseGrace == null || inProgressReleaseGrace.isNegative()
				|| inProgressReleaseGrace.isZero()) {
			inProgressReleaseGrace = Duration.ofMinutes(3);
		}
	}
}
