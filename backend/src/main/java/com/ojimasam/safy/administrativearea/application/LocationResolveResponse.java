package com.ojimasam.safy.administrativearea.application;

import java.math.BigDecimal;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;

public record LocationResolveResponse(
		boolean resolved,
		Long areaId,
		String areaCode,
		String areaName,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		String message
) {
	public static LocationResolveResponse resolved(
			AdministrativeArea area,
			String address,
			BigDecimal latitude,
			BigDecimal longitude
	) {
		return new LocationResolveResponse(
				true,
				area.getId(),
				area.getCode(),
				area.getName(),
				address,
				latitude,
				longitude,
				null
		);
	}

	/**
	 * 지원 행정구역이 아니어도 주소는 채워서 내려준다.
	 * 지도를 움직이다 서울 경계를 넘었을 때 주소만 사라지면 화면이 어색해지고,
	 * "지원 지역이 아님"과 "주소를 못 찾음"을 구분할 수 없게 된다.
	 */
	public static LocationResolveResponse unresolved(
			String address,
			BigDecimal latitude,
			BigDecimal longitude,
			String message
	) {
		return new LocationResolveResponse(false, null, null, null, address, latitude, longitude, message);
	}
}
