package com.ojimasam.safy.live.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "live_sessions")
public class LiveSession extends BaseEntity {
	@Id
	private UUID id;

	@Column(name = "report_id", nullable = false, unique = true)
	private Long reportId;

	@Column(name = "reporter_id", nullable = false)
	private Long reporterId;

	@Column(name = "room_name", nullable = false, unique = true, length = 80)
	private String roomName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LiveSessionStatus status;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	@Version
	private long version;

	protected LiveSession() {
	}

	private LiveSession(UUID id, Long reportId, Long reporterId, Instant expiresAt) {
		this.id = id;
		this.reportId = reportId;
		this.reporterId = reporterId;
		this.roomName = "report-" + id;
		this.status = LiveSessionStatus.CREATED;
		this.expiresAt = expiresAt;
	}

	public static LiveSession create(Long reportId, Long reporterId, Instant now, Duration ttl) {
		if (reportId == null) throw new IllegalArgumentException("제보 ID는 필수입니다.");
		if (reporterId == null) throw new IllegalArgumentException("제보자 ID는 필수입니다.");
		if (now == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("세션 유효 시간은 0보다 커야 합니다.");
		}
		UUID id = UUID.randomUUID();
		return new LiveSession(id, reportId, reporterId, now.plus(ttl));
	}

	public void requireOwnedBy(Long userId) {
		if (!reporterId.equals(userId)) throw new LiveSessionAccessDeniedException();
	}

	public void activate(Instant now) {
		requireJoinable(now);
		if (status == LiveSessionStatus.CREATED) {
			status = LiveSessionStatus.ACTIVE;
			startedAt = now;
		}
	}

	public void requireJoinable(Instant now) {
		if (status == LiveSessionStatus.ENDED || status == LiveSessionStatus.FAILED) {
			throw new IllegalStateException("종료된 라이브 세션입니다.");
		}
		if (!now.isBefore(expiresAt)) throw new IllegalStateException("만료된 라이브 세션입니다.");
	}

	public boolean end(Instant now) {
		if (status == LiveSessionStatus.ENDED) return false;
		status = LiveSessionStatus.ENDED;
		endedAt = now;
		return true;
	}

	public void markFailed(String reason, Instant retryAt) {
		if (status == LiveSessionStatus.ENDED) return;
		status = LiveSessionStatus.FAILED;
		expiresAt = retryAt;
		failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
	}

	public void retryCreation(Instant now, Duration ttl) {
		if (status != LiveSessionStatus.FAILED || startedAt != null) {
			throw new IllegalStateException("생성에 실패한 라이브 세션만 재시도할 수 있습니다.");
		}
		if (now == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("세션 유효 시간은 0보다 커야 합니다.");
		}
		status = LiveSessionStatus.CREATED;
		endedAt = null;
		expiresAt = now.plus(ttl);
		failureReason = null;
	}

	public UUID getId() { return id; }
	public Long getReportId() { return reportId; }
	public Long getReporterId() { return reporterId; }
	public String getRoomName() { return roomName; }
	public LiveSessionStatus getStatus() { return status; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getEndedAt() { return endedAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public String getFailureReason() { return failureReason; }
}
