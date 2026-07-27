package com.ojimasam.safy.report.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.user.domain.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "report_messages",
		indexes = @Index(name = "idx_report_messages_report_sent", columnList = "report_id, sent_at, id")
)
public class ReportMessage extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private Report report;

	@Column(name = "sender_user_id", nullable = false)
	private Long senderUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "sender_role", nullable = false, length = 30)
	private UserRole senderRole;

	@Column(nullable = false, length = 1000)
	private String content;

	@Column(name = "sent_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant sentAt;

	protected ReportMessage() {
	}

	private ReportMessage(Report report, Long senderUserId, UserRole senderRole, String content, Instant sentAt) {
		this.report = report;
		this.senderUserId = senderUserId;
		this.senderRole = senderRole;
		this.content = content;
		this.sentAt = sentAt;
	}

	public static ReportMessage create(Report report, Long senderUserId, UserRole senderRole,
			String content, Instant sentAt) {
		if (report == null || senderUserId == null || senderRole == null || sentAt == null) {
			throw new IllegalArgumentException("메시지의 제보, 발신자, 역할, 발신 시각은 필수입니다.");
		}
		if (content == null || content.isBlank()) throw new IllegalArgumentException("메시지는 비어 있을 수 없습니다.");
		String normalized = content.strip();
		if (normalized.length() > 1000) throw new IllegalArgumentException("메시지는 1000자 이하여야 합니다.");
		return new ReportMessage(report, senderUserId, senderRole, normalized, sentAt);
	}

	public Long getId() { return id; }
	public Report getReport() { return report; }
	public Long getSenderUserId() { return senderUserId; }
	public UserRole getSenderRole() { return senderRole; }
	public String getContent() { return content; }
	public Instant getSentAt() { return sentAt; }
}
