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
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReportCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private ReportSubcategory subcategory;

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
			ReportCategory category,
			ReportSubcategory subcategory,
			String description,
			BigDecimal latitude,
			BigDecimal longitude,
			String address,
			ReportStatus status,
			Instant reportedAt
	) {
		this.reporterId = reporterId;
		this.type = type;
		this.category = category;
		this.subcategory = subcategory;
		this.description = description;
		this.latitude = latitude;
		this.longitude = longitude;
		this.address = address;
		this.status = status;
		this.reportedAt = reportedAt;
		this.routingStatus = ReportRoutingStatus.PENDING;
		this.nextRoutingAt = reportedAt;
	}

	public static Report create(
			Long reporterId,
			ReportType type,
			ReportCategory category,
			ReportSubcategory subcategory,
			String description,
			BigDecimal latitude,
			BigDecimal longitude,
			String address
	) {
		validateCommon(reporterId, description, latitude, longitude);
		if (type == null) throw new IllegalArgumentException("제보 유형은 필수입니다.");
		validateCategory(category, subcategory);
		return new Report(
				reporterId,
				type,
				category,
				subcategory,
				description.strip(),
				latitude,
				longitude,
				address,
				ReportStatus.RECEIVED,
				Instant.now()
		);
	}

	private static void validateCategory(ReportCategory category, ReportSubcategory subcategory) {
		if (category == null) throw new IllegalArgumentException("제보 카테고리는 필수입니다.");
		if (subcategory == null) throw new IllegalArgumentException("제보 세부 상황은 필수입니다.");
		if (!subcategory.belongsTo(category)) {
			throw new IllegalArgumentException("제보 카테고리와 세부 상황이 일치하지 않습니다.");
		}
	}

	private static void validateCommon(Long reporterId, String description, BigDecimal latitude, BigDecimal longitude) {
		if (reporterId == null) throw new IllegalArgumentException("제보자 ID는 필수입니다.");
		if (description == null || description.isBlank()) throw new IllegalArgumentException("제보 내용은 비어 있을 수 없습니다.");
		validateCoordinates(latitude, longitude);
	}

	public void requireMediaCreation(Long userId, ReportType expectedType) {
		if (!reporterId.equals(userId)) throw new IllegalArgumentException("본인의 제보만 미디어를 연결할 수 있습니다.");
		if (type != expectedType) throw new IllegalStateException("제보 유형에 맞지 않는 미디어 요청입니다.");
		if (status == ReportStatus.CANCELLED || status == ReportStatus.CLOSED || status == ReportStatus.REJECTED) {
			throw new IllegalStateException("종료된 제보에는 미디어를 연결할 수 없습니다.");
		}
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

	/**
	 * 담당자가 제보를 실제로 확인(타일 열람·라이브 시청)한 시점의 자동 전이.
	 * push 기반 구조에서는 별도 수동 수락 없이 확인 즉시 처리중으로 간주한다. 멱등.
	 */
	public void beginHandling() {
		if (status == ReportStatus.IN_PROGRESS) return;
		requireStatus(ReportStatus.ASSIGNED);
		this.status = ReportStatus.IN_PROGRESS;
	}

	public void completeWork(Instant closedAt) {
		requireHandling();
		this.status = ReportStatus.CLOSED;
		this.closedAt = requireClosedAt(closedAt);
	}

	/**
	 * 담당자가 무효 제보로 반려. 종결(terminal) 상태이며 CLOSED와 구분한다.
	 */
	public void reject(Instant closedAt) {
		requireHandling();
		this.status = ReportStatus.REJECTED;
		this.closedAt = requireClosedAt(closedAt);
	}

	/**
	 * 담당자가 처리를 놓아 다른 담당자에게 재배정되도록 접수 상태로 되돌린다.
	 * routingStatus(ROUTED)는 유지되어 미배정 재시도 스케줄러가 다시 배정한다.
	 */
	public void releaseForReassignment() {
		requireHandling();
		this.status = ReportStatus.RECEIVED;
	}

	private Instant requireClosedAt(Instant closedAt) {
		if (closedAt == null || closedAt.isBefore(reportedAt)) {
			throw new IllegalArgumentException("종료 시각이 올바르지 않습니다.");
		}
		return closedAt;
	}

	private void requireHandling() {
		if (status != ReportStatus.ASSIGNED && status != ReportStatus.IN_PROGRESS) {
			throw new IllegalStateException("배정 또는 처리중 상태의 제보만 처리할 수 있습니다.");
		}
	}

	/**
	 * 좌표로 확정한 상세 주소를 반영한다. 라우팅 성공 여부와 무관하게 남길 수 있어야 하므로
	 * routingStatus 를 따지지 않는다. 빈 값으로 기존 주소를 지우지는 않는다.
	 */
	public void applyResolvedAddress(String resolvedAddress) {
		if (resolvedAddress == null || resolvedAddress.isBlank()) return;
		this.address = resolvedAddress;
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

	public ReportCategory getCategory() {
		return category;
	}

	public ReportSubcategory getSubcategory() {
		return subcategory;
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
