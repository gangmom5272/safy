package com.ojimasam.safy.controlcenter.domain;

import java.time.Instant;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.common.persistence.BaseEntity;

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
import jakarta.persistence.UniqueConstraint;
import com.ojimasam.safy.controlcenter.domain.type.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.type.CoverageStatus;

@Entity
@Table(
		name = "control_center_areas",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_control_center_area_role",
				columnNames = {"control_center_id", "administrative_area_id", "coverage_role"}
		),
		indexes = @Index(
				name = "idx_control_center_areas_routing",
				columnList = "administrative_area_id, status, coverage_role, priority"
		)
)
public class ControlCenterArea extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "control_center_id", nullable = false)
	private ControlCenter controlCenter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "administrative_area_id", nullable = false)
	private AdministrativeArea administrativeArea;

	@Enumerated(EnumType.STRING)
	@Column(name = "coverage_role", nullable = false, length = 30)
	private CoverageRole coverageRole;

	@Column(nullable = false)
	private int priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CoverageStatus status;

	@Column(name = "effective_from", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant effectiveFrom;

	@Column(name = "effective_to", columnDefinition = "timestamp(6) with time zone")
	private Instant effectiveTo;

	protected ControlCenterArea() {
	}

	private ControlCenterArea(
			ControlCenter controlCenter,
			AdministrativeArea administrativeArea,
			CoverageRole coverageRole,
			int priority,
			Instant effectiveFrom,
			Instant effectiveTo
	) {
		this.controlCenter = controlCenter;
		this.administrativeArea = administrativeArea;
		this.coverageRole = coverageRole;
		this.priority = priority;
		this.status = CoverageStatus.ACTIVE;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = effectiveTo;
	}

	public static ControlCenterArea assign(
			ControlCenter controlCenter,
			AdministrativeArea administrativeArea,
			CoverageRole coverageRole,
			int priority,
			Instant effectiveFrom,
			Instant effectiveTo
	) {
		validate(controlCenter, administrativeArea, coverageRole, priority, effectiveFrom, effectiveTo);
		return new ControlCenterArea(controlCenter, administrativeArea, coverageRole, priority, effectiveFrom, effectiveTo);
	}

	public void update(
			CoverageRole coverageRole,
			int priority,
			CoverageStatus status,
			Instant effectiveFrom,
			Instant effectiveTo
	) {
		validate(controlCenter, administrativeArea, coverageRole, priority, effectiveFrom, effectiveTo);
		if (status == null) throw new IllegalArgumentException("담당 구역 상태는 필수입니다.");
		this.coverageRole = coverageRole;
		this.priority = priority;
		this.status = status;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = effectiveTo;
	}

	public void deactivate(Instant endedAt) {
		if (endedAt == null) throw new IllegalArgumentException("담당 종료 시각은 필수입니다.");
		this.status = CoverageStatus.INACTIVE;
		this.effectiveTo = endedAt;
	}

	private static void validate(
			ControlCenter controlCenter,
			AdministrativeArea administrativeArea,
			CoverageRole coverageRole,
			int priority,
			Instant effectiveFrom,
			Instant effectiveTo
	) {
		if (controlCenter == null) throw new IllegalArgumentException("관제센터는 필수입니다.");
		if (administrativeArea == null) throw new IllegalArgumentException("행정구역은 필수입니다.");
		if (coverageRole == null) throw new IllegalArgumentException("담당 역할은 필수입니다.");
		if (priority < 1) throw new IllegalArgumentException("라우팅 우선순위는 1 이상이어야 합니다.");
		if (effectiveFrom == null) throw new IllegalArgumentException("담당 시작 시각은 필수입니다.");
		if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
			throw new IllegalArgumentException("담당 종료 시각은 시작 시각 이후여야 합니다.");
		}
	}

	public Long getId() {
		return id;
	}

	public ControlCenter getControlCenter() {
		return controlCenter;
	}

	public AdministrativeArea getAdministrativeArea() {
		return administrativeArea;
	}

	public CoverageRole getCoverageRole() {
		return coverageRole;
	}

	public int getPriority() {
		return priority;
	}

	public CoverageStatus getStatus() {
		return status;
	}

	public Instant getEffectiveFrom() {
		return effectiveFrom;
	}

	public Instant getEffectiveTo() {
		return effectiveTo;
	}
}
