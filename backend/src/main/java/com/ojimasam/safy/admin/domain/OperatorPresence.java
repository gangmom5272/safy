package com.ojimasam.safy.admin.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;

/**
 * 담당자의 근무 상태. 자동 배정은 근무중(ON_DUTY)인 담당자에게만 이루어진다.
 * 접속(SSE 연결)·수동 토글이 상태를 올리고, 연결이 끊긴 채 유예가 지나면
 * 스위퍼가 오프라인으로 내리며 들고 있던 배정을 회수한다.
 */
@Entity
@Table(
		name = "operator_presences",
		uniqueConstraints = @UniqueConstraint(name = "uk_operator_presences_operator", columnNames = "operator_id")
)
public class OperatorPresence extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "operator_id", nullable = false)
	private Long operatorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OperatorPresenceStatus status;

	@Column(name = "last_seen_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant lastSeenAt;

	protected OperatorPresence() {
	}

	private OperatorPresence(Long operatorId, OperatorPresenceStatus status, Instant lastSeenAt) {
		if (operatorId == null || status == null || lastSeenAt == null) {
			throw new IllegalArgumentException("담당자, 근무 상태, 최근 접속 시각은 필수입니다.");
		}
		this.operatorId = operatorId;
		this.status = status;
		this.lastSeenAt = lastSeenAt;
	}

	public static OperatorPresence connect(Long operatorId, Instant now) {
		return new OperatorPresence(operatorId, OperatorPresenceStatus.ON_DUTY, now);
	}

	/** 접속 신호. 오프라인이었다면 근무중으로 복귀하고, 수동 자리비움은 그대로 유지한다. */
	public void markConnected(Instant now) {
		touch(now);
		if (status == OperatorPresenceStatus.OFFLINE) {
			this.status = OperatorPresenceStatus.ON_DUTY;
		}
	}

	public void touch(Instant now) {
		if (now == null) throw new IllegalArgumentException("최근 접속 시각은 필수입니다.");
		this.lastSeenAt = now;
	}

	public void change(OperatorPresenceStatus status, Instant now) {
		if (status == null) throw new IllegalArgumentException("근무 상태는 필수입니다.");
		touch(now);
		this.status = status;
	}

	public boolean isOnDuty() {
		return status == OperatorPresenceStatus.ON_DUTY;
	}

	public Long getId() {
		return id;
	}

	public Long getOperatorId() {
		return operatorId;
	}

	public OperatorPresenceStatus getStatus() {
		return status;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}
}
