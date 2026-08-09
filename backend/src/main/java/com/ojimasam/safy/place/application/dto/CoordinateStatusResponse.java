package com.ojimasam.safy.place.application.dto;

import java.time.Instant;

import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;

/**
 * 임의 좌표의 상황 요약. 외부 검색으로 찾은 구체적 장소에 쓴다.
 *
 * <p>자치구·121장소 응답과 <b>항목과 척도가 같다</b>. 다른 것은 근거를 어떻게 찾았는지뿐이고,
 * 그건 {@code congestionMatchType} 과 {@code reportRadiusMeters} 로 알려준다 — 화면이 각주로
 * 밝혀야 사용자가 "이 값이 어디 기준인지" 알 수 있다.
 *
 * @param congestion 근거가 없으면 {@code null} 이다. 121장소가 근처에 없는 좌표가 실제로 많다.
 * @param congestionMatchType {@code GRID}(좌표의 격자를 덮는 장소) 또는 {@code NEAREST}(최근접).
 *        혼잡도가 없으면 {@code null} 이다.
 * @param recentReportCount 반경 안 30분 이내 공개 제보 수. 혼잡도와 범위가 달라 따로 밝혀야 한다.
 */
public record CoordinateStatusResponse(
		CongestionObservationView congestion,
		String congestionMatchType,
		long recentReportCount,
		int reportRadiusMeters,
		Instant updatedAt
) {
	public static CoordinateStatusResponse of(
			CoordinateCongestionView congestion,
			long recentReportCount,
			int reportRadiusMeters,
			Instant updatedAt
	) {
		return new CoordinateStatusResponse(
				congestion == null ? null : congestion.congestion(),
				congestion == null ? null : congestion.matchType(),
				recentReportCount,
				reportRadiusMeters,
				updatedAt
		);
	}
}
