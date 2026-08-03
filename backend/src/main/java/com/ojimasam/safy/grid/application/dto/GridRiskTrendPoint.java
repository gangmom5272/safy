package com.ojimasam.safy.grid.application.dto;

import java.time.Instant;

import com.ojimasam.safy.analysis.domain.risk.GridObservationStatus;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.grid.domain.GridRiskHistory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 격자 위험도 추이의 한 점.
 *
 * <p>이력은 <b>값이 바뀔 때만</b> 쌓인다. 점 사이 간격이 일정하지 않고, 다음 점이
 * 나올 때까지 그 값이 유지된 것으로 읽어야 한다(계단 함수).
 */
@Schema(description = "격자 위험도 추이의 한 점. 다음 점까지 이 값이 유지된 것으로 읽는다")
public record GridRiskTrendPoint(
		@Schema(description = "이 값이 계산된 시간 창의 끝")
		Instant windowEndedAt,

		@Schema(description = "위험 점수 0~100", example = "60.0")
		Double score,

		@Schema(description = "위험 단계", example = "medium")
		RiskLevel congestionLevel,

		@Schema(description = "점수 출처", example = "PUBLIC_DATA")
		GridObservationStatus observationStatus
) {
	public static GridRiskTrendPoint from(GridRiskHistory history) {
		return new GridRiskTrendPoint(
				history.getWindowEndedAt(),
				history.getScore(),
				history.getCongestionLevel(),
				history.getObservationStatus()
		);
	}
}
