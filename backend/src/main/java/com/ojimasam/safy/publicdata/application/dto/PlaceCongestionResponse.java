package com.ojimasam.safy.publicdata.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;

/**
 * 지도에 표시할 지역별 최신 인구 밀집 상태.
 *
 * latitude·longitude 는 비어 있을 수 있다. 서울시 121장소 목록에 좌표가 없어 지오코딩으로 채우는 중이며,
 * 좌표가 아직 없는 지역도 목록과 혼잡도는 유효하므로 응답에서 빼지 않는다.
 * 지도에 마커를 찍는 쪽에서 좌표가 없는 항목을 건너뛰어야 한다.
 */
public record PlaceCongestionResponse(
		Long placeId,
		String externalPlaceId,
		String placeName,
		PlaceCategory category,
		BigDecimal latitude,
		BigDecimal longitude,
		CongestionLevel congestionLevel,
		String congestionMessage,
		Integer populationMin,
		Integer populationMax,
		Instant observedAt
) {
	public static PlaceCongestionResponse from(CityCongestionObservation observation) {
		Place place = observation.getPlace();
		return new PlaceCongestionResponse(
				place.getId(),
				place.getExternalPlaceId(),
				place.getName(),
				place.getCategory(),
				place.getCenterLatitude(),
				place.getCenterLongitude(),
				observation.getCongestionLevel(),
				observation.getCongestionMessage(),
				observation.getPopulationMin(),
				observation.getPopulationMax(),
				observation.getObservedAt()
		);
	}
}
