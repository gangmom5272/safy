package com.ojimasam.safy.notification.infrastructure;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.web-push")
public record WebPushProperties(
		boolean enabled,
		String subject,
		String publicKey,
		String privateKey,
		long ttlSeconds,
		Duration connectTimeout,
		Duration readTimeout,
		Duration schedulerDelay,
		Duration retryDelay,
		Duration processingTimeout,
		int maxAttempts,
		List<String> allowedEndpointHosts
) {
	public WebPushProperties {
		connectTimeout = positive(connectTimeout, Duration.ofSeconds(3));
		readTimeout = positive(readTimeout, Duration.ofSeconds(5));
		schedulerDelay = positive(schedulerDelay, Duration.ofSeconds(1));
		retryDelay = positive(retryDelay, Duration.ofMinutes(1));
		processingTimeout = positive(processingTimeout, Duration.ofMinutes(1));
		if (maxAttempts <= 0) maxAttempts = 5;
		allowedEndpointHosts = allowedEndpointHosts == null ? List.of() : List.copyOf(allowedEndpointHosts);
	}

	private static Duration positive(Duration value, Duration fallback) {
		return value == null || value.isZero() || value.isNegative() ? fallback : value;
	}
}
