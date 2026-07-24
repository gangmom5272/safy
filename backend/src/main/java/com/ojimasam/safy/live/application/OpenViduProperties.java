package com.ojimasam.safy.live.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openvidu")
public record OpenViduProperties(String url, String apiKey, String apiSecret,
		Duration tokenTtl, Duration sessionTtl, Duration cleanupDelay) {
	public OpenViduProperties {
		tokenTtl = tokenTtl == null ? Duration.ofMinutes(10) : tokenTtl;
		sessionTtl = sessionTtl == null ? Duration.ofMinutes(30) : sessionTtl;
		cleanupDelay = cleanupDelay == null ? Duration.ofMinutes(1) : cleanupDelay;
	}
}
