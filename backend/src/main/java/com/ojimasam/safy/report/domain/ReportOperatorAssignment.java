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
import com.ojimasam.safy.report.domain.type.ReportAssignmentCancelReason;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

@Entity
@Table(
		name = "report_operator_assignments",
		indexes = {
				@Index(name = "idx_report_assignments_report_status", columnList = "report_id, status"),
				@Index(name = "idx_report_assignments_operator_status", columnList = "operator_id, status")
		}
)
public class ReportOperatorAssignment extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private Report report;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_center_route_id", nullable = false)
	private ReportCenterRoute route;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "operator_id", nullable = false)
	private User operator;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_by")
	private User assignedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReportOperatorAssignmentStatus status;

	@Column(name = "assigned_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant assignedAt;

	@Column(name = "accepted_at", columnDefinition = "timestamp(6) with time zone")
	private Instant acceptedAt;

	@Column(name = "started_at", columnDefinition = "timestamp(6) with time zone")
	private Instant startedAt;

	@Column(name = "completed_at", columnDefinition = "timestamp(6) with time zone")
	private Instant completedAt;

	@Column(name = "cancelled_at", columnDefinition = "timestamp(6) with time zone")
	private Instant cancelledAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancel_reason", length = 30)
	private ReportAssignmentCancelReason cancelReason;

	protected ReportOperatorAssignment() {
	}

	private ReportOperatorAssignment(Report report, ReportCenterRoute route, User operator, User assignedBy,
			Instant assignedAt) {
		if (report == null || route == null || operator == null || assignedAt == null) {
			throw new IllegalArgumentException("제보, 라우팅, 담당자, 배정 시각은 필수입니다.");
		}
		this.report = report;
		this.route = route;
		this.operator = operator;
		this.assignedBy = assignedBy;
		this.status = ReportOperatorAssignmentStatus.ASSIGNED;
		this.assignedAt = assignedAt;
	}

	public static ReportOperatorAssignment automatic(
			Report report,
			ReportCenterRoute route,
			User operator,
			Instant assignedAt
	) {
		return new ReportOperatorAssignment(report, route, operator, null, assignedAt);
	}

	/** 담당자가 공용 큐에서 직접 담당(claim)한 배정. 본인이 배정자다. */
	public static ReportOperatorAssignment claimed(
			Report report,
			ReportCenterRoute route,
			User operator,
			Instant assignedAt
	) {
		return new ReportOperatorAssignment(report, route, operator, operator, assignedAt);
	}

	/**
	 * 담당자가 제보를 확인한 시점의 자동 전이(수락+착수 통합). 멱등.
	 */
	public void beginHandling(Long operatorId, Instant now) {
		requireOperator(operatorId);
		if (status == ReportOperatorAssignmentStatus.IN_PROGRESS) return;
		requireStatus(ReportOperatorAssignmentStatus.ASSIGNED);
		Instant time = requireTime(now);
		this.status = ReportOperatorAssignmentStatus.IN_PROGRESS;
		this.acceptedAt = time;
		this.startedAt = time;
	}

	public void complete(Long operatorId, Instant completedAt) {
		requireOperator(operatorId);
		if (status != ReportOperatorAssignmentStatus.ASSIGNED
				&& status != ReportOperatorAssignmentStatus.IN_PROGRESS) {
			throw new IllegalStateException("현재 배정 상태에서는 요청을 처리할 수 없습니다.");
		}
		Instant time = requireTime(completedAt);
		if (this.startedAt == null) this.startedAt = time;
		this.status = ReportOperatorAssignmentStatus.COMPLETED;
		this.completedAt = time;
	}

	public void cancel(Instant cancelledAt, ReportAssignmentCancelReason cancelReason) {
		if (cancelReason == null) throw new IllegalArgumentException("배정 취소 사유는 필수입니다.");
		if (status == ReportOperatorAssignmentStatus.COMPLETED
				|| status == ReportOperatorAssignmentStatus.CANCELLED) return;
		this.status = ReportOperatorAssignmentStatus.CANCELLED;
		this.cancelledAt = requireTime(cancelledAt);
		this.cancelReason = cancelReason;
	}

	private void requireOperator(Long operatorId) {
		if (operatorId == null || !operator.getId().equals(operatorId)) {
			throw new IllegalStateException("배정받은 담당자만 처리할 수 있습니다.");
		}
	}

	private void requireStatus(ReportOperatorAssignmentStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("현재 배정 상태에서는 요청을 처리할 수 없습니다.");
		}
	}

	private Instant requireTime(Instant value) {
		if (value == null || value.isBefore(assignedAt)) {
			throw new IllegalArgumentException("처리 시각이 올바르지 않습니다.");
		}
		return value;
	}

	public Long getId() { return id; }
	public Report getReport() { return report; }
	public ReportCenterRoute getRoute() { return route; }
	public User getOperator() { return operator; }
	public User getAssignedBy() { return assignedBy; }
	public ReportOperatorAssignmentStatus getStatus() { return status; }
	public Instant getAssignedAt() { return assignedAt; }
	public Instant getAcceptedAt() { return acceptedAt; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public Instant getCancelledAt() { return cancelledAt; }
	public ReportAssignmentCancelReason getCancelReason() { return cancelReason; }
}
