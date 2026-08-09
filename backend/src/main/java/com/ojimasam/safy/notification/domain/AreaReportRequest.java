package com.ojimasam.safy.notification.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "area_report_requests",
		indexes = @Index(name = "idx_area_report_requests_place_created", columnList = "place_id, created_at")
)
public class AreaReportRequest extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "requester_user_id", nullable = false)
	private Long requesterUserId;

	@Column(name = "place_id", nullable = false)
	private Long placeId;

	@Column(name = "place_name", nullable = false, length = 255)
	private String placeName;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, length = 500)
	private String content;

	@Column(name = "requested_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant requestedAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant expiresAt;

	@Column(name = "recipient_count", nullable = false)
	private int recipientCount;

	protected AreaReportRequest() {
	}

	private AreaReportRequest(Long requesterUserId, Long placeId, String placeName,
			String title, String content, Instant requestedAt, Instant expiresAt) {
		this.requesterUserId = requesterUserId;
		this.placeId = placeId;
		this.placeName = placeName;
		this.title = title;
		this.content = content;
		this.requestedAt = requestedAt;
		this.expiresAt = expiresAt;
	}

	public static AreaReportRequest create(Long requesterUserId, Long placeId, String placeName,
			String title, String content, Instant requestedAt, Instant expiresAt) {
		if (requesterUserId == null || placeId == null || placeName == null || placeName.isBlank()
				|| title == null || title.isBlank() || content == null || content.isBlank()
				|| requestedAt == null || expiresAt == null || !expiresAt.isAfter(requestedAt)) {
			throw new IllegalArgumentException("구역 제보 요청 정보가 올바르지 않습니다.");
		}
		return new AreaReportRequest(requesterUserId, placeId, placeName.strip(), title.strip(),
				content.strip(), requestedAt, expiresAt);
	}

	public void recordRecipients(int recipientCount) {
		if (recipientCount < 0) throw new IllegalArgumentException("수신자 수는 음수일 수 없습니다.");
		this.recipientCount = recipientCount;
	}

	public Long getId() { return id; }
	public Long getPlaceId() { return placeId; }
	public String getPlaceName() { return placeName; }
	public String getTitle() { return title; }
	public String getContent() { return content; }
	public Instant getRequestedAt() { return requestedAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public int getRecipientCount() { return recipientCount; }
}
