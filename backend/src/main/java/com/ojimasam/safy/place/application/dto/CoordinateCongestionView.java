package com.ojimasam.safy.place.application.dto;

import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;

/**
 * 임의 좌표의 혼잡도. 값 자체는 121장소 하나의 실제 관측이고, 그 장소를 어떻게 찾았는지를
 * {@code matchType} 으로 함께 알려준다.
 *
 * <p>화면 각주가 이 값으로 표현을 바꾼다 — 격자로 찾았으면 "역삼역 기준", 최근접으로
 * 찾았으면 "~에서 가까운 역삼역 기준"이다. 정확도가 다른 두 값을 같은 문구로 보여주면
 * 사용자가 구분할 수 없다.
 */
public record CoordinateCongestionView(
		CongestionObservationView congestion,
		String matchType
) {
	/** 좌표가 속한 격자를 121장소가 덮고 있었다. */
	public static final String MATCH_GRID = "GRID";
	/** 그 격자를 덮는 장소가 없어 가장 가까운 장소로 대체했다. */
	public static final String MATCH_NEAREST = "NEAREST";

	public static CoordinateCongestionView grid(CongestionObservationView congestion) {
		return new CoordinateCongestionView(congestion, MATCH_GRID);
	}

	public static CoordinateCongestionView nearest(CongestionObservationView congestion) {
		return new CoordinateCongestionView(congestion, MATCH_NEAREST);
	}
}
