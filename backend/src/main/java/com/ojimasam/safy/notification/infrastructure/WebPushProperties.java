package com.ojimasam.safy.notification.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ojimasam.safy.notification.domain.type.NotificationType;

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
		Duration retryJitter,
		Duration processingTimeout,
		int maxAttempts,
		int sendConcurrency,
		Duration completedRetention,
		Map<NotificationType, Duration> validity,
		List<String> allowedEndpointHosts
) {
	private static final int MAX_BACKOFF_SHIFT = 7;

	public WebPushProperties {
		connectTimeout = positive(connectTimeout, Duration.ofSeconds(3));
		readTimeout = positive(readTimeout, Duration.ofSeconds(5));
		schedulerDelay = positive(schedulerDelay, Duration.ofSeconds(1));
		retryDelay = positive(retryDelay, Duration.ofMinutes(1));
		// 0 은 지터를 끄겠다는 뜻이므로 positive 가 아니라 음수만 걸러낸다.
		retryJitter = retryJitter == null || retryJitter.isNegative() ? Duration.ofSeconds(30) : retryJitter;
		processingTimeout = positive(processingTimeout, Duration.ofMinutes(1));
		if (maxAttempts <= 0) maxAttempts = 8;
		if (sendConcurrency <= 0) sendConcurrency = 8;
		// 0 은 정리를 하지 않겠다는 뜻이므로 positive 가 아니라 음수만 걸러낸다.
		completedRetention = completedRetention == null || completedRetention.isNegative()
				? Duration.ofDays(7) : completedRetention;
		validity = validity == null ? Map.of() : Map.copyOf(validity);
		allowedEndpointHosts = allowedEndpointHosts == null ? List.of() : List.copyOf(allowedEndpointHosts);
	}

	// 알림이 가치를 유지하는 시간. 푸시 서비스 TTL 과 재시도 종료 시점을 함께 결정한다.
	public Duration validityFor(NotificationType type) {
		Duration configured = validity.get(type);
		return configured == null || configured.isZero() || configured.isNegative()
				? Duration.ofSeconds(ttlSeconds) : configured;
	}

	// 지수 백오프. 유효 시간이 긴 알림을 적은 시도 횟수로 오래 커버한다.
	public Duration backoffFor(int attemptCount) {
		int shift = Math.min(Math.max(attemptCount - 1, 0), MAX_BACKOFF_SHIFT);
		Duration base = retryDelay.multipliedBy(1L << shift);
		long jitterMillis = retryJitter.toMillis();
		return jitterMillis <= 0 ? base : base.plusMillis(ThreadLocalRandom.current().nextLong(jitterMillis + 1));
	}

	private static Duration positive(Duration value, Duration fallback) {
		return value == null || value.isZero() || value.isNegative() ? fallback : value;
	}
}
