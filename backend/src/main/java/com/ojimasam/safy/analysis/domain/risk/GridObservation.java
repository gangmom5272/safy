package com.ojimasam.safy.analysis.domain.risk;

/**
 * 시간 창 안에 그 격자로 들어온 제보 1건의 위험도 결과.
 *
 * <p>{@code finalRisk} 가 null 인 제보(분석 성공 프레임이 없어 위험도를 낼 수 없던
 * 경우)는 계산에서 제외한다.
 */
public record GridObservation(
		Long reportId,
		Long reporterUserId,
		Double finalRisk,
		double riskConfidence
) {
}
