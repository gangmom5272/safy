package com.ojimasam.safy.publicdata.infrastructure;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.public-data")
public record PublicDataProperties(
		URI baseUrl,
		Duration connectTimeout,
		Duration readTimeout
) {
	public PublicDataProperties {
		if (baseUrl == null) baseUrl = URI.create("http://localhost:8000");
		if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
		// 혼잡도·행사 요청은 FastAPI 가 장소 121곳을 조회하는 동안 대기하므로 짧게 두면 항상 타임아웃된다.
		if (readTimeout == null) readTimeout = Duration.ofMinutes(2);
	}
}
