package com.ojimasam.safy.analysis.domain.risk;

/**
 * {@code grid_risk_snapshots} 한 행에 대응한다.
 *
 * <p>{@code score} 가 null 이면 근거가 없어 위험도를 낼 수 없는 격자다. 0 으로 채우면
 * 안 된다 — 0 은 very_low(안전함)로 읽혀 지도가 온통 초록색이 된다. 데이터가 없는
 * 것과 안전한 것은 다르다. 화면에서는 색칠하지 않고 비워 둔다.
 */
public record GridRiskSnapshot(
		Long gridCellId,
		Double score,
		RiskLevel congestionLevel,
		GridObservationStatus observationStatus,
		int evidenceCount,
		int distinctReporterCount,
		double confidenceScore,
		String calculationVersion
) {
}
