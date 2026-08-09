package com.ojimasam.safy.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.notification.domain.type.NotificationPushDeliveryStatus;

class NotificationPushDeliveryJobTest {
	@Test
	void retriesInterruptedJobAndEventuallyCompletes() {
		Instant createdAt = Instant.parse("2026-08-04T00:00:00Z");
		NotificationPushDeliveryJob job = NotificationPushDeliveryJob.create(1L, 2L, createdAt);

		job.start(createdAt);
		job.recoverInterrupted(createdAt.plusSeconds(60));
		job.start(createdAt.plusSeconds(60));
		job.complete(createdAt.plusSeconds(61));

		assertEquals(NotificationPushDeliveryStatus.COMPLETED, job.getStatus());
		assertEquals(2, job.getAttemptCount());
	}
}
