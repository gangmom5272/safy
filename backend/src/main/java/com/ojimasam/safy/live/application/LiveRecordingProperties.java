package com.ojimasam.safy.live.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 미디어 서버(Egress)가 녹화본과 추출 프레임을 오브젝트 스토리지에 직접 업로드할 때 사용하는 설정.
 * Egress 컨테이너에서 접근 가능한 내부 엔드포인트여야 한다.
 */
@ConfigurationProperties("live.recording")
public record LiveRecordingProperties(
		boolean enabled,
		String bucket,
		String s3Endpoint,
		String s3AccessKey,
		String s3SecretKey,
		String region,
		Duration frameInterval,
		int frameWidth,
		int frameHeight,
		int startMaxAttempts,
		Duration startRetryDelay
) {
	public LiveRecordingProperties {
		region = region == null || region.isBlank() ? "us-east-1" : region;
		frameInterval = frameInterval == null || frameInterval.isZero() || frameInterval.isNegative()
				? Duration.ofSeconds(5) : frameInterval;
		frameWidth = frameWidth <= 0 ? 1280 : frameWidth;
		frameHeight = frameHeight <= 0 ? 720 : frameHeight;
		// Egress 시작 실패는 CPU 포화 같은 일시적 사유가 많아 짧게 재시도한다.
		startMaxAttempts = startMaxAttempts <= 0 ? 4 : startMaxAttempts;
		startRetryDelay = startRetryDelay == null || startRetryDelay.isZero() || startRetryDelay.isNegative()
				? Duration.ofSeconds(2) : startRetryDelay;
	}
}
