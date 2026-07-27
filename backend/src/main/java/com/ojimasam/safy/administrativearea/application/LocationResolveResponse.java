package com.ojimasam.safy.administrativearea.application;

import java.math.BigDecimal;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;

public record LocationResolveResponse(
		boolean resolved,
		Long areaId,
		String areaCode,
		String areaName,
		BigDecimal latitude,
		BigDecimal longitude,
		String message
) {
	public static LocationResolveResponse resolved(
			AdministrativeArea area,
			BigDecimal latitude,
			BigDecimal longitude
	) {
		return new LocationResolveResponse(
				true,
				area.getId(),
				area.getCode(),
				area.getName(),
				latitude,
				longitude,
				null
		);
	}

	public static LocationResolveResponse unresolved(
			BigDecimal latitude,
			BigDecimal longitude,
			String message
	) {
		return new LocationResolveResponse(false, null, null, null, latitude, longitude, message);
	}
}
