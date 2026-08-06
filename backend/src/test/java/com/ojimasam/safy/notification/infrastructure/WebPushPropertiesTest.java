package com.ojimasam.safy.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.notification.domain.type.NotificationType;

class WebPushPropertiesTest {
	@Test
	void doublesBackoffSoLongLivedNotificationsSurviveFewAttempts() {
		WebPushProperties properties = properties(Duration.ZERO);

		assertEquals(Duration.ofMinutes(1), properties.backoffFor(1));
		assertEquals(Duration.ofMinutes(2), properties.backoffFor(2));
		assertEquals(Duration.ofMinutes(4), properties.backoffFor(3));
		assertEquals(Duration.ofMinutes(128), properties.backoffFor(8));
	}

	@Test
	void capsBackoffGrowthSoDelayCannotOverflow() {
		assertEquals(Duration.ofMinutes(128), properties(Duration.ZERO).backoffFor(20));
	}

	@Test
	void spreadsRetryWithinConfiguredJitterWindow() {
		WebPushProperties properties = properties(Duration.ofSeconds(30));

		for (int attemptCount = 1; attemptCount <= 8; attemptCount++) {
			Duration base = Duration.ofMinutes(1L << (attemptCount - 1));
			for (int trial = 0; trial < 50; trial++) {
				Duration backoff = properties.backoffFor(attemptCount);
				assertTrue(backoff.compareTo(base) >= 0, "지터가 기본 대기보다 앞당기면 안 된다");
				assertTrue(backoff.compareTo(base.plusSeconds(30)) <= 0, "지터가 설정된 창을 넘으면 안 된다");
			}
		}
	}

	@Test
	void producesDifferentDelaysSoRecoveredJobsDoNotFireTogether() {
		WebPushProperties properties = properties(Duration.ofSeconds(30));

		long distinct = IntStream.range(0, 100)
				.mapToObj(trial -> properties.backoffFor(1))
				.distinct()
				.count();

		assertTrue(distinct > 1, "지터가 적용되면 재시도 시각이 한 값으로 몰리지 않는다");
	}

	@Test
	void keepsSafetyValidityShorterThanNoticeValidity() {
		WebPushProperties properties = properties(Duration.ZERO);

		assertEquals(Duration.ofMinutes(15), properties.validityFor(NotificationType.SAFETY));
		assertEquals(Duration.ofHours(24), properties.validityFor(NotificationType.SYSTEM));
	}

	@Test
	void fallsBackToGlobalTtlWhenTypeHasNoConfiguredValidity() {
		assertEquals(Duration.ofSeconds(86400), properties(Duration.ZERO).validityFor(NotificationType.REPORT));
	}

	@Test
	void keepsZeroRetentionAsAnOffSwitchButRejectsNegative() {
		assertEquals(Duration.ZERO, properties(Duration.ZERO, Duration.ZERO).completedRetention());
		assertEquals(Duration.ofDays(7), properties(Duration.ZERO, Duration.ofDays(-1)).completedRetention());
	}

	@Test
	void fallsBackToDefaultJitterWhenMisconfigured() {
		assertEquals(Duration.ofSeconds(30), properties(null).retryJitter());
		assertEquals(Duration.ofSeconds(30), properties(Duration.ofSeconds(-1)).retryJitter());
	}

	private WebPushProperties properties(Duration retryJitter) {
		return properties(retryJitter, Duration.ofDays(7));
	}

	private WebPushProperties properties(Duration retryJitter, Duration completedRetention) {
		return new WebPushProperties(true, "mailto:test@example.com", "public", "private", 86400,
				Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(1),
				Duration.ofMinutes(1), retryJitter, Duration.ofMinutes(1), 8, 8, completedRetention,
				Map.of(NotificationType.SAFETY, Duration.ofMinutes(15),
						NotificationType.SYSTEM, Duration.ofHours(24)),
				List.of("fcm.googleapis.com"));
	}
}
