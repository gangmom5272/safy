package com.ojimasam.safy.controlcenter.application;

import java.time.Instant;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.CoverageStatus;

public record ControlCenterAreaResponse(
		Long id,
		Long controlCenterId,
		String controlCenterCode,
		Long administrativeAreaId,
		String administrativeAreaCode,
		String administrativeAreaName,
		CoverageRole coverageRole,
		int priority,
		CoverageStatus status,
		Instant effectiveFrom,
		Instant effectiveTo
) {
	public static ControlCenterAreaResponse from(ControlCenterArea coverage) {
		ControlCenter center = coverage.getControlCenter();
		AdministrativeArea area = coverage.getAdministrativeArea();
		return new ControlCenterAreaResponse(
				coverage.getId(),
				center.getId(),
				center.getCode(),
				area.getId(),
				area.getCode(),
				area.getName(),
				coverage.getCoverageRole(),
				coverage.getPriority(),
				coverage.getStatus(),
				coverage.getEffectiveFrom(),
				coverage.getEffectiveTo()
		);
	}
}
