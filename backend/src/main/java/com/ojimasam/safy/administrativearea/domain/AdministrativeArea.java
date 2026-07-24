package com.ojimasam.safy.administrativearea.domain;

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

@Entity
@Table(
		name = "administrative_areas",
		indexes = @Index(
				name = "idx_administrative_areas_parent_type_status",
				columnList = "parent_id, area_type, status"
		)
)
public class AdministrativeArea extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private AdministrativeArea parent;

	@Column(nullable = false, unique = true, length = 10)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "area_type", nullable = false, length = 20)
	private AdministrativeAreaType areaType;

	@Column(name = "boundary_geojson", columnDefinition = "text")
	private String boundaryGeoJson;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AdministrativeAreaStatus status;

	protected AdministrativeArea() {
	}

	public Long getId() {
		return id;
	}

	public AdministrativeArea getParent() {
		return parent;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public AdministrativeAreaType getAreaType() {
		return areaType;
	}

	public String getBoundaryGeoJson() {
		return boundaryGeoJson;
	}

	public AdministrativeAreaStatus getStatus() {
		return status;
	}
}
