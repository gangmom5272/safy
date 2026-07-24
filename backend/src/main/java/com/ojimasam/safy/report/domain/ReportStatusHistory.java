package com.ojimasam.safy.report.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.user.domain.User;

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
		name = "report_status_histories",
		indexes = @Index(name = "idx_report_status_histories_report_time", columnList = "report_id, changed_at")
)
public class ReportStatusHistory extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private Report report;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 30)
	private ReportStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 30)
	private ReportStatus toStatus;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private User changedBy;

	@Column(length = 500)
	private String reason;

	@Column(name = "changed_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant changedAt;

	protected ReportStatusHistory() {
	}

	private ReportStatusHistory(Report report, ReportStatus fromStatus, ReportStatus toStatus, User changedBy,
			String reason, Instant changedAt) {
		if (report == null || toStatus == null || changedAt == null) {
			throw new IllegalArgumentException("제보, 변경 상태, 변경 시각은 필수입니다.");
		}
		this.report = report;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.changedBy = changedBy;
		String trimmedReason = reason == null ? null : reason.trim();
		this.reason = trimmedReason == null || trimmedReason.isBlank() ? null
				: trimmedReason.substring(0, Math.min(trimmedReason.length(), 500));
		this.changedAt = changedAt;
	}

	public static ReportStatusHistory record(Report report, ReportStatus fromStatus, ReportStatus toStatus,
			User changedBy, String reason, Instant changedAt) {
		return new ReportStatusHistory(report, fromStatus, toStatus, changedBy, reason, changedAt);
	}

	public Long getId() { return id; }
	public Report getReport() { return report; }
	public ReportStatus getFromStatus() { return fromStatus; }
	public ReportStatus getToStatus() { return toStatus; }
	public User getChangedBy() { return changedBy; }
	public String getReason() { return reason; }
	public Instant getChangedAt() { return changedAt; }
}
