package com.ojimasam.safy.notification.application.dto;

import java.math.BigDecimal;

import com.ojimasam.safy.place.domain.Place;

public record AreaReportRequestPlaceView(
		Long id,
		String name,
		BigDecimal latitude,
		BigDecimal longitude
) {
	public static AreaReportRequestPlaceView from(Place place) {
		return new AreaReportRequestPlaceView(
				place.getId(), place.getName(), place.getCenterLatitude(), place.getCenterLongitude());
	}
}
