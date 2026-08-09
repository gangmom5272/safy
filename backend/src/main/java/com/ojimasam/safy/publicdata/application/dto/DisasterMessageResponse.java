package com.ojimasam.safy.publicdata.application.dto;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** FastAPI 가 재난문자 원본의 쉼표 구분 수신지역명을 regions 배열로 정규화해서 보낸다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisasterMessageResponse(
		CollectionRunPayload collectionRun,
		List<Item> items
) {
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Item(
			String externalId,
			Instant sentAt,
			String message,
			List<String> regions,
			String emergencyLevel,
			String disasterType
	) {
	}

	public List<Item> safeItems() {
		return items == null ? List.of() : items;
	}
}
