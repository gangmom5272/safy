package com.ojimasam.safy.notification.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.notification.domain.type.NotificationPushDeliveryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
		name = "notification_push_delivery_jobs",
		indexes = {
				@Index(
						name = "idx_notification_push_jobs_ready",
						columnList = "status, next_attempt_at"
				),
				@Index(
						name = "idx_notification_push_jobs_recovery",
						columnList = "status, started_at"
				)
		},
		uniqueConstraints = @UniqueConstraint(
				name = "uk_notification_push_jobs_target",
				columnNames = {"notification_id", "subscription_id"}
		)
)
public class NotificationPushDeliveryJob extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "notification_id", nullable = false)
	private Long notificationId;

	@Column(name = "subscription_id", nullable = false)
	private Long subscriptionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationPushDeliveryStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", columnDefinition = "timestamp(6) with time zone")
	private Instant nextAttemptAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@Column(name = "started_at", columnDefinition = "timestamp(6) with time zone")
	private Instant startedAt;

	@Column(name = "completed_at", columnDefinition = "timestamp(6) with time zone")
	private Instant completedAt;

	protected NotificationPushDeliveryJob() {
	}

	private NotificationPushDeliveryJob(Long notificationId, Long subscriptionId, Instant now) {
		this.notificationId = notificationId;
		this.subscriptionId = subscriptionId;
		this.status = NotificationPushDeliveryStatus.PENDING;
		this.nextAttemptAt = now;
	}

	public static NotificationPushDeliveryJob create(Long notificationId, Long subscriptionId, Instant now) {
		if (notificationId == null || subscriptionId == null || now == null) {
			throw new IllegalArgumentException("알림, 구독, 생성 시각은 필수입니다.");
		}
		return new NotificationPushDeliveryJob(notificationId, subscriptionId, now);
	}

	public void start(Instant now) {
		if (status != NotificationPushDeliveryStatus.PENDING
				&& status != NotificationPushDeliveryStatus.RETRY_WAIT) {
			throw new IllegalStateException("대기 중인 전송 작업만 시작할 수 있습니다.");
		}
		status = NotificationPushDeliveryStatus.PROCESSING;
		attemptCount++;
		startedAt = now;
		nextAttemptAt = null;
	}

	public void scheduleRetry(String error, Instant retryAt) {
		requireProcessing();
		status = NotificationPushDeliveryStatus.RETRY_WAIT;
		lastError = error;
		nextAttemptAt = retryAt;
	}

	public void complete(Instant now) {
		requireProcessing();
		status = NotificationPushDeliveryStatus.COMPLETED;
		lastError = null;
		completedAt = now;
	}

	public void fail(String error, Instant now) {
		requireProcessing();
		status = NotificationPushDeliveryStatus.FAILED;
		lastError = error;
		completedAt = now;
	}

	public void recoverInterrupted(Instant retryAt) {
		requireProcessing();
		status = NotificationPushDeliveryStatus.RETRY_WAIT;
		lastError = "백엔드 재시작 또는 작업 시간 초과로 재시도합니다.";
		nextAttemptAt = retryAt;
	}

	private void requireProcessing() {
		if (status != NotificationPushDeliveryStatus.PROCESSING) {
			throw new IllegalStateException("처리 중인 전송 작업만 변경할 수 있습니다.");
		}
	}

	public Long getId() { return id; }
	public Long getNotificationId() { return notificationId; }
	public Long getSubscriptionId() { return subscriptionId; }
	public NotificationPushDeliveryStatus getStatus() { return status; }
	public int getAttemptCount() { return attemptCount; }
	public Instant getStartedAt() { return startedAt; }
}
