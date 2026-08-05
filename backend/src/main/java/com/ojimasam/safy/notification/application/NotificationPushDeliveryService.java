package com.ojimasam.safy.notification.application;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.NotificationPushDeliveryJob;
import com.ojimasam.safy.notification.domain.WebPushSubscription;
import com.ojimasam.safy.notification.domain.type.WebPushSubscriptionStatus;
import com.ojimasam.safy.notification.infrastructure.JpaNotificationPushDeliveryJobRepository;
import com.ojimasam.safy.notification.infrastructure.JpaWebPushSubscriptionRepository;

@Service
public class NotificationPushDeliveryService {
	private final JpaWebPushSubscriptionRepository subscriptionRepository;
	private final JpaNotificationPushDeliveryJobRepository jobRepository;
	private final NotificationSettingsService settingsService;
	private final Clock clock;

	@Autowired
	public NotificationPushDeliveryService(
			JpaWebPushSubscriptionRepository subscriptionRepository,
			JpaNotificationPushDeliveryJobRepository jobRepository,
			NotificationSettingsService settingsService) {
		this(subscriptionRepository, jobRepository, settingsService, Clock.systemUTC());
	}

	NotificationPushDeliveryService(JpaWebPushSubscriptionRepository subscriptionRepository,
			JpaNotificationPushDeliveryJobRepository jobRepository,
			NotificationSettingsService settingsService, Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.jobRepository = jobRepository;
		this.settingsService = settingsService;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueue(Notification notification) {
		if (!settingsService.allows(notification.getRecipientUserId(), notification.getType())) return;

		List<WebPushSubscription> subscriptions = subscriptionRepository.findAllByRecipientUserIdAndStatus(
				notification.getRecipientUserId(), WebPushSubscriptionStatus.ACTIVE);
		for (WebPushSubscription subscription : subscriptions) {
			jobRepository.save(NotificationPushDeliveryJob.create(
					notification.getId(), subscription.getId(), clock.instant()));
		}
	}
}
