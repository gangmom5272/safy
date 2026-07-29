package com.ojimasam.safy.administrativearea.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 좌표 캐시 키를 만든다.
 *
 * <p>정밀도를 Report.latitude/longitude 의 컬럼 정밀도(numeric(10,7))에 맞춘다.
 * 그래야 저장 전 좌표(위치 조회)와 저장 후 좌표(라우팅)가 반드시 같은 키로 떨어져,
 * 같은 지점을 두 번 조회하지 않는다.
 *
 * <p>적중률을 높이려고 더 거칠게 잡지 않는다. 반올림은 격자를 만들고, 격자 하나에
 * 두 자치구가 걸치면 경계 근처 제보가 옆 구의 캐시 값을 받는다. 이 값은 관할
 * 관제센터를 정하므로 오배정으로 이어진다.
 */
final class CoordinateCacheKey {
	private static final int SCALE = 7;

	private CoordinateCacheKey() {
	}

	static String of(String prefix, BigDecimal latitude, BigDecimal longitude) {
		return prefix + normalize(latitude) + ":" + normalize(longitude);
	}

	private static String normalize(BigDecimal coordinate) {
		return coordinate.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
	}
}
