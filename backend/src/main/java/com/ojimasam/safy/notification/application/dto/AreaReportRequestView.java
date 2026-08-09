package com.ojimasam.safy.notification.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.notification.domain.AreaReportRequest;
import com.ojimasam.safy.place.domain.Place;

public record AreaReportRequestView(
		Long id,
		Long placeId,
		String placeName,
		String title,
		String content,
		BigDecimal latitude,
		BigDecimal longitude,
		Instant requestedAt,
		Instant expiresAt,
		int recipientCount
) {
	public static AreaReportRequestView from(AreaReportRequest request, Place place) {
		return new AreaReportRequestView(
				request.getId(), request.getPlaceId(), request.getPlaceName(), request.getTitle(),
				request.getContent(), place.getCenterLatitude(), place.getCenterLongitude(),
				request.getRequestedAt(), request.getExpiresAt(), request.getRecipientCount());
	}
}
