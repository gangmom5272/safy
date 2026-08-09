package com.ojimasam.safy.notification.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.notification.domain.type.NotificationType;

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

@Entity
@Table(
		name = "notifications",
		indexes = @Index(name = "idx_notifications_recipient_created", columnList = "recipient_user_id, created_at"),
		uniqueConstraints = @UniqueConstraint(name = "uk_notifications_source_event", columnNames = "source_event_id")
)
public class Notification extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recipient_user_id", nullable = false)
	private Long recipientUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationType type;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "report_id")
	private Long reportId;

	@Column(name = "area_report_request_id")
	private Long areaReportRequestId;

	@Column(name = "source_event_id", nullable = false)
	private Long sourceEventId;

	@Column(name = "read_at", columnDefinition = "timestamp(6) with time zone")
	private Instant readAt;

	protected Notification() {
	}

	private Notification(Long recipientUserId, NotificationType type, String title, String content,
			Long reportId, Long areaReportRequestId, Long sourceEventId) {
		this.recipientUserId = recipientUserId;
		this.type = type;
		this.title = title;
		this.content = content;
		this.reportId = reportId;
		this.areaReportRequestId = areaReportRequestId;
		this.sourceEventId = sourceEventId;
	}

	public static Notification create(Long recipientUserId, NotificationType type, String title, String content,
			Long reportId, Long sourceEventId) {
		return create(recipientUserId, type, title, content, reportId, null, sourceEventId);
	}

	public static Notification create(Long recipientUserId, NotificationType type, String title, String content,
			Long reportId, Long areaReportRequestId, Long sourceEventId) {
		if (recipientUserId == null || type == null || title == null || title.isBlank()
				|| content == null || content.isBlank() || sourceEventId == null) {
			throw new IllegalArgumentException("알림 수신자, 유형, 제목, 내용, 원본 이벤트는 필수입니다.");
		}
		return new Notification(recipientUserId, type, title, content,
				reportId, areaReportRequestId, sourceEventId);
	}

	public void markAsRead(Instant now) {
		if (readAt == null) readAt = now;
	}

	public Long getId() { return id; }
	public Long getRecipientUserId() { return recipientUserId; }
	public NotificationType getType() { return type; }
	public String getTitle() { return title; }
	public String getContent() { return content; }
	public Long getReportId() { return reportId; }
	public Long getAreaReportRequestId() { return areaReportRequestId; }
	public Long getSourceEventId() { return sourceEventId; }
	public Instant getReadAt() { return readAt; }
}
