package com.ojimasam.safy.grid.domain;

import java.time.Instant;

import com.ojimasam.safy.analysis.domain.risk.GridObservationStatus;
import com.ojimasam.safy.analysis.domain.risk.GridRiskSnapshot;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 격자 위험도 이력 한 행({@code grid_risk_snapshots}).
 *
 * <p>계산 결과인 {@link GridRiskSnapshot} 을 시간 창과 함께 저장한 것이다. 이름이
 * 다른 이유는 그 record 가 계산 산출물이고 이쪽은 저장 표현이기 때문이다
 * ({@code RiskCalculation} → {@code RiskAssessment} 와 같은 구분).
 *
 * <p>근거가 처음부터 없는 격자는 저장하지 않는다. 서울 전체를 격자로 나누면 대부분이
 * 그런 칸이라 저장량만 늘고, 조회 시 행이 없으면 NO_DATA 로 간주하면 된다.
 *
 * <p>다만 <b>위험했던 칸의 근거가 사라진 경우에는 {@code NO_DATA} 이력을 남긴다.</b>
 * 그러지 않으면 이력이 갱신되지 않아 마지막 위험도가 지도에 영구히 남는다. 그때
 * {@code score} 와 {@code congestionLevel} 은 null 이다 — 0 으로 채우면 very_low
 * (안전함)로 읽혀 데이터 없음과 구분되지 않는다.
 */
@Entity
@Table(
		name = "grid_risk_snapshots",
		uniqueConstraints = @UniqueConstraint(
				// 같은 창을 두 번 계산해도 행이 늘지 않는다.
				name = "uk_grid_risk_snapshots_cell_window",
				columnNames = {"grid_cell_id", "window_ended_at"}
		),
		// 셀의 최신 값과 추이를 모두 이 순서로 읽는다.
		indexes = @Index(
				name = "idx_grid_risk_snapshots_cell_window",
				columnList = "grid_cell_id, window_ended_at"
		)
)
public class GridRiskHistory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "grid_cell_id", nullable = false, updatable = false)
	private Long gridCellId;

	// RiskAssessment.finalRiskScore 와 같이 부동소수점으로 둔다. ERD 는 NUMERIC(5,2) 지만
	// 제보 단위 점수가 이미 double precision 이라 두 층의 점수 타입을 맞춘다.
	// NO_DATA(위험 종료) 이력에서는 null 이다.
	@Column
	private Double score;

	@Enumerated(EnumType.STRING)
	@Column(name = "congestion_level", length = 30)
	private RiskLevel congestionLevel;

	@Enumerated(EnumType.STRING)
	@Column(name = "observation_status", nullable = false, length = 30)
	private GridObservationStatus observationStatus;

	@Column(name = "evidence_count", nullable = false)
	private int evidenceCount;

	@Column(name = "distinct_reporter_count", nullable = false)
	private int distinctReporterCount;

	@Column(name = "confidence_score", nullable = false)
	private double confidenceScore;

	@Column(name = "window_started_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant windowStartedAt;

	@Column(name = "window_ended_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant windowEndedAt;

	@Column(name = "computed_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant computedAt;

	@Column(name = "calculation_version", nullable = false, length = 50)
	private String calculationVersion;

	protected GridRiskHistory() {
	}

	private GridRiskHistory(GridRiskSnapshot snapshot, Instant windowStartedAt, Instant windowEndedAt) {
		this.gridCellId = snapshot.gridCellId();
		this.score = snapshot.score();
		this.congestionLevel = snapshot.congestionLevel();
		this.observationStatus = snapshot.observationStatus();
		this.evidenceCount = snapshot.evidenceCount();
		this.distinctReporterCount = snapshot.distinctReporterCount();
		this.confidenceScore = snapshot.confidenceScore();
		this.windowStartedAt = windowStartedAt;
		this.windowEndedAt = windowEndedAt;
		this.computedAt = windowEndedAt;
		this.calculationVersion = snapshot.calculationVersion();
	}

	public static GridRiskHistory of(
			GridRiskSnapshot snapshot, Instant windowStartedAt, Instant windowEndedAt) {
		if (windowStartedAt == null || windowEndedAt == null) {
			throw new IllegalArgumentException("시간 창은 필수입니다.");
		}

		return new GridRiskHistory(snapshot, windowStartedAt, windowEndedAt);
	}

	/** 이 이력이 지도에 그릴 값인지. {@code NO_DATA} 는 위험이 끝났다는 기록이라 그리지 않는다. */
	public boolean isActive() {
		return observationStatus != GridObservationStatus.NO_DATA;
	}

	/**
	 * 같은 창을 다시 계산했을 때 값을 갱신한다. 창 도중에 제보가 더 들어오면 결과가
	 * 달라지므로 마지막 계산이 이긴다. 격자와 시간 창은 바꾸지 않는다.
	 */
	public void refresh(GridRiskSnapshot snapshot, Instant computedAt) {
		this.score = snapshot.score();
		this.congestionLevel = snapshot.congestionLevel();
		this.observationStatus = snapshot.observationStatus();
		this.evidenceCount = snapshot.evidenceCount();
		this.distinctReporterCount = snapshot.distinctReporterCount();
		this.confidenceScore = snapshot.confidenceScore();
		this.computedAt = computedAt;
		this.calculationVersion = snapshot.calculationVersion();
	}

	public Long getId() {
		return id;
	}

	public Long getGridCellId() {
		return gridCellId;
	}

	public Double getScore() {
		return score;
	}

	public RiskLevel getCongestionLevel() {
		return congestionLevel;
	}

	public GridObservationStatus getObservationStatus() {
		return observationStatus;
	}

	public int getEvidenceCount() {
		return evidenceCount;
	}

	public int getDistinctReporterCount() {
		return distinctReporterCount;
	}

	public double getConfidenceScore() {
		return confidenceScore;
	}

	public Instant getWindowStartedAt() {
		return windowStartedAt;
	}

	public Instant getWindowEndedAt() {
		return windowEndedAt;
	}

	public Instant getComputedAt() {
		return computedAt;
	}

	public String getCalculationVersion() {
		return calculationVersion;
	}
}
