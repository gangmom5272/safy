package com.ojimasam.safy.administrativearea.application.dto;

import java.time.Instant;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;

/**
 * 시민 화면의 지역 상황 요약.
 *
 * @param congestion 서울시 실시간 도시데이터 기반 혼잡도. 근거가 없으면 {@code null} 이다.
 * @param recentReportCount 시민 화면의 '최근 제보'. 지도 마커와 같은 조건(공개 + 최근 창)으로 센다.
 */
public record AreaStatusResponse(
		Long areaId,
		String areaCode,
		String areaName,
		CongestionObservationView congestion,
		long recentReportCount,
		Instant updatedAt
) {
	public static AreaStatusResponse of(
			AdministrativeArea area,
			CongestionObservationView congestion,
			long recentReportCount,
			Instant updatedAt
	) {
		return new AreaStatusResponse(
				area.getId(),
				area.getCode(),
				area.getName(),
				congestion,
				recentReportCount,
				updatedAt
		);
	}
}
