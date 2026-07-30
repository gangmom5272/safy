package com.ojimasam.safy.publicdata.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * FastAPI 가 citydata 응답의 EVENT_STTS 를 정규화해서 보낸다.
 * 원본에 안정적인 행사 식별자가 없어 externalId 는 FastAPI 가 합성한 값이다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CulturalEventResponse(
		CollectionRunPayload collectionRun,
		List<Item> items
) {
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Item(
			String externalId,
			String areaCode,
			String areaName,
			String name,
			String period,
			LocalDate startDate,
			LocalDate endDate,
			String place,
			BigDecimal latitude,
			BigDecimal longitude,
			Boolean isPaid,
			String thumbnailUrl,
			String detailUrl,
			String details
	) {
	}

	public List<Item> safeItems() {
		return items == null ? List.of() : items;
	}
}
