package com.ojimasam.safy.grid.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 100m 격자 셀.
 *
 * <p>행은 서울 전역을 미리 채우지 않고, 값이 생기는 셀만 만든다. 셀이 속할 자리는
 * {@link GridCellCode} 산식으로 이미 정해져 있으므로 행이 없는 셀은
 * "존재하지 않는 셀"이 아니라 "데이터가 없는 셀"이다. 서울 전역을 미리 만들면
 * 60,520행(실제 면적)~124,992행(bbox)이고 그 중 대부분이 영구히 빈 행이 되며,
 * 셀 크기를 바꿀 때 전체를 재생성해야 한다.
 *
 * <p>행 내용은 모두 {@code gridCode} 에서 도출되므로 만든 뒤 바뀌지 않는다.
 * 그래서 {@code updatedAt} 을 두지 않고 {@code createdAt} 만 기록한다.
 *
 * <p>셀 경계는 {@code rowIndex}/{@code columnIndex} 에서 계산되는 파생값이라
 * 저장하지 않는다.
 */
@Entity
@Table(
		name = "grid_cells",
		uniqueConstraints = {
				// 동시에 들어온 제보가 같은 셀을 중복 생성하는 것을 막는다.
				// GridCellRegistry 의 find-or-create 가 이 제약에 의존한다.
				@UniqueConstraint(name = "uk_grid_cells_grid_code", columnNames = "grid_code"),
				@UniqueConstraint(
						name = "uk_grid_cells_row_column",
						columnNames = {"row_index", "column_index"}
				)
		}
)
@EntityListeners(AuditingEntityListener.class)
public class GridCell {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "grid_code", nullable = false, updatable = false, length = 24)
	private String gridCode;

	@Column(name = "row_index", nullable = false, updatable = false)
	private int rowIndex;

	@Column(name = "column_index", nullable = false, updatable = false)
	private int columnIndex;

	@Column(name = "center_latitude", nullable = false, updatable = false, precision = 10, scale = 7)
	private BigDecimal centerLatitude;

	@Column(name = "center_longitude", nullable = false, updatable = false, precision = 10, scale = 7)
	private BigDecimal centerLongitude;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant createdAt;

	protected GridCell() {
	}

	private GridCell(GridCellCode code) {
		this.gridCode = code.value();
		this.rowIndex = code.row();
		this.columnIndex = code.column();
		this.centerLatitude = code.centerLatitude();
		this.centerLongitude = code.centerLongitude();
	}

	public static GridCell of(GridCellCode code) {
		if (code == null) {
			throw new IllegalArgumentException("격자 셀 코드는 필수입니다.");
		}

		return new GridCell(code);
	}

	public Long getId() {
		return id;
	}

	public String getGridCode() {
		return gridCode;
	}

	public int getRowIndex() {
		return rowIndex;
	}

	public int getColumnIndex() {
		return columnIndex;
	}

	public BigDecimal getCenterLatitude() {
		return centerLatitude;
	}

	public BigDecimal getCenterLongitude() {
		return centerLongitude;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/** 저장된 행에서 좌표계 값 객체를 복원한다. */
	public GridCellCode toCode() {
		return new GridCellCode(rowIndex, columnIndex);
	}
}
