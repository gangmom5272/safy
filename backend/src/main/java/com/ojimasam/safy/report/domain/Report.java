package com.ojimasam.safy.report.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.media.domain.MediaAsset;

import org.hibernate.annotations.ColumnDefault;
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
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

@Entity
@Table(
		name = "reports",
		indexes = {
				@Index(
						name = "idx_reports_routing_retry",
						columnList = "routing_status, next_routing_at"
				),
				@Index(
						name = "idx_reports_public_feed",
						columnList = "public_visibility, reported_at, id"
				),
				@Index(
						name = "idx_reports_grid_cell_code",
						columnList = "grid_cell_code"
				)
		}
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

	/**
	 * 좌표가 속한 100m 격자 셀 코드. 셀 단위 집계·필터에 쓴다.
	 *
	 * <p>좌표에서 도출되는 값이라 생성 시점에 함께 정한다. 나중에 채우는 방식으로 두면
	 * 좌표와 셀이 어긋난 행이 생길 수 있다.
	 *
	 * <p>좌표를 이 컬럼으로 대체하지 않는다. 셀 코드만 남기면 셀 크기를 100m 에서
	 * 바꿀 때 백필할 근거가 사라진다.
	 *
	 * <p>기존 행 때문에 nullable 이다. {@code ddl-auto: update} 는 값이 있는 테이블에
	 * NOT NULL 컬럼을 추가하지 못한다. 백필은 db/seed/report-grid-cell-code.sql 에 있다.
	 */
	@Column(name = "grid_cell_code", length = 24)
	private String gridCellCode;

	/**
	 * 좌표를 포함하는 서울 121장소. 좌표가 어느 장소에도 들지 않으면 NULL 이다.
	 *
	 * <p>격자 셀 코드와 달리 장소는 폴리곤 조회가 필요해 생성자에서 정할 수 없다.
	 * 등록 흐름에서 판정한 뒤 채운다.
	 *
	 * <p>참조를 엔티티로 잡지 않고 ID 만 둔다. 제보를 읽을 때 장소까지 함께 끌고 올
	 * 이유가 없고, {@code reporterId} 등 다른 집계 간 참조도 같은 방식이다.
	 */
	@Column(name = "place_id")
	private Long placeId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "administrative_area_id")
	private AdministrativeArea administrativeArea;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "public_visibility", nullable = false, length = 20)
	@ColumnDefault("'PENDING'")
	private ReportPublicVisibility publicVisibility;

	/** 시민에게 내보내는 유일한 이미지. 원본이 아니라 블러 파생본만 여기에 들어간다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "representative_blur_asset_id")
	private MediaAsset representativeBlurAsset;

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
		this.gridCellCode = GridCellCode.of(latitude, longitude).value();
		this.address = address;
		this.status = status;
		this.reportedAt = reportedAt;
		this.publicVisibility = ReportPublicVisibility.PENDING;
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

	/**
	 * 제보자가 취소할 수 있는 상태인지. 담당자가 확인(IN_PROGRESS)하기 전까지만 허용한다.
	 * 취소 버튼 노출 조건이 프론트에 중복되지 않도록 조회 응답(cancellable)에도 그대로 내려준다.
	 */
	public boolean isCancellable() {
		return status == ReportStatus.RECEIVED || status == ReportStatus.ASSIGNED;
	}

	public void cancel() {
		if (!isCancellable()) {
			throw new IllegalStateException("접수 또는 배정 상태의 제보만 취소할 수 있습니다.");
		}
		this.status = ReportStatus.CANCELLED;
		withhold();
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
		publish();
	}

	/**
	 * 담당자가 무효 제보로 반려. 종결(terminal) 상태이며 CLOSED와 구분한다.
	 */
	public void reject(Instant closedAt) {
		requireHandling();
		this.status = ReportStatus.REJECTED;
		this.closedAt = requireClosedAt(closedAt);
		withhold();
	}

	/**
	 * 담당자가 업무를 종료(채택)하며 시민 피드에 공개한다.
	 * 이미 내려진(WITHHELD) 제보는 다시 올리지 않는다 — 내림이 나중 판단이므로 우선한다.
	 * 내려진 제보 때문에 업무 종료 자체가 막히면 안 되므로 예외 대신 조용히 반환한다.
	 */
	public void publish() {
		if (publicVisibility == ReportPublicVisibility.WITHHELD) return;
		this.publicVisibility = ReportPublicVisibility.PUBLISHED;
	}

	/**
	 * 시민 피드에서 내린다. 취소·반려처럼 공개 전(PENDING)에서도 호출되고,
	 * 공개 후 담당자가 직접 내리는 경로도 있어 상태를 따지지 않는다. 멱등.
	 */
	public void withhold() {
		this.publicVisibility = ReportPublicVisibility.WITHHELD;
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

	/** 좌표를 포함하는 장소를 채운다. 어느 장소에도 들지 않으면 호출하지 않는다. */
	public void applyResolvedPlace(Long resolvedPlaceId) {
		if (resolvedPlaceId == null) return;
		this.placeId = resolvedPlaceId;
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

	public String getGridCellCode() {
		return gridCellCode;
	}

	public Long getPlaceId() {
		return placeId;
	}

	public ReportStatus getStatus() {
		return status;
	}

	public ReportPublicVisibility getPublicVisibility() {
		return publicVisibility;
	}

	public MediaAsset getRepresentativeBlurAsset() {
		return representativeBlurAsset;
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
