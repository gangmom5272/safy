package com.ojimasam.safy.grid.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 서울시 주요 지역 121곳이 덮는 격자 셀. 장소 하나가 여러 셀을 덮고,
 * 셀 하나에 여러 장소가 걸칠 수 있다.
 *
 * <p>격자 위험도는 제보가 없어도 공공데이터만으로 계산되어야 한다. 그래서 좌표 하나로
 * 장소를 찾는 {@code PlaceLocator} 의 반대 방향 — 장소가 덮는 셀 전부 — 이 필요하고,
 * 제보 등록 시점의 find-or-create 로도 채워지지 않는다.
 *
 * <p>장소 경계는 바뀌지 않으므로 시드 시점에 한 번 계산해 두고 재사용한다.
 * 그래서 {@code updatedAt} 을 두지 않고 {@code createdAt} 만 기록한다.
 */
@Entity
@Table(
		name = "place_grid_mappings",
		uniqueConstraints = @UniqueConstraint(
				// 시드를 다시 실행해도 행이 늘지 않는다.
				name = "uk_place_grid_mappings_place_cell",
				columnNames = {"place_id", "grid_cell_id"}
		),
		// 위험도 계산은 셀에서 장소를 찾는 방향으로 읽는다.
		// place_id 방향은 위 UNIQUE 제약의 선두 컬럼이 인덱스 역할을 한다.
		indexes = @Index(name = "idx_place_grid_mappings_grid_cell_id", columnList = "grid_cell_id")
)
@EntityListeners(AuditingEntityListener.class)
public class PlaceGridMapping {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "place_id", nullable = false, updatable = false)
	private Long placeId;

	@Column(name = "grid_cell_id", nullable = false, updatable = false)
	private Long gridCellId;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant createdAt;

	protected PlaceGridMapping() {
	}

	private PlaceGridMapping(Long placeId, Long gridCellId) {
		this.placeId = placeId;
		this.gridCellId = gridCellId;
	}

	public static PlaceGridMapping of(Long placeId, Long gridCellId) {
		if (placeId == null) {
			throw new IllegalArgumentException("장소 식별자는 필수입니다.");
		}
		if (gridCellId == null) {
			throw new IllegalArgumentException("격자 셀 식별자는 필수입니다.");
		}

		return new PlaceGridMapping(placeId, gridCellId);
	}

	public Long getId() {
		return id;
	}

	public Long getPlaceId() {
		return placeId;
	}

	public Long getGridCellId() {
		return gridCellId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}