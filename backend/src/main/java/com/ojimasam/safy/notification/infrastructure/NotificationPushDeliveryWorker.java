package com.ojimasam.safy.notification.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class NotificationPushDeliveryWorker {
	private static final Logger log = LoggerFactory.getLogger(NotificationPushDeliveryWorker.class);
	private static final List<NotificationPushDeliveryStatus> READY = List.of(
			NotificationPushDeliveryStatus.PENDING, NotificationPushDeliveryStatus.RETRY_WAIT);
	private static final int BATCH_SIZE = 20;
	private static final long SHUTDOWN_WAIT_SECONDS = 10;

	private final JpaNotificationPushDeliveryJobRepository jobs;
	private final JpaNotificationRepository notifications;
	private final JpaWebPushSubscriptionRepository subscriptions;
	private final NotificationSettingsService settingsService;
	private final WebPushSender sender;
	private final WebPushProperties properties;
	private final TransactionTemplate transactions;
	private final Clock clock;
	private ExecutorService sendExecutor;

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

	@PostConstruct
	void initializeExecutor() {
		sendExecutor = Executors.newFixedThreadPool(properties.sendConcurrency(),
				Thread.ofPlatform().name("web-push-send-", 0).factory());
	}

	@PreDestroy
	void shutdownExecutor() throws InterruptedException {
		if (sendExecutor == null) return;
		sendExecutor.shutdown();
		// 남은 전송을 잠시 기다린다. 그래도 끝나지 않으면 중단된 작업으로 회수되어 재시도된다.
		if (!sendExecutor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
			sendExecutor.shutdownNow();
		}
	}

	@Scheduled(fixedDelayString = "${app.web-push.scheduler-delay:PT1S}")
	public void processPending() {
		if (!sender.isConfigured()) return;
		recoverInterrupted();
		List<Target> targets = claimBatch();
		if (!targets.isEmpty()) sendConcurrently(targets);
	}

	private List<Target> claimBatch() {
		List<Target> targets = new ArrayList<>();
		for (int index = 0; index < BATCH_SIZE; index++) {
			Optional<Target> claimed = claim();
			if (claimed.isEmpty()) break;
			// 알림이나 구독이 사라진 작업은 claim 단계에서 이미 종료 처리됐다.
			if (claimed.get().notification() != null) targets.add(claimed.get());
		}
		return targets;
	}

	private void sendConcurrently(List<Target> targets) {
		CompletableFuture<?>[] sends = targets.stream()
				.map(target -> CompletableFuture.runAsync(() -> process(target), sendExecutor))
				.toArray(CompletableFuture[]::new);
		try {
			CompletableFuture.allOf(sends).join();
		} catch (CompletionException exception) {
			// 개별 전송 실패는 각자 트랜잭션에서 이미 기록됐다. 여기 오는 것은 예상 밖 예외뿐이다.
			log.warn("Web Push 전송 작업 일부가 예외로 끝났습니다.", exception);
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
			} else if (result == WebPushSendResult.REJECTED) {
				job.fail("Web Push 요청이 거절되어 재시도하지 않습니다.", clock.instant());
			} else if (job.getAttemptCount() >= properties.maxAttempts()) {
				job.fail("Web Push 전송 재시도 횟수를 초과했습니다.", clock.instant());
			} else {
				Instant retryAt = clock.instant().plus(properties.backoffFor(job.getAttemptCount()));
				Instant validUntil = job.getCreatedAt()
						.plus(properties.validityFor(target.notification().getType()));
				if (retryAt.isAfter(validUntil)) {
					job.fail("유효 시간 안에 다시 시도할 수 없어 전송을 중단합니다.", clock.instant());
				} else {
					job.scheduleRetry("Web Push 서비스가 요청을 처리하지 못했습니다.", retryAt);
				}
			}
		}));
		if (result == WebPushSendResult.FAILED || result == WebPushSendResult.REJECTED) {
			log.warn("Web Push 전송 작업이 실패했습니다. jobId={}, attempt={}, result={}",
					target.jobId(), target.attemptCount(), result);
		}
	}

	private record Target(Long jobId, int attemptCount, Notification notification,
			WebPushSubscription subscription) {
	}
}
