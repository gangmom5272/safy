package com.ojimasam.safy.grid.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.analysis.domain.risk.GridObservationStatus;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.grid.domain.GridRiskHistory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지도에 그릴 격자 한 칸. 응답에 없는 칸은 근거가 없는 칸({@code NO_DATA})이므로
 * 화면에서 색칠하지 않는다.
 *
 * <p>그릴 사각형의 <b>경계</b>를 담는다. 프론트에도 같은 격자 산식이 있어 코드로 역산할
 * 수 있지만, 두 산식이 어긋나면 조용히 다른 칸을 그리게 된다.
 *
 * <p>{@code score} 도 함께 담는다. 등급만으로는 '약간 붐빔'(45)과 '붐빔'(60)이 둘 다
 * {@code medium} 이라 구분되지 않는다. 등급으로 색을 정하고 점수로 농도를 나누면 된다.
 *
 * <p>줌 아웃 시 여러 100m 칸이 하나로 묶인다. 그때 {@code score} 는 묶인 칸들의
 * <b>최댓값</b>이고 나머지 필드는 그 최댓값 칸의 값이다. {@code gridCode} 도 그 칸의
 * 코드라서 클릭하면 100m 단위 추이를 그대로 조회할 수 있다.
 */
@Schema(description = "지도에 그릴 격자 한 칸의 위험도")
public record GridRiskResponse(
		@Schema(description = "가장 위험한 100m 칸의 셀 코드. 추이 조회에 쓴다", example = "S100-r174-c199")
		String gridCode,

		@Schema(description = "이 칸의 한 변 길이(m). resolution 이 1이면 100", example = "800")
		int resolutionMeters,

		@Schema(description = "남서쪽 위도", example = "37.5561")
		BigDecimal southWestLatitude,

		@Schema(description = "남서쪽 경도", example = "126.9243")
		BigDecimal southWestLongitude,

		@Schema(description = "북동쪽 위도", example = "37.5570")
		BigDecimal northEastLatitude,

		@Schema(description = "북동쪽 경도", example = "126.9254")
		BigDecimal northEastLongitude,

		@Schema(description = "위험 점수 0~100. 묶인 칸이면 최댓값", example = "60.0")
		Double score,

		@Schema(description = "위험 단계", example = "medium")
		RiskLevel congestionLevel,

		@Schema(description = "점수 출처", example = "PUBLIC_DATA")
		GridObservationStatus observationStatus,

		@Schema(description = "사용한 근거 수. 제보 수 + 공공데이터 관측이 있으면 1", example = "2")
		int evidenceCount,

		@Schema(description = "서로 다른 제보자 수", example = "1")
		int distinctReporterCount,

		@Schema(description = "근거 신뢰도 0~1", example = "0.5")
		double confidenceScore,

		@Schema(description = "이 값이 계산된 시간 창의 끝")
		Instant windowEndedAt
) {
	private static final int COORDINATE_SCALE = 7;

	/**
	 * @param row 묶은 뒤의 행. {@code resolution} 으로 나눈 값이다.
	 * @param column 묶은 뒤의 열
	 * @param resolution 묶은 배수. 1이면 100m 그대로다
	 * @param peak 묶인 칸 중 점수가 가장 높은 칸
	 * @param peakCode 그 칸의 셀 코드
	 */
	public static GridRiskResponse of(
			int row, int column, int resolution, GridRiskHistory peak, String peakCode) {
		double heightDegrees = GridCellCode.STEP_LATITUDE * resolution;
		double widthDegrees = GridCellCode.STEP_LONGITUDE * resolution;

		return new GridRiskResponse(
				peakCode,
				GridCellCode.RESOLUTION_METERS * resolution,
				scaled(GridCellCode.ORIGIN_LATITUDE + row * heightDegrees),
				scaled(GridCellCode.ORIGIN_LONGITUDE + column * widthDegrees),
				scaled(GridCellCode.ORIGIN_LATITUDE + (row + 1) * heightDegrees),
				scaled(GridCellCode.ORIGIN_LONGITUDE + (column + 1) * widthDegrees),
				peak.getScore(),
				peak.getCongestionLevel(),
				peak.getObservationStatus(),
				peak.getEvidenceCount(),
				peak.getDistinctReporterCount(),
				peak.getConfidenceScore(),
				peak.getWindowEndedAt()
		);
	}

	private static BigDecimal scaled(double value) {
		return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, java.math.RoundingMode.HALF_UP);
	}
}
