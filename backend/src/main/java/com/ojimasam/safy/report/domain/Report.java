package com.ojimasam.safy.report.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "reports",
		indexes = @Index(
				name = "idx_reports_routing_retry",
				columnList = "routing_status, next_routing_at"
		)
)
public class Report extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reporter_id", nullable = false)
	private Long reporterId;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_type", nullable = false, length = 20)
	private ReportType type;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(length = 500)
	private String address;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "administrative_area_id")
	private AdministrativeArea administrativeArea;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status;

	@Column(name = "reported_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant reportedAt;

	@Column(name = "closed_at", columnDefinition = "timestamp(6) with time zone")
	private Instant closedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "routing_status", nullable = false, length = 30)
	private ReportRoutingStatus routingStatus;

	@Column(name = "routing_attempts", nullable = false)
	private int routingAttempts;

	@Column(name = "next_routing_at", columnDefinition = "timestamp(6) with time zone")
	private Instant nextRoutingAt;

	@Column(name = "routing_failure_reason", length = 500)
	private String routingFailureReason;

	protected Report() {
	}

	private Report(
			Long reporterId,
			ReportType type,
			String description,
			BigDecimal latitude,
			BigDecimal longitude,
			String address,
			ReportStatus status,
			Instant reportedAt
	) {
		this.reporterId = reporterId;
		this.type = type;
		this.description = description;
		this.latitude = latitude;
		this.longitude = longitude;
		this.address = address;
		this.status = status;
		this.reportedAt = reportedAt;
		this.routingStatus = ReportRoutingStatus.PENDING;
		this.nextRoutingAt = reportedAt;
	}

	public static Report createText(
			Long reporterId,
			String description,
			BigDecimal latitude,
			BigDecimal longitude,
			String address
	) {
		if (reporterId == null) {
			throw new IllegalArgumentException("제보자 ID는 필수입니다.");
		}
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("텍스트 제보 내용은 비어 있을 수 없습니다.");
		}
		if (latitude == null || longitude == null) {
			throw new IllegalArgumentException("위도와 경도는 필수입니다.");
		}
		if (latitude.compareTo(new BigDecimal("-90")) < 0
				|| latitude.compareTo(new BigDecimal("90")) > 0) {
			throw new IllegalArgumentException("위도는 -90 이상 90 이하여야 합니다.");
		}
		if (longitude.compareTo(new BigDecimal("-180")) < 0
				|| longitude.compareTo(new BigDecimal("180")) > 0) {
			throw new IllegalArgumentException("경도는 -180 이상 180 이하여야 합니다.");
		}

		return new Report(
				reporterId,
				ReportType.TEXT,
				description,
				latitude,
				longitude,
				address,
				ReportStatus.RECEIVED,
				Instant.now()
		);
	}

	public static Report createImage(
			Long reporterId,
			String description,
			BigDecimal latitude,
			BigDecimal longitude,
			String address
	) {
		validateCommon(reporterId, description, latitude, longitude);
		return new Report(
				reporterId,
				ReportType.IMAGE,
				description,
				latitude,
				longitude,
				address,
				ReportStatus.RECEIVED,
				Instant.now()
		);
	}

	public static Report createLive(
			Long reporterId,
			String description,
			BigDecimal latitude,
			BigDecimal longitude,
			String address
	) {
		if (reporterId == null) throw new IllegalArgumentException("제보자 ID는 필수입니다.");
		validateCoordinates(latitude, longitude);
		return new Report(
				reporterId,
				ReportType.LIVE,
				description == null ? "" : description.strip(),
				latitude,
				longitude,
				address,
				ReportStatus.RECEIVED,
				Instant.now()
		);
	}

	private static void validateCommon(Long reporterId, String description, BigDecimal latitude, BigDecimal longitude) {
		if (reporterId == null) throw new IllegalArgumentException("제보자 ID는 필수입니다.");
		if (description == null || description.isBlank()) throw new IllegalArgumentException("제보 내용은 비어 있을 수 없습니다.");
		validateCoordinates(latitude, longitude);
	}

	private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) throw new IllegalArgumentException("위도와 경도는 필수입니다.");
		if (latitude.compareTo(new BigDecimal("-90")) < 0 || latitude.compareTo(new BigDecimal("90")) > 0) {
			throw new IllegalArgumentException("위도는 -90 이상 90 이하여야 합니다.");
		}
		if (longitude.compareTo(new BigDecimal("-180")) < 0 || longitude.compareTo(new BigDecimal("180")) > 0) {
			throw new IllegalArgumentException("경도는 -180 이상 180 이하여야 합니다.");
		}
	}

	public void cancel() {
		if (status != ReportStatus.RECEIVED && status != ReportStatus.ASSIGNED) {
			throw new IllegalStateException("접수 또는 배정 상태의 제보만 취소할 수 있습니다.");
		}
		this.status = ReportStatus.CANCELLED;
		if (routingStatus == ReportRoutingStatus.PENDING) {
			this.routingStatus = ReportRoutingStatus.CANCELLED;
			this.nextRoutingAt = null;
		}
	}

	public void assign() {
		requireStatus(ReportStatus.RECEIVED);
		this.status = ReportStatus.ASSIGNED;
	}

	public void accept() {
		requireStatus(ReportStatus.ASSIGNED);
		this.status = ReportStatus.ACCEPTED;
	}

	public void startWork() {
		requireStatus(ReportStatus.ACCEPTED);
		this.status = ReportStatus.IN_PROGRESS;
	}

	public void completeWork(Instant closedAt) {
		requireStatus(ReportStatus.IN_PROGRESS);
		if (closedAt == null || closedAt.isBefore(reportedAt)) {
			throw new IllegalArgumentException("종료 시각이 올바르지 않습니다.");
		}
		this.status = ReportStatus.CLOSED;
		this.closedAt = closedAt;
	}

	public void completeRouting(AdministrativeArea administrativeArea) {
		if (administrativeArea == null) throw new IllegalArgumentException("행정구역은 필수입니다.");
		if (routingStatus != ReportRoutingStatus.PENDING) return;
		this.administrativeArea = administrativeArea;
		this.routingStatus = ReportRoutingStatus.ROUTED;
		this.nextRoutingAt = null;
		this.routingFailureReason = null;
	}

	public void scheduleRoutingRetry(String reason, Instant nextRoutingAt, int maxAttempts) {
		if (routingStatus != ReportRoutingStatus.PENDING) return;
		if (nextRoutingAt == null) throw new IllegalArgumentException("다음 라우팅 시각은 필수입니다.");
		if (maxAttempts < 1) throw new IllegalArgumentException("최대 라우팅 시도 횟수는 1 이상이어야 합니다.");
		this.routingAttempts++;
		this.routingFailureReason = truncateReason(reason);
		if (routingAttempts >= maxAttempts) {
			this.routingStatus = ReportRoutingStatus.MANUAL_REQUIRED;
			this.nextRoutingAt = null;
			return;
		}
		this.nextRoutingAt = nextRoutingAt;
	}

	public void requireManualRouting(String reason) {
		if (routingStatus != ReportRoutingStatus.PENDING) return;
		this.routingStatus = ReportRoutingStatus.MANUAL_REQUIRED;
		this.nextRoutingAt = null;
		this.routingFailureReason = truncateReason(reason);
	}

	private String truncateReason(String reason) {
		if (reason == null || reason.isBlank()) return null;
		String trimmed = reason.trim();
		return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
	}

	private void requireStatus(ReportStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("현재 제보 상태에서는 요청을 처리할 수 없습니다.");
		}
	}

	public Long getId() {
		return id;
	}

	public Long getReporterId() {
		return reporterId;
	}

	public ReportType getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public String getAddress() {
		return address;
	}

	public ReportStatus getStatus() {
		return status;
	}

	public Instant getReportedAt() {
		return reportedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public AdministrativeArea getAdministrativeArea() {
		return administrativeArea;
	}

	public ReportRoutingStatus getRoutingStatus() {
		return routingStatus;
	}

	public int getRoutingAttempts() {
		return routingAttempts;
	}

	public Instant getNextRoutingAt() {
		return nextRoutingAt;
	}

	public String getRoutingFailureReason() {
		return routingFailureReason;
	}
}
