package com.ojimasam.safy.report.domain;

import java.math.BigDecimal;
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

@Entity
@Table(name = "reports")
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status;

	@Column(name = "reported_at", nullable = false)
	private Instant reportedAt;

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

	private static void validateCommon(Long reporterId, String description, BigDecimal latitude, BigDecimal longitude) {
		if (reporterId == null) throw new IllegalArgumentException("제보자 ID는 필수입니다.");
		if (description == null || description.isBlank()) throw new IllegalArgumentException("제보 내용은 비어 있을 수 없습니다.");
		if (latitude == null || longitude == null) throw new IllegalArgumentException("위도와 경도는 필수입니다.");
		if (latitude.compareTo(new BigDecimal("-90")) < 0 || latitude.compareTo(new BigDecimal("90")) > 0) {
			throw new IllegalArgumentException("위도는 -90 이상 90 이하여야 합니다.");
		}
		if (longitude.compareTo(new BigDecimal("-180")) < 0 || longitude.compareTo(new BigDecimal("180")) > 0) {
			throw new IllegalArgumentException("경도는 -180 이상 180 이하여야 합니다.");
		}
	}

	public void cancel() {
		requireReceived();
		this.status = ReportStatus.CANCELLED;
	}

	private void requireReceived() {
		if (status != ReportStatus.RECEIVED) {
			throw new IllegalStateException("접수 상태의 제보만 변경할 수 있습니다.");
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
}
