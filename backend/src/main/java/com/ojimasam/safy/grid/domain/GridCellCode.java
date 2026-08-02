package com.ojimasam.safy.grid.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 100m 격자 셀의 좌표. 좌표를 넣으면 속할 셀이 결정론적으로 정해진다.
 *
 * <p>격자는 고정 원점과 간격에 정렬돼 있어 서울 어느 좌표든 이미 속할 셀이 있다.
 * 셀 행을 DB 에 미리 만들어 둘 필요가 없고, 값이 생기는 셀만 기록하면 된다.
 *
 * <p>산식은 위경도 등간격이다. 좌표계 변환 라이브러리가 필요 없어
 * 프론트(TypeScript)와 같은 식을 열 줄로 재현할 수 있다. 서울 위도 범위
 * (37.42~37.70)에서 셀 폭은 99.5~99.9m 로 변하는데, 100m 대비 0.4% 라 무시한다.
 *
 * <p><b>double 로 계산하는 이유:</b> 프론트가 IEEE-754 double 로 같은 연산을 한다.
 * BigDecimal 로 나누면 반올림 방식이 달라 경계 좌표에서 프론트와 다른 셀이 나온다.
 * 연산 순서까지 프론트와 같게 유지해야 한다
 * ({@code Math.floor((latitude - ORIGIN) / STEP)}).
 *
 * @see <a href="https://ssafy.atlassian.net/browse/S15P11A503-156">S15P11A503-156</a>
 */
public record GridCellCode(int row, int column) {
	/** 셀 한 변의 길이(m). 코드 접두사에 들어간다. */
	public static final int RESOLUTION_METERS = 100;

	public static final double ORIGIN_LATITUDE = 37.4;
	public static final double ORIGIN_LONGITUDE = 126.7;
	/** 약 100.0m */
	public static final double STEP_LATITUDE = 0.0009;
	/** 서울 위도(37.55°) 기준 약 99.7m */
	public static final double STEP_LONGITUDE = 0.00113;

	/** reports·grid_cells 의 좌표 컬럼 scale 과 맞춘다. */
	private static final int COORDINATE_SCALE = 7;

	public static GridCellCode of(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) {
			throw new IllegalArgumentException("위도와 경도는 필수입니다.");
		}

		return of(latitude.doubleValue(), longitude.doubleValue());
	}

	public static GridCellCode of(double latitude, double longitude) {
		return new GridCellCode(
				(int) Math.floor((latitude - ORIGIN_LATITUDE) / STEP_LATITUDE),
				(int) Math.floor((longitude - ORIGIN_LONGITUDE) / STEP_LONGITUDE)
		);
	}

	/**
	 * 저장·조회에 쓰는 셀 코드.
	 *
	 * <p>원점 남·서쪽 좌표는 음수 인덱스가 되므로 {@code r}/{@code c} 접두사를 붙인다.
	 * 접두사가 없으면 {@code S100--12--9} 처럼 dash 가 겹쳐 파싱이 깨진다.
	 */
	public String value() {
		return "S" + RESOLUTION_METERS + "-r" + row + "-c" + column;
	}

	/** 셀 중심 위도. 셀 행을 만들 때 쓴다. */
	public BigDecimal centerLatitude() {
		return scaled(ORIGIN_LATITUDE + (row + 0.5) * STEP_LATITUDE);
	}

	/** 셀 중심 경도. 셀 행을 만들 때 쓴다. */
	public BigDecimal centerLongitude() {
		return scaled(ORIGIN_LONGITUDE + (column + 0.5) * STEP_LONGITUDE);
	}

	private static BigDecimal scaled(double value) {
		return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
	}
}
