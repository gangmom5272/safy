package com.ojimasam.safy.publicdata.domain;

import java.time.Instant;
import java.util.List;

public interface CityCongestionObservationRepository {
	boolean existsByPlace_IdAndObservedAt(Long placeId, Instant observedAt);

	CityCongestionObservation save(CityCongestionObservation observation);

	/** 활성 지역별 최신 관측값 1건. 지도 조회의 기본 질의다. */
	List<CityCongestionObservation> findLatestPerPlace();

	/** 보관 기간을 넘긴 관측값을 지운다. 반환값은 삭제된 행 수다. */
	int deleteObservedBefore(Instant threshold);
}
