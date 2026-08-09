package com.ojimasam.safy.administrativearea.infrastructure;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.kakao.local")
public record KakaoLocalProperties(
		String restApiKey,
		URI baseUrl,
		Duration connectTimeout,
		Duration readTimeout
) {
	public KakaoLocalProperties {
		if (restApiKey == null) restApiKey = "";
		if (baseUrl == null) baseUrl = URI.create("https://dapi.kakao.com");
		if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
		if (readTimeout == null) readTimeout = Duration.ofSeconds(3);
	}
}
