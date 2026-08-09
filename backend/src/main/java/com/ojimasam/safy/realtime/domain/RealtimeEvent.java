package com.ojimasam.safy.realtime.domain;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;

@Entity
@Table(
		name = "realtime_events",
		indexes = @Index(name = "idx_realtime_events_recipient_id", columnList = "recipient_user_id, id")
)
public class RealtimeEvent extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recipient_user_id", nullable = false)
	private Long recipientUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 30)
	private RealtimeEventType eventType;

	@Column(name = "report_id")
	private Long reportId;

	@Column(name = "resource_id", length = 100)
	private String resourceId;

	@Column(name = "message_id")
	private Long messageId;

	protected RealtimeEvent() {
	}

	private RealtimeEvent(Long recipientUserId, RealtimeEventType eventType, Long reportId,
			String resourceId, Long messageId) {
		this.recipientUserId = recipientUserId;
		this.eventType = eventType;
		this.reportId = reportId;
		this.resourceId = resourceId;
		this.messageId = messageId;
	}

	public static RealtimeEvent create(Long recipientUserId, RealtimeEventType eventType, Long reportId,
			String resourceId, Long messageId) {
		if (recipientUserId == null || eventType == null) {
			throw new IllegalArgumentException("실시간 이벤트의 수신자와 유형은 필수입니다.");
		}
		if (eventType == RealtimeEventType.AREA_REPORT_REQUEST) {
			if (resourceId == null || resourceId.isBlank()) {
				throw new IllegalArgumentException("구역 제보 요청 ID는 필수입니다.");
			}
		} else if (reportId == null) {
			throw new IllegalArgumentException("제보 이벤트의 제보 ID는 필수입니다.");
		}
		return new RealtimeEvent(recipientUserId, eventType, reportId, resourceId, messageId);
	}

	public Long getId() { return id; }
	public Long getRecipientUserId() { return recipientUserId; }
	public RealtimeEventType getEventType() { return eventType; }
	public Long getReportId() { return reportId; }
	public String getResourceId() { return resourceId; }
	public Long getMessageId() { return messageId; }
}
