package com.ojimasam.safy.publicdata.application.dto;

import java.time.Instant;

import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;

/**
 * 서울시 실시간 도시데이터 관측값 하나를 화면용으로 줄인 것.
 *
 * <p>지역 상황 요약이 쓴다. 제보 분석은 이 값을 쓰지 않는다 — 그쪽은 그 제보를 분석한
 * 결과만 다루고, 혼잡도도 AI가 계산한 국소 최대 혼잡도(L)를 쓴다.
 *
 * <p>지도용 {@link PlaceCongestionResponse} 와 달리 좌표·분류를 담지 않는다. 이 값을 쓰는
 * 화면은 이미 어느 지점을 보고 있는지 알고 있다.
 *
 * @param populationMin 서울시가 주는 추정 인원 하한. 값이 없는 관측도 있어 비어 있을 수 있다.
 */
public record CongestionObservationView(
		Long placeId,
		String placeName,
		CongestionLevel congestionLevel,
		String congestionMessage,
		Integer populationMin,
		Integer populationMax,
		Instant observedAt
) {
	public static CongestionObservationView from(CityCongestionObservation observation) {
		return new CongestionObservationView(
				observation.getPlace().getId(),
				observation.getPlace().getName(),
				observation.getCongestionLevel(),
				observation.getCongestionMessage(),
				observation.getPopulationMin(),
				observation.getPopulationMax(),
				observation.getObservedAt()
		);
	}
}
