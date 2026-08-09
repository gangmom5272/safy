package com.ojimasam.safy.publicdata.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ojimasam.safy.publicdata.domain.CulturalEvent;

/** 지도에 표시할 진행 중인 문화 행사. */
public record CulturalEventView(
		Long eventId,
		String title,
		String description,
		String venueName,
		String eventPeriod,
		LocalDate startDate,
		LocalDate endDate,
		BigDecimal latitude,
		BigDecimal longitude,
		String areaName,
		Boolean paid,
		String thumbnailUrl,
		String detailUrl
) {
	public static CulturalEventView from(CulturalEvent event) {
		return new CulturalEventView(
				event.getId(),
				event.getTitle(),
				event.getDescription(),
				event.getVenueName(),
				event.getEventPeriod(),
				event.getStartDate(),
				event.getEndDate(),
				event.getLatitude(),
				event.getLongitude(),
				event.getAreaName(),
				event.getPaid(),
				event.getThumbnailUrl(),
				event.getDetailUrl()
		);
	}
}
