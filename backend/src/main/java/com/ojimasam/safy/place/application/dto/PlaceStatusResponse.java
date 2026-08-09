package com.ojimasam.safy.place.application.dto;

import java.time.Instant;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;

/**
 * 검색한 장소 하나의 상황 요약.
 *
 * @param congestion 이 장소 자신의 최신 혼잡도. 근거가 없으면(수집 공백 등) {@code null} 이다.
 * @param recentReportCount 이 장소의 경계 폴리곤 안에 있는 30분 이내 공개 제보 수.
 */
public record PlaceStatusResponse(
		Long placeId,
		String placeName,
		CongestionObservationView congestion,
		long recentReportCount,
		Instant updatedAt
) {
	public static PlaceStatusResponse of(
			Place place, CongestionObservationView congestion, long recentReportCount, Instant updatedAt) {
		return new PlaceStatusResponse(place.getId(), place.getName(), congestion, recentReportCount, updatedAt);
	}
}
