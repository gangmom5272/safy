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
		// 정상 프레임은 YOLO+P2PNet 직렬 실행이라 P2PNet 추론(운영 CPU 수 초)보다
		// read timeout 이 먼저 끝나면 안 된다. application.yml 기본값과 함께 맞춘다.
		if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
			readTimeout = Duration.ofSeconds(30);
		}
	}
}
