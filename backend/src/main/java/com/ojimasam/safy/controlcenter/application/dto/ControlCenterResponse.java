package com.ojimasam.safy.controlcenter.application.dto;

import java.time.Instant;

import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterType;

public record ControlCenterResponse(
		Long id,
		String code,
		String name,
		ControlCenterType centerType,
		ControlCenterStatus status,
		Integer capacityLimit,
		String address,
		String phoneNumber,
		Instant createdAt,
		Instant updatedAt
) {
	public static ControlCenterResponse from(ControlCenter controlCenter) {
		return new ControlCenterResponse(
				controlCenter.getId(),
				controlCenter.getCode(),
				controlCenter.getName(),
				controlCenter.getCenterType(),
				controlCenter.getStatus(),
				controlCenter.getCapacityLimit(),
				controlCenter.getAddress(),
				controlCenter.getPhoneNumber(),
				controlCenter.getCreatedAt(),
				controlCenter.getUpdatedAt()
		);
	}
}
