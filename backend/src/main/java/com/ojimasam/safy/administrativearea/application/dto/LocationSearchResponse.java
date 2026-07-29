package com.ojimasam.safy.administrativearea.application.dto;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;

public record LocationSearchResponse(
		Long areaId,
		String areaCode,
		String areaName,
		String displayName
) {
	public static LocationSearchResponse from(AdministrativeArea area) {
		return new LocationSearchResponse(area.getId(), area.getCode(), area.getName(), area.getName());
	}
}
