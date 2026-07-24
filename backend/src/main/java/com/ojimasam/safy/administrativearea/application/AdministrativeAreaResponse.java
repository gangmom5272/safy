package com.ojimasam.safy.administrativearea.application;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "행정구역 정보")
public record AdministrativeAreaResponse(
		@Schema(example = "2") Long id,
		@Schema(example = "1168000000") String code,
		@Schema(example = "강남구") String name,
		@Schema(example = "SIGUNGU") AdministrativeAreaType areaType,
		@Schema(example = "1", nullable = true) Long parentId,
		@Schema(example = "1100000000", nullable = true) String parentCode
) {
	public static AdministrativeAreaResponse from(AdministrativeArea area) {
		AdministrativeArea parent = area.getParent();
		return new AdministrativeAreaResponse(
				area.getId(),
				area.getCode(),
				area.getName(),
				area.getAreaType(),
				parent == null ? null : parent.getId(),
				parent == null ? null : parent.getCode()
		);
	}
}
