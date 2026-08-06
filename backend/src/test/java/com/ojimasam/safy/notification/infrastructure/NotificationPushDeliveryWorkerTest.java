package com.ojimasam.safy.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.ojimasam.safy.notification.application.NotificationSettingsService;
import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.NotificationPushDeliveryJob;
import com.ojimasam.safy.notification.domain.WebPushSubscription;
import com.ojimasam.safy.notification.domain.type.NotificationType;

@ExtendWith(MockitoExtension.class)
class NotificationPushDeliveryWorkerTest {
	private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
	private static final int CONCURRENCY = 4;

	@Mock JpaNotificationPushDeliveryJobRepository jobs;
	@Mock JpaNotificationRepository notifications;
	@Mock JpaWebPushSubscriptionRepository subscriptions;
	@Mock NotificationSettingsService settingsService;
	@Mock WebPushSender sender;
	@Mock TransactionTemplate transactions;

	private NotificationPushDeliveryWorker worker;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		lenient().when(transactions.execute(any())).thenAnswer(invocation ->
				invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));
		lenient().doAnswer(invocation -> {
			invocation.getArgument(0, Consumer.class).accept(null);
			return null;
		}).when(transactions).executeWithoutResult(any());

		worker = new NotificationPushDeliveryWorker(jobs, notifications, subscriptions, settingsService,
				sender, properties(), transactions, Clock.fixed(NOW, ZoneOffset.UTC));
		worker.initializeExecutor();
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		worker.shutdownExecutor();
	}

	@Test
	void sendsClaimedJobsConcurrently() {
		when(sender.isConfigured()).thenReturn(true);
		stubClaimableJobs(CONCURRENCY);
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger peakInFlight = new AtomicInteger();
		when(sender.send(any(), any())).thenAnswer(invocation -> {
			peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
			Thread.sleep(50);
			inFlight.decrementAndGet();
			return WebPushSendResult.SUCCESS;
		});

		worker.processPending();

		verify(sender, times(CONCURRENCY)).send(any(), any());
		assertTrue(peakInFlight.get() >= 2,
				"전송이 직렬로 처리되면 안 된다. 관측된 최대 동시 전송 수: " + peakInFlight.get());
	}

	@Test
	void doesNothingWhenSenderIsNotConfigured() {
		when(sender.isConfigured()).thenReturn(false);

		worker.processPending();

		verify(jobs, never()).findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
				any(), any());
		verify(sender, never()).send(any(), any());
	}

	private void stubClaimableJobs(int count) {
		List<NotificationPushDeliveryJob> claimable = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			NotificationPushDeliveryJob job = NotificationPushDeliveryJob.create((long) index, (long) index, NOW);
			ReflectionTestUtils.setField(job, "id", (long) index);
			claimable.add(job);
		}

		OngoingStubbing<Optional<NotificationPushDeliveryJob>> claims = when(jobs
				.findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(any(), any()));
		for (NotificationPushDeliveryJob job : claimable) {
			claims = claims.thenReturn(Optional.of(job));
		}
		claims.thenReturn(Optional.empty());

		claimable.forEach(job -> when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job)));
		when(notifications.findById(any())).thenReturn(Optional.of(notification()));
		when(subscriptions.findById(any())).thenReturn(Optional.of(subscription()));
		when(settingsService.allows(any(), any())).thenReturn(true);
	}

	private Notification notification() {
		Notification notification = Notification.create(10L, NotificationType.SAFETY,
				"title", "content", 20L, 30L);
		ReflectionTestUtils.setField(notification, "id", 40L);
		return notification;
	}

	private WebPushSubscription subscription() {
		WebPushSubscription subscription = WebPushSubscription.create(10L,
				"https://fcm.googleapis.com/fcm/send/token", "hash", "key", "auth", null);
		ReflectionTestUtils.setField(subscription, "id", 50L);
		return subscription;
	}

	private WebPushProperties properties() {
		return new WebPushProperties(true, "mailto:test@example.com", "public", "private", 86400,
				Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(1),
				Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(1), 8, CONCURRENCY,
				Duration.ofDays(7), Map.of(NotificationType.SAFETY, Duration.ofMinutes(15)),
				List.of("fcm.googleapis.com"));
	}
}
