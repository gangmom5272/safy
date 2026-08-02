package com.ojimasam.safy.analysis.infrastructure;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.server")
public record AiServerProperties(
		URI baseUrl,
		Duration connectTimeout,
		Duration readTimeout
) {
	public AiServerProperties {
		if (baseUrl == null) baseUrl = URI.create("http://localhost:8000");
		if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
			connectTimeout = Duration.ofSeconds(2);
		}
		if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
			readTimeout = Duration.ofSeconds(10);
		}
	}
}
