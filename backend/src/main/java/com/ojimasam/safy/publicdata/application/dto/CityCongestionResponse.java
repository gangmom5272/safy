package com.ojimasam.safy.publicdata.application.dto;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** FastAPI 가 서울시 실시간 도시데이터 121곳을 한 응답으로 합쳐서 보낸다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CityCongestionResponse(
		CollectionRunPayload collectionRun,
		List<Item> items
) {
	/**
	 * FastAPI 응답에는 latitude·longitude 가 있지만 원본 인구 블록이 지역 좌표를 주지 않아 항상 비어 있다.
	 * 지역 중심점은 places 가 갖는 값이므로 여기서 받지 않는다(미지 필드는 무시된다).
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Item(
			String areaCode,
			String areaName,
			String congestionLevel,
			String congestionMessage,
			Integer populationMin,
			Integer populationMax,
			Instant observedAt
	) {
	}

	public List<Item> safeItems() {
		return items == null ? List.of() : items;
	}
}
