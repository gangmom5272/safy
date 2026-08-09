package com.ojimasam.safy.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.NotificationPushDeliveryJob;
import com.ojimasam.safy.notification.domain.WebPushSubscription;
import com.ojimasam.safy.notification.domain.type.NotificationType;
import com.ojimasam.safy.notification.domain.type.WebPushSubscriptionStatus;
import com.ojimasam.safy.notification.infrastructure.JpaNotificationPushDeliveryJobRepository;
import com.ojimasam.safy.notification.infrastructure.JpaWebPushSubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class NotificationPushDeliveryServiceTest {
	@Mock JpaWebPushSubscriptionRepository subscriptions;
	@Mock JpaNotificationPushDeliveryJobRepository jobs;
	@Mock NotificationSettingsService settings;

	private NotificationPushDeliveryService service;
	private Notification notification;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);
		service = new NotificationPushDeliveryService(subscriptions, jobs, settings, clock);
		notification = Notification.create(10L, NotificationType.REPORT, "title", "content", 20L, 30L);
		ReflectionTestUtils.setField(notification, "id", 40L);
	}

	@Test
	void createsOneDurableJobPerActiveSubscription() {
		WebPushSubscription subscription = WebPushSubscription.create(
				10L, "https://fcm.googleapis.com/fcm/send/token", "hash", "key", "auth", null);
		ReflectionTestUtils.setField(subscription, "id", 50L);
		when(settings.allows(10L, NotificationType.REPORT)).thenReturn(true);
		when(subscriptions.findAllByRecipientUserIdAndStatus(10L, WebPushSubscriptionStatus.ACTIVE))
				.thenReturn(List.of(subscription));

		service.enqueue(notification);

		verify(jobs).save(any(NotificationPushDeliveryJob.class));
	}

	@Test
	void skipsJobsWhenUserDisabledThisNotificationType() {
		when(settings.allows(10L, NotificationType.REPORT)).thenReturn(false);

		service.enqueue(notification);

		verify(jobs, never()).save(any());
	}
}
