package com.ojimasam.safy.report.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;

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
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteType;

@Entity
@Table(
		name = "report_center_routes",
		indexes = {
				@Index(name = "idx_report_center_routes_report", columnList = "report_id, status"),
				@Index(name = "idx_report_center_routes_center", columnList = "control_center_id, status")
		}
)
public class ReportCenterRoute extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private Report report;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "previous_route_id")
	private ReportCenterRoute previousRoute;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "control_center_id", nullable = false)
	private ControlCenter controlCenter;

	@Enumerated(EnumType.STRING)
	@Column(name = "route_type", nullable = false, length = 30)
	private ReportCenterRouteType routeType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReportCenterRouteStatus status;

	@Column(nullable = false)
	private int priority;

	@Column(name = "routing_reason", nullable = false, length = 500)
	private String routingReason;

	@Column(name = "routed_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant routedAt;

	@Column(name = "closed_at", columnDefinition = "timestamp(6) with time zone")
	private Instant closedAt;

	protected ReportCenterRoute() {
	}

	private ReportCenterRoute(
			Report report,
			ReportCenterRoute previousRoute,
			ControlCenter controlCenter,
			ReportCenterRouteType routeType,
			int priority,
			String routingReason,
			Instant routedAt
	) {
		this.report = report;
		this.previousRoute = previousRoute;
		this.controlCenter = controlCenter;
		this.routeType = routeType;
		this.status = ReportCenterRouteStatus.ACTIVE;
		this.priority = priority;
		this.routingReason = routingReason;
		this.routedAt = routedAt;
	}

	public static ReportCenterRoute createInitial(
			Report report,
			ControlCenter controlCenter,
			int priority,
			String routingReason,
			Instant routedAt
	) {
		validate(report, controlCenter, priority, routingReason, routedAt);
		return new ReportCenterRoute(
				report,
				null,
				controlCenter,
				ReportCenterRouteType.AUTO,
				priority,
				routingReason.trim(),
				routedAt
		);
	}

	private static void validate(
			Report report,
			ControlCenter controlCenter,
			int priority,
			String routingReason,
			Instant routedAt
	) {
		if (report == null) throw new IllegalArgumentException("제보는 필수입니다.");
		if (controlCenter == null) throw new IllegalArgumentException("관제센터는 필수입니다.");
		if (priority < 1) throw new IllegalArgumentException("라우팅 우선순위는 1 이상이어야 합니다.");
		if (routingReason == null || routingReason.isBlank()) throw new IllegalArgumentException("라우팅 사유는 필수입니다.");
		if (routingReason.length() > 500) throw new IllegalArgumentException("라우팅 사유는 500자 이하여야 합니다.");
		if (routedAt == null) throw new IllegalArgumentException("라우팅 시각은 필수입니다.");
	}

	public void close(Instant closedAt) {
		if (closedAt == null || closedAt.isBefore(routedAt)) {
			throw new IllegalArgumentException("라우팅 종료 시각이 올바르지 않습니다.");
		}
		if (status != ReportCenterRouteStatus.ACTIVE) return;
		this.status = ReportCenterRouteStatus.CLOSED;
		this.closedAt = closedAt;
	}

	public Long getId() {
		return id;
	}

	public Report getReport() {
		return report;
	}

	public ReportCenterRoute getPreviousRoute() {
		return previousRoute;
	}

	public ControlCenter getControlCenter() {
		return controlCenter;
	}

	public ReportCenterRouteType getRouteType() {
		return routeType;
	}

	public ReportCenterRouteStatus getStatus() {
		return status;
	}

	public int getPriority() {
		return priority;
	}

	public String getRoutingReason() {
		return routingReason;
	}

	public Instant getRoutedAt() {
		return routedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}
}
