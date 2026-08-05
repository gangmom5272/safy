package com.ojimasam.safy.notification.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ojimasam.safy.notification.application.NotificationSettingsService;
import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.WebPushSubscription;
import com.ojimasam.safy.notification.domain.type.NotificationPushDeliveryStatus;
import com.ojimasam.safy.notification.domain.type.WebPushSubscriptionStatus;

@Component
public class NotificationPushDeliveryWorker {
	private static final Logger log = LoggerFactory.getLogger(NotificationPushDeliveryWorker.class);
	private static final List<NotificationPushDeliveryStatus> READY = List.of(
			NotificationPushDeliveryStatus.PENDING, NotificationPushDeliveryStatus.RETRY_WAIT);
	private static final int BATCH_SIZE = 20;

	private final JpaNotificationPushDeliveryJobRepository jobs;
	private final JpaNotificationRepository notifications;
	private final JpaWebPushSubscriptionRepository subscriptions;
	private final NotificationSettingsService settingsService;
	private final WebPushSender sender;
	private final WebPushProperties properties;
	private final TransactionTemplate transactions;
	private final Clock clock;

	@Autowired
	public NotificationPushDeliveryWorker(JpaNotificationPushDeliveryJobRepository jobs,
			JpaNotificationRepository notifications, JpaWebPushSubscriptionRepository subscriptions,
			NotificationSettingsService settingsService, WebPushSender sender,
			WebPushProperties properties, TransactionTemplate transactions) {
		this(jobs, notifications, subscriptions, settingsService, sender,
				properties, transactions, Clock.systemUTC());
	}

	NotificationPushDeliveryWorker(JpaNotificationPushDeliveryJobRepository jobs,
			JpaNotificationRepository notifications, JpaWebPushSubscriptionRepository subscriptions,
			NotificationSettingsService settingsService, WebPushSender sender,
			WebPushProperties properties, TransactionTemplate transactions, Clock clock) {
		this.jobs = jobs;
		this.notifications = notifications;
		this.subscriptions = subscriptions;
		this.settingsService = settingsService;
		this.sender = sender;
		this.properties = properties;
		this.transactions = transactions;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.web-push.scheduler-delay:PT1S}")
	public void processPending() {
		if (!sender.isConfigured()) return;
		recoverInterrupted();
		for (int index = 0; index < BATCH_SIZE; index++) {
			Optional<Target> target = claim();
			if (target.isEmpty()) return;
			if (target.get().notification() != null) process(target.get());
		}
	}

	private void recoverInterrupted() {
		Instant now = clock.instant();
		transactions.executeWithoutResult(status -> jobs
				.findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
						NotificationPushDeliveryStatus.PROCESSING, now.minus(properties.processingTimeout()))
				.forEach(job -> job.recoverInterrupted(now)));
	}

	private Optional<Target> claim() {
		return transactions.execute(status -> jobs
				.findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(READY, clock.instant())
				.map(job -> {
					job.start(clock.instant());
					Notification notification = notifications.findById(job.getNotificationId()).orElse(null);
					WebPushSubscription subscription = subscriptions.findById(job.getSubscriptionId()).orElse(null);
					if (notification == null || subscription == null
							|| subscription.getStatus() != WebPushSubscriptionStatus.ACTIVE) {
						job.complete(clock.instant());
						return new Target(job.getId(), job.getAttemptCount(), null, null);
					}
					if (!settingsService.allows(notification.getRecipientUserId(), notification.getType())) {
						job.complete(clock.instant());
						return new Target(job.getId(), job.getAttemptCount(), null, null);
					}
					return new Target(job.getId(), job.getAttemptCount(), notification, subscription);
				}));
	}

	private void process(Target target) {
		WebPushSendResult result = sender.send(target.notification(), target.subscription());
		transactions.executeWithoutResult(status -> jobs.findByIdForUpdate(target.jobId()).ifPresent(job -> {
			if (job.getStatus() != NotificationPushDeliveryStatus.PROCESSING) return;
			if (result == WebPushSendResult.SUCCESS || result == WebPushSendResult.EXPIRED) {
				job.complete(clock.instant());
				if (result == WebPushSendResult.EXPIRED) {
					subscriptions.findById(target.subscription().getId()).ifPresent(WebPushSubscription::expire);
				}
			} else if (job.getAttemptCount() >= properties.maxAttempts()) {
				job.fail("Web Push 전송 재시도 횟수를 초과했습니다.", clock.instant());
			} else {
				job.scheduleRetry("Web Push 서비스가 요청을 처리하지 못했습니다.",
						clock.instant().plus(properties.retryDelay().multipliedBy(job.getAttemptCount())));
			}
		}));
		if (result == WebPushSendResult.FAILED) {
			log.warn("Web Push 전송 작업이 실패했습니다. jobId={}, attempt={}",
					target.jobId(), target.attemptCount());
		}
	}

	private record Target(Long jobId, int attemptCount, Notification notification,
			WebPushSubscription subscription) {
	}
}
