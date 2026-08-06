package com.ojimasam.safy.notification.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ojimasam.safy.notification.domain.NotificationPushDeliveryJob;
import com.ojimasam.safy.notification.domain.type.NotificationPushDeliveryStatus;

@Component
public class NotificationPushDeliveryCleanupWorker {
	private static final Logger log = LoggerFactory.getLogger(NotificationPushDeliveryCleanupWorker.class);
	private static final List<NotificationPushDeliveryStatus> FINISHED = List.of(
			NotificationPushDeliveryStatus.COMPLETED, NotificationPushDeliveryStatus.FAILED);
	// 리포지토리의 findTop500... 조회 건수와 같아야 한다.
	private static final int BATCH_SIZE = 500;
	private static final int MAX_BATCHES_PER_RUN = 100;

	private final JpaNotificationPushDeliveryJobRepository jobs;
	private final WebPushProperties properties;
	private final TransactionTemplate transactions;
	private final Clock clock;

	@Autowired
	public NotificationPushDeliveryCleanupWorker(JpaNotificationPushDeliveryJobRepository jobs,
			WebPushProperties properties, TransactionTemplate transactions) {
		this(jobs, properties, transactions, Clock.systemUTC());
	}

	NotificationPushDeliveryCleanupWorker(JpaNotificationPushDeliveryJobRepository jobs,
			WebPushProperties properties, TransactionTemplate transactions, Clock clock) {
		this.jobs = jobs;
		this.properties = properties;
		this.transactions = transactions;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.web-push.cleanup-delay:PT1H}")
	public void purgeFinishedJobs() {
		Duration retention = properties.completedRetention();
		if (retention.isZero()) return;

		Instant threshold = clock.instant().minus(retention);
		int purged = 0;
		// 한 트랜잭션이 지나치게 커지지 않도록 나눠 지우고, 한 주기에 지우는 총량도 제한한다.
		for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
			int deleted = purgeBatch(threshold);
			purged += deleted;
			if (deleted < BATCH_SIZE) break;
		}
		if (purged > 0) {
			log.info("완료된 Web Push 전송 작업을 정리했습니다. count={}, before={}", purged, threshold);
		}
	}

	private int purgeBatch(Instant threshold) {
		return transactions.execute(status -> {
			List<NotificationPushDeliveryJob> finished = jobs
					.findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(FINISHED, threshold);
			jobs.deleteAllInBatch(finished);
			return finished.size();
		});
	}
}
