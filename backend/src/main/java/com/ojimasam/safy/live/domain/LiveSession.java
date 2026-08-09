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
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;

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

	@Column(name = "disconnected_at")
	private Instant disconnectedAt;

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
		} else if (status == LiveSessionStatus.DISCONNECTED) {
			status = LiveSessionStatus.ACTIVE;
			disconnectedAt = null;
		}
	}

	/**
	 * 미디어 서버가 제보자 이탈을 통지했을 때, 즉시 종료하지 않고 재접속 유예 상태로 전환한다.
	 * 일시적인 네트워크 단절로 세션이 죽는 것을 막는다.
	 *
	 * @return 이번 호출로 상태가 바뀌었으면 true
	 */
	public boolean markDisconnected(Instant now) {
		if (status != LiveSessionStatus.ACTIVE) return false;
		status = LiveSessionStatus.DISCONNECTED;
		disconnectedAt = now;
		return true;
	}

	/**
	 * 제보자가 유예 시간 안에 재접속했다.
	 *
	 * @return 이번 호출로 다시 송출 상태가 됐으면 true
	 */
	public boolean markReconnected(Instant now) {
		if (status != LiveSessionStatus.DISCONNECTED) return false;
		status = LiveSessionStatus.ACTIVE;
		disconnectedAt = null;
		if (startedAt == null) startedAt = now;
		return true;
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
	public Instant getDisconnectedAt() { return disconnectedAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public String getFailureReason() { return failureReason; }
}
