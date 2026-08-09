package com.ojimasam.safy.report.application.dto;

import com.ojimasam.safy.controlcenter.domain.ControlCenter;

public record AssignedControlCenterView(
		String name,
		String administrativeAreaName
) {
	public static AssignedControlCenterView of(ControlCenter center, String administrativeAreaName) {
		return new AssignedControlCenterView(center.getName(), administrativeAreaName);
	}
}
