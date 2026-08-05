package com.ojimasam.safy.notification.domain;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.notification.domain.type.WebPushSubscriptionStatus;

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
		name = "web_push_subscriptions",
		indexes = @Index(name = "idx_web_push_recipient_status", columnList = "recipient_user_id, status"),
		uniqueConstraints = @UniqueConstraint(name = "uk_web_push_endpoint_hash", columnNames = "endpoint_hash")
)
public class WebPushSubscription extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recipient_user_id", nullable = false)
	private Long recipientUserId;

	@Column(nullable = false, length = 2048)
	private String endpoint;

	@Column(name = "endpoint_hash", nullable = false, length = 64)
	private String endpointHash;

	@Column(nullable = false, length = 255)
	private String p256dh;

	@Column(nullable = false, length = 255)
	private String auth;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private WebPushSubscriptionStatus status;

	protected WebPushSubscription() {
	}

	private WebPushSubscription(Long recipientUserId, String endpoint, String endpointHash, String p256dh,
			String auth, String userAgent) {
		this.recipientUserId = recipientUserId;
		this.endpoint = endpoint;
		this.endpointHash = endpointHash;
		this.p256dh = p256dh;
		this.auth = auth;
		this.userAgent = userAgent;
		this.status = WebPushSubscriptionStatus.ACTIVE;
	}

	public static WebPushSubscription create(Long recipientUserId, String endpoint, String endpointHash,
			String p256dh, String auth, String userAgent) {
		return new WebPushSubscription(recipientUserId, endpoint, endpointHash, p256dh, auth, userAgent);
	}

	public void refresh(Long recipientUserId, String endpoint, String p256dh, String auth, String userAgent) {
		this.recipientUserId = recipientUserId;
		this.endpoint = endpoint;
		this.p256dh = p256dh;
		this.auth = auth;
		this.userAgent = userAgent;
		this.status = WebPushSubscriptionStatus.ACTIVE;
	}

	public void disable() { status = WebPushSubscriptionStatus.DISABLED; }
	public void expire() { status = WebPushSubscriptionStatus.EXPIRED; }

	public Long getId() { return id; }
	public Long getRecipientUserId() { return recipientUserId; }
	public String getEndpoint() { return endpoint; }
	public String getEndpointHash() { return endpointHash; }
	public String getP256dh() { return p256dh; }
	public String getAuth() { return auth; }
	public WebPushSubscriptionStatus getStatus() { return status; }
}
